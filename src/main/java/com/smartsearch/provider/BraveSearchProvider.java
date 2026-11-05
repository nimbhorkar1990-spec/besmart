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
public class BraveSearchProvider implements SearchProvider {

    private static final Logger logger = LoggerFactory.getLogger(BraveSearchProvider.class);

    private final SystemSettingService settingService;
    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public BraveSearchProvider(SystemSettingService settingService) {
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
                        logger.warn("Failed to initialize HttpClient for Brave provider: {}", e.getMessage());
                    }
                }
            }
        }
        return httpClient;
    }

    @Override
    public String getProviderId() {
        return "BRAVE";
    }

    @Override
    public String getDisplayName() {
        return "Brave Search API";
    }

    @Override
    public boolean isEnabled() {
        return settingService.getBooleanSetting("BRAVE_SEARCH_ENABLED", true);
    }

    @Override
    public boolean isConfigured() {
        String key = settingService.getSetting("BRAVE_API_KEY", System.getenv("BRAVE_API_KEY"));
        return key != null && !key.isBlank();
    }

    @Override
    public int getPriority() {
        return settingService.getIntSetting("BRAVE_PRIORITY", 3);
    }

    @Override
    public List<SearchResultItemDto> search(SearchRequestDto request) {
        List<SearchResultItemDto> results = new ArrayList<>();
        if (!isEnabled() || !isConfigured() || request.getQuery() == null || request.getQuery().isBlank()) {
            return results;
        }

        String apiKey = settingService.getSetting("BRAVE_API_KEY", System.getenv("BRAVE_API_KEY"));

        try {
            String encodedQuery = URLEncoder.encode(request.getQuery().trim(), StandardCharsets.UTF_8);
            String url = "https://api.search.brave.com/res/v1/web/search?q=" + encodedQuery + "&count=10";

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("X-Subscription-Token", apiKey.trim())
                    .header("Accept", "application/json")
                    .header("User-Agent", "SmartSearchBot/1.0 (+http://localhost:8080/bot)")
                    .GET()
                    .build();

            HttpClient client = getHttpClient();
            if (client == null) {
                return results;
            }
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode items = root.path("web").path("results");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String title = item.path("title").asText();
                        String pageUrl = item.path("url").asText();
                        String description = item.path("description").asText();
                        String domain = extractDomain(pageUrl);

                        SearchResultItemDto dto = new SearchResultItemDto(title, pageUrl, description, "ALL", domain);
                        dto.setSource("BRAVE");
                        dto.setSourceBadge("Brave Search API");
                        dto.setSourceBadgeClass("badge-provider-brave");
                        dto.addVerifiedProvider("Brave");
                        results.add(dto);
                    }
                }
            } else {
                logger.warn("Brave Search API returned HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logger.error("Error invoking Brave Search API: {}", e.getMessage());
        }

        return results;
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : "brave.com";
        } catch (Exception e) {
            return "web";
        }
    }
}

