package com.smartsearch.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsearch.dto.SearchRequestDto;
import com.smartsearch.dto.SearchResultItemDto;
import com.smartsearch.service.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class BingSearchProvider implements SearchProvider {

    private static final Logger logger = LoggerFactory.getLogger(BingSearchProvider.class);

    private final SystemSettingService settingService;
    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public BingSearchProvider(SystemSettingService settingService) {
        this.settingService = settingService;
        this.objectMapper = new ObjectMapper();
    }

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    try {
                        httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(3))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    } catch (Exception e) {
                        logger.warn("Failed to initialize HttpClient for Bing provider: {}", e.getMessage());
                    }
                }
            }
        }
        return httpClient;
    }

    @Override
    public String getProviderId() {
        return "BING";
    }

    @Override
    public String getDisplayName() {
        return "Microsoft Bing Web Search API v7";
    }

    @Override
    public boolean isEnabled() {
        return settingService.getBooleanSetting("BING_SEARCH_ENABLED", true);
    }

    @Override
    public boolean isConfigured() {
        String key = settingService.getSetting("BING_API_KEY", System.getenv("BING_API_KEY"));
        return key != null && !key.isBlank();
    }

    @Override
    public int getPriority() {
        return settingService.getIntSetting("BING_PRIORITY", 2);
    }

    @Override
    public List<SearchResultItemDto> search(SearchRequestDto request) {
        List<SearchResultItemDto> results = new ArrayList<>();
        if (!isEnabled() || !isConfigured() || request.getQuery() == null || request.getQuery().isBlank()) {
            return results;
        }

        String apiKey = settingService.getSetting("BING_API_KEY", System.getenv("BING_API_KEY"));

        try {
            String encodedQuery = URLEncoder.encode(request.getQuery().trim(), StandardCharsets.UTF_8);
            String url = "https://api.bing.microsoft.com/v7.0/search?q=" + encodedQuery + "&count=10&responseFilter=Webpages";

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("Ocp-Apim-Subscription-Key", apiKey.trim())
                    .header("User-Agent", "SmartSearchBot/1.0 (+http://localhost:8080/bot)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpClient client = getHttpClient();
            if (client == null) {
                return results;
            }
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode pages = root.path("webPages").path("value");
                if (pages.isArray()) {
                    for (JsonNode page : pages) {
                        String name = page.path("name").asText();
                        String pageUrl = page.path("url").asText();
                        String snippet = page.path("snippet").asText();
                        String domain = extractDomain(pageUrl);

                        SearchResultItemDto dto = new SearchResultItemDto(name, pageUrl, snippet, "ALL", domain);
                        dto.setSource("BING");
                        dto.setSourceBadge("Bing Web Search");
                        dto.setSourceBadgeClass("badge-provider-bing");
                        results.add(dto);
                    }
                }
            } else {
                logger.warn("Bing Search API returned HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logger.error("Error invoking Bing Search API: {}", e.getMessage());
        }

        return results;
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : "bing.com";
        } catch (Exception e) {
            return "web";
        }
    }
}

