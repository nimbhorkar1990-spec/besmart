package com.smartsearch.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RobotsTxtEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(RobotsTxtEvaluator.class);

    private final UrlSecurityValidator securityValidator;
    private HttpClient httpClient;
    private final Map<String, RobotsRules> cache = new ConcurrentHashMap<>();

    public RobotsTxtEvaluator() {
        this(new UrlSecurityValidator());
    }

    public RobotsTxtEvaluator(UrlSecurityValidator securityValidator) {
        this.securityValidator = securityValidator;
    }

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(3))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                }
            }
        }
        return httpClient;
    }

    public boolean isAllowed(String urlStr) {
        try {
            URI uri = URI.create(urlStr);
            String domain = uri.getHost();
            if (domain == null)
                return true;

            RobotsRules rules = getRulesForDomain(uri);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            if (uri.getQuery() != null) {
                path = path + "?" + uri.getQuery();
            }

            return rules.isAllowed(path);
        } catch (Exception e) {
            return true;
        }
    }

    public int getCrawlDelaySeconds(String urlStr) {
        try {
            URI uri = URI.create(urlStr);
            RobotsRules rules = getRulesForDomain(uri);
            return rules.getCrawlDelay();
        } catch (Exception e) {
            return 0;
        }
    }

    private RobotsRules getRulesForDomain(URI uri) {
        String domain = uri.getHost().toLowerCase();
        RobotsRules existing = cache.get(domain);
        if (existing != null && !existing.isExpired()) {
            return existing;
        }

        RobotsRules freshRules = fetchRobotsTxt(uri);
        cache.put(domain, freshRules);
        return freshRules;
    }

    private RobotsRules fetchRobotsTxt(URI uri) {
        String domain = uri.getHost();
        String robotsUrl = uri.getScheme() + "://" + domain + "/robots.txt";

        if (securityValidator != null && !securityValidator.isSafeToCrawl(robotsUrl)) {
            logger.warn("Crawl safety: blocked robots.txt fetch for unsafe URL: {}", robotsUrl);
            return new RobotsRules(List.of("/"), 0);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(robotsUrl))
                    .timeout(Duration.ofSeconds(3))
                    .header("User-Agent", "SmartSearchBot/1.0 (+http://localhost:8080/bot)")
                    .GET()
                    .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                return parseRobotsContent(response.body());
            }
        } catch (Exception e) {
            logger.debug("robots.txt not found or timed out for {}: {}", domain, e.getMessage());
        }

        return new RobotsRules(Collections.emptyList(), 0);
    }

    public RobotsRules parseRobotsContent(String content) {
        List<String> disallows = new ArrayList<>();
        int crawlDelay = 0;

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            boolean appliesToUs = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int commentIdx = line.indexOf("#");
                if (commentIdx != -1) {
                    line = line.substring(0, commentIdx).trim();
                }

                String lower = line.toLowerCase();
                if (lower.startsWith("user-agent:")) {
                    String agent = line.substring(11).trim();
                    appliesToUs = agent.equals("*") || agent.equalsIgnoreCase("SmartSearchBot");
                } else if (appliesToUs) {
                    if (lower.startsWith("disallow:")) {
                        String disallowPath = line.substring(9).trim();
                        if (!disallowPath.isEmpty()) {
                            disallows.add(disallowPath);
                        }
                    } else if (lower.startsWith("crawl-delay:")) {
                        try {
                            crawlDelay = Integer.parseInt(line.substring(12).trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return new RobotsRules(disallows, crawlDelay);
    }

    public static class RobotsRules {
        private final List<String> disallowPrefixes;
        private final int crawlDelay;
        private final long cachedAtEpochMs;

        public RobotsRules(List<String> disallowPrefixes, int crawlDelay) {
            this.disallowPrefixes = disallowPrefixes != null ? disallowPrefixes : Collections.emptyList();
            this.crawlDelay = crawlDelay;
            this.cachedAtEpochMs = System.currentTimeMillis();
        }

        public boolean isAllowed(String path) {
            for (String prefix : disallowPrefixes) {
                if (path.startsWith(prefix)) {
                    return false;
                }
            }
            return true;
        }

        public int getCrawlDelay() {
            return crawlDelay;
        }

        public boolean isExpired() {
            return (System.currentTimeMillis() - cachedAtEpochMs) > 86_400_000L; // 24 hours
        }
    }
}
