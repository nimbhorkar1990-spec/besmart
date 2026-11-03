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
public class GoogleSearchProvider implements SearchProvider {

    private static final Logger logger = LoggerFactory.getLogger(GoogleSearchProvider.class);

    private final SystemSettingService settingService;
    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public GoogleSearchProvider(SystemSettingService settingService) {
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
                        logger.warn("Failed to initialize HttpClient for Google provider: {}", e.getMessage());
                    }
                }
            }
        }
        return httpClient;
    }

    @Override
    public String getProviderId() {
        return "GOOGLE";
    }

    @Override
    public String getDisplayName() {
        return "Google Custom Search JSON API";
    }

    @Override
    public boolean isEnabled() {
        return settingService.getBooleanSetting("GOOGLE_SEARCH_ENABLED", true);
    }

    @Override
    public boolean isConfigured() {
        String key = settingService.getSetting("GOOGLE_API_KEY", System.getenv("GOOGLE_API_KEY"));
        String cx = settingService.getSetting("GOOGLE_SEARCH_ENGINE_ID", System.getenv("GOOGLE_SEARCH_ENGINE_ID"));
        return key != null && !key.isBlank() && cx != null && !cx.isBlank();
    }

    @Override
    public int getPriority() {
        return settingService.getIntSetting("GOOGLE_PRIORITY", 1);
    }

    @Override
    public List<SearchResultItemDto> search(SearchRequestDto request) {
        List<SearchResultItemDto> results = new ArrayList<>();
        if (!isEnabled() || !isConfigured() || request.getQuery() == null || request.getQuery().isBlank()) {
            return results;
        }

        String apiKey = settingService.getSetting("GOOGLE_API_KEY", System.getenv("GOOGLE_API_KEY"));
        String cx = settingService.getSetting("GOOGLE_SEARCH_ENGINE_ID", System.getenv("GOOGLE_SEARCH_ENGINE_ID"));

        try {
            String encodedQuery = URLEncoder.encode(request.getQuery().trim(), StandardCharsets.UTF_8);
            String url = String.format("https://www.googleapis.com/customsearch/v1?key=%s&cx=%s&q=%s&num=10",
                    apiKey.trim(), cx.trim(), encodedQuery);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
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
                JsonNode items = root.path("items");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String title = item.path("title").asText();
                        String link = item.path("link").asText();
                        String snippet = item.path("snippet").asText();
                        String displayLink = item.path("displayLink").asText();

                        String imageUrl = null;
                        JsonNode cseImage = item.path("pagemap").path("cse_image");
                        if (cseImage.isArray() && !cseImage.isEmpty()) {
                            imageUrl = cseImage.get(0).path("src").asText(null);
                        }

                        SearchResultItemDto dto = new SearchResultItemDto(title, link, snippet, "ALL", displayLink);
                        dto.setImageUrl(imageUrl);
                        dto.setSource("GOOGLE");
                        dto.setSourceBadge("Google Search API");
                        dto.setSourceBadgeClass("badge-provider-google");
                        results.add(dto);
                    }
                }
            } else {
                logger.warn("Google Search API returned HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logger.error("Error invoking Google Search API: {}", e.getMessage());
        }

        return results;
    }
}

