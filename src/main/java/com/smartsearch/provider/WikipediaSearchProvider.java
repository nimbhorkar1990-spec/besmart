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
public class WikipediaSearchProvider implements SearchProvider {

    private static final Logger logger = LoggerFactory.getLogger(WikipediaSearchProvider.class);

    private final SystemSettingService settingService;
    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public WikipediaSearchProvider(SystemSettingService settingService) {
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
                        logger.warn("Failed to initialize HttpClient for Wikipedia provider: {}", e.getMessage());
                    }
                }
            }
        }
        return httpClient;
    }

    @Override
    public String getProviderId() {
        return "WIKIPEDIA";
    }

    @Override
    public String getDisplayName() {
        return "Wikimedia Open Search API";
    }

    @Override
    public boolean isEnabled() {
        return settingService.getBooleanSetting("WIKIPEDIA_SEARCH_ENABLED", true);
    }

    @Override
    public boolean isConfigured() {
        return true; // Zero-config public authorized REST API
    }

    @Override
    public int getPriority() {
        return settingService.getIntSetting("WIKIPEDIA_PRIORITY", 4);
    }

    @Override
    public List<SearchResultItemDto> search(SearchRequestDto request) {
        List<SearchResultItemDto> results = new ArrayList<>();
        if (!isEnabled() || request.getQuery() == null || request.getQuery().isBlank()) {
            return results;
        }

        try {
            String encodedQuery = URLEncoder.encode(request.getQuery().trim(), StandardCharsets.UTF_8);
            String url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=" + encodedQuery + "&utf8=&format=json&srlimit=10";

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("User-Agent", "SmartSearchBot/1.0 (+http://localhost:8080/bot; info@smartsearch.local)")
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
                JsonNode items = root.path("query").path("search");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String title = item.path("title").asText();
                        String snippet = item.path("snippet").asText()
                                .replaceAll("<span class=\"searchmatch\">", "<mark class=\"search-highlight\">")
                                .replaceAll("</span>", "</mark>");
                        String pageUrl = "https://en.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(" ", "_"), StandardCharsets.UTF_8);

                        SearchResultItemDto dto = new SearchResultItemDto(title, pageUrl, snippet, "ALL", "wikipedia.org");
                        dto.setSource("WIKIPEDIA");
                        dto.setSourceBadge("Wikipedia API");
                        dto.setSourceBadgeClass("badge-provider-wiki");
                        results.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Wikipedia Search API request error: {}", e.getMessage());
        }

        return results;
    }
}

