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
public class YandexSearchProvider implements SearchProvider {

    private static final Logger logger = LoggerFactory.getLogger(YandexSearchProvider.class);

    private final SystemSettingService settingService;
    private final SearchProviderConfigRepository providerConfigRepository;
    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public YandexSearchProvider(SystemSettingService settingService,
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
                        logger.warn("Failed to initialize HttpClient for Yandex provider: {}", e.getMessage());
                    }
                }
            }
        }
        return httpClient;
    }

    @Override
    public String getProviderId() {
        return "YANDEX";
    }

    @Override
    public String getDisplayName() {
        return "Yandex Multilingual Search";
    }

    @Override
    public boolean isEnabled() {
        Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("YANDEX");
        return cfg.map(SearchProviderConfig::getEnabled)
                .orElseGet(() -> settingService.getBooleanSetting("YANDEX_SEARCH_ENABLED", false));
    }

    @Override
    public boolean isConfigured() {
        Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("YANDEX");
        String apiKey = cfg.map(SearchProviderConfig::getApiKey).orElse("");
        if (apiKey.isBlank()) {
            apiKey = settingService.getSetting("YANDEX_API_KEY", "");
        }
        return !apiKey.isBlank();
    }

    @Override
    public int getPriority() {
        Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("YANDEX");
        return cfg.map(SearchProviderConfig::getPriority).orElse(6);
    }

    @Override
    public List<SearchResultItemDto> search(SearchRequestDto request) {
        List<SearchResultItemDto> results = new ArrayList<>();
        if (!isEnabled() || !isConfigured() || request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return results;
        }

        Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("YANDEX");
        String apiKey = cfg.map(SearchProviderConfig::getApiKey).orElse(settingService.getSetting("YANDEX_API_KEY", ""));
        int limit = cfg.map(SearchProviderConfig::getResultLimit).orElse(10);
        int timeoutMs = cfg.map(SearchProviderConfig::getTimeoutMs).orElse(3000);

        try {
            String encodedQuery = URLEncoder.encode(request.getQuery().trim(), StandardCharsets.UTF_8);
            String endpoint = cfg.map(SearchProviderConfig::getEndpointUrl)
                    .filter(s -> !s.isBlank())
                    .orElse("https://searchapi.yandex.net/v2/web/search");

            String url = endpoint + "?query=" + encodedQuery + "&limit=" + limit;

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "SmartSearchBot/1.0 (+http://localhost:8080/bot)")
                    .header("Accept", "application/json")
                    .GET();

            if (apiKey.startsWith("Bearer ") || apiKey.startsWith("Api-Key ")) {
                reqBuilder.header("Authorization", apiKey);
            } else {
                reqBuilder.header("Authorization", "Api-Key " + apiKey);
            }

            HttpClient client = getHttpClient();
            if (client == null) return results;

            HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode items = root.path("results");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String title = item.path("title").asText();
                        String link = item.path("url").asText();
                        String snippet = item.path("snippet").asText();
                        String domain = extractDomain(link);

                        SearchResultItemDto dto = new SearchResultItemDto(title, link, snippet, "ALL", domain);
                        dto.setSource("YANDEX");
                        dto.setSourceBadge("Yandex Search");
                        dto.setSourceBadgeClass("badge-provider-yandex");
                        dto.addVerifiedProvider("Yandex");
                        results.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Yandex API search error: {}", e.getMessage());
        }

        return results;
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : "yandex.com";
        } catch (Exception e) {
            return "web";
        }
    }
}

