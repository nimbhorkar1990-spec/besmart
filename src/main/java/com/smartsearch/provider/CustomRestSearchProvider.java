package com.smartsearch.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsearch.dto.SearchRequestDto;
import com.smartsearch.dto.SearchResultItemDto;
import com.smartsearch.model.SearchProviderConfig;
import com.smartsearch.repository.SearchProviderConfigRepository;
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
public class CustomRestSearchProvider implements SearchProvider {

    private static final Logger logger = LoggerFactory.getLogger(CustomRestSearchProvider.class);

    private final SearchProviderConfigRepository providerConfigRepository;
    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public CustomRestSearchProvider(SearchProviderConfigRepository providerConfigRepository) {
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
                        logger.warn("Failed to initialize HttpClient for custom provider: {}", e.getMessage());
                    }
                }
            }
        }
        return httpClient;
    }

    @Override
    public String getProviderId() {
        return "CUSTOM_REST";
    }

    @Override
    public String getDisplayName() {
        return "Dynamic Custom REST Engines";
    }

    @Override
    public boolean isEnabled() {
        // Active if any custom provider is enabled and connected
        return providerConfigRepository.findAll().stream()
                .anyMatch(p -> Boolean.TRUE.equals(p.getIsCustom()) && Boolean.TRUE.equals(p.getEnabled()));
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public int getPriority() {
        return 7;
    }

    @Override
    public List<SearchResultItemDto> search(SearchRequestDto request) {
        List<SearchResultItemDto> results = new ArrayList<>();
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return results;
        }

        List<SearchProviderConfig> customActive = providerConfigRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsCustom())
                        && Boolean.TRUE.equals(p.getEnabled())
                        && "CONNECTED".equalsIgnoreCase(p.getConnectionStatus())
                        && p.getEndpointUrl() != null && !p.getEndpointUrl().isBlank())
                .toList();

        for (SearchProviderConfig cfg : customActive) {
            try {
                List<SearchResultItemDto> providerResults = executeCustomQuery(cfg, request.getQuery().trim());
                results.addAll(providerResults);
            } catch (Exception e) {
                logger.warn("Custom provider {} execution failed: {}", cfg.getProviderId(), e.getMessage());
            }
        }

        return results;
    }

    private List<SearchResultItemDto> executeCustomQuery(SearchProviderConfig cfg, String query) throws Exception {
        List<SearchResultItemDto> items = new ArrayList<>();
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String endpoint = cfg.getEndpointUrl();

        String url = endpoint;
        if (endpoint.contains("?")) {
            url += "&q=" + encodedQuery;
        } else {
            url += "?q=" + encodedQuery;
        }

        if ("API_KEY_QUERY".equalsIgnoreCase(cfg.getAuthMethod()) && cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
            url += "&api_key=" + URLEncoder.encode(cfg.getApiKey(), StandardCharsets.UTF_8);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(cfg.getTimeoutMs() != null ? cfg.getTimeoutMs() : 3000))
                .header("User-Agent", "SmartSearchBot/1.0 (+http://localhost:8080/bot)")
                .header("Accept", "application/json")
                .GET();

        if ("API_KEY_HEADER".equalsIgnoreCase(cfg.getAuthMethod()) && cfg.getApiKey() != null) {
            String headerName = (cfg.getAuthHeaderName() != null && !cfg.getAuthHeaderName().isBlank())
                    ? cfg.getAuthHeaderName() : "X-API-KEY";
            builder.header(headerName, cfg.getApiKey());
        } else if ("BEARER_TOKEN".equalsIgnoreCase(cfg.getAuthMethod()) && cfg.getApiKey() != null) {
            builder.header("Authorization", "Bearer " + cfg.getApiKey());
        }

        HttpClient client = getHttpClient();
        if (client == null) return items;

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 && response.body() != null) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode resultsArray = findResultsArray(root);

            if (resultsArray != null && resultsArray.isArray()) {
                int limit = cfg.getResultLimit() != null ? cfg.getResultLimit() : 10;
                for (JsonNode node : resultsArray) {
                    String title = findField(node, "title", "name", "headline");
                    String link = findField(node, "url", "link", "href", "target");
                    String snippet = findField(node, "snippet", "desc", "description", "content", "summary");

                    if (link == null || link.isBlank()) continue;

                    String domain = extractDomain(link);
                    SearchResultItemDto dto = new SearchResultItemDto(
                            (title != null && !title.isBlank()) ? title : link,
                            link,
                            (snippet != null && !snippet.isBlank()) ? snippet : "Custom search result",
                            "ALL",
                            domain
                    );
                    dto.setSource(cfg.getProviderId());
                    dto.setSourceBadge(cfg.getDisplayName());
                    dto.setSourceBadgeClass("badge bg-dark-subtle text-dark border");
                    dto.addVerifiedProvider(cfg.getDisplayName());
                    items.add(dto);

                    if (items.size() >= limit) break;
                }
            }
        }
        return items;
    }

    private JsonNode findResultsArray(JsonNode root) {
        if (root.isArray()) return root;
        String[] possibleKeys = {"results", "items", "hits", "data", "webPages", "value", "list"};
        for (String k : possibleKeys) {
            if (root.has(k) && root.get(k).isArray()) {
                return root.get(k);
            }
        }
        if (root.has("response") && root.get("response").has("results")) {
            return root.get("response").get("results");
        }
        return null;
    }

    private String findField(JsonNode node, String... keys) {
        for (String k : keys) {
            if (node.has(k) && !node.get(k).asText().isBlank()) {
                return node.get(k).asText();
            }
        }
        return null;
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : "web";
        } catch (Exception e) {
            return "web";
        }
    }
}

