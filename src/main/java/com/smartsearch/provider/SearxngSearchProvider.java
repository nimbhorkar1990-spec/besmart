package com.smartsearch.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsearch.dto.SearchRequestDto;
import com.smartsearch.dto.SearchResultItemDto;
import com.smartsearch.model.SearchProviderConfig;
import com.smartsearch.repository.SearchProviderConfigRepository;
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
import java.util.Optional;

@Component
public class SearxngSearchProvider implements SearchProvider {

    private static final Logger logger = LoggerFactory.getLogger(SearxngSearchProvider.class);

    private final SystemSettingService settingService;
    private final SearchProviderConfigRepository providerConfigRepository;
    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public SearxngSearchProvider(SystemSettingService settingService,
                                 SearchProviderConfigRepository providerConfigRepository) {
        this.settingService = settingService;
        this.providerConfigRepository = providerConfigRepository;
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
                        logger.warn("Failed to initialize HttpClient for SearXNG provider: {}", e.getMessage());
                    }
                }
            }
        }
        return httpClient;
    }

    @Override
    public String getProviderId() {
        return "SEARXNG";
    }

    @Override
    public String getDisplayName() {
        return "SearXNG Metasearch API";
    }

    @Override
    public boolean isEnabled() {
        Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("SEARXNG");
        return cfg.map(SearchProviderConfig::getEnabled)
                .orElseGet(() -> settingService.getBooleanSetting("SEARXNG_SEARCH_ENABLED", false));
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public int getPriority() {
        Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("SEARXNG");
        return cfg.map(SearchProviderConfig::getPriority).orElse(5);
    }

    @Override
    public List<SearchResultItemDto> search(SearchRequestDto request) {
        List<SearchResultItemDto> results = new ArrayList<>();
        if (!isEnabled() || request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return results;
        }

        try {
            Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("SEARXNG");
            String endpoint = cfg.map(SearchProviderConfig::getEndpointUrl)
                    .filter(s -> !s.isBlank())
                    .orElseGet(() -> settingService.getSetting("SEARXNG_ENDPOINT", "https://searx.be/search"));

            String encodedQuery = URLEncoder.encode(request.getQuery().trim(), StandardCharsets.UTF_8);
            String url = endpoint + "?q=" + encodedQuery + "&format=json&language=en";

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "SmartSearchBot/1.0 (+http://localhost:8080/bot; info@smartsearch.local)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpClient client = getHttpClient();
            if (client == null) return results;

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode items = root.path("results");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String title = item.path("title").asText();
                        String link = item.path("url").asText();
                        String content = item.path("content").asText();
                        String engine = item.path("engine").asText("SearXNG");

                        if (link == null || link.isBlank()) continue;

                        SearchResultItemDto dto = new SearchResultItemDto(
                                title, link, content, "ALL", extractDomain(link)
                        );
                        dto.setSource("SEARXNG");
                        dto.setSourceBadge("SearXNG (" + engine + ")");
                        dto.setSourceBadgeClass("badge-provider-searxng");
                        dto.addVerifiedProvider("SearXNG");
                        results.add(dto);

                        if (results.size() >= 10) break;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("SearXNG search error: {}", e.getMessage());
        }

        return results;
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : "searx.be";
        } catch (Exception e) {
            return "web";
        }
    }
}

