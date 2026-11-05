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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
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
public class MojeekSearchProvider implements SearchProvider {

    private static final Logger logger = LoggerFactory.getLogger(MojeekSearchProvider.class);

    private final SystemSettingService settingService;
    private final SearchProviderConfigRepository providerConfigRepository;
    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public MojeekSearchProvider(SystemSettingService settingService,
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
                        logger.warn("Failed to initialize HttpClient for Mojeek provider: {}", e.getMessage());
                    }
                }
            }
        }
        return httpClient;
    }

    @Override
    public String getProviderId() {
        return "MOJEEK";
    }

    @Override
    public String getDisplayName() {
        return "Mojeek Independent Index";
    }

    @Override
    public boolean isEnabled() {
        Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("MOJEEK");
        return cfg.map(SearchProviderConfig::getEnabled)
                .orElseGet(() -> settingService.getBooleanSetting("MOJEEK_SEARCH_ENABLED", false));
    }

    @Override
    public boolean isConfigured() {
        return true; // Mojeek operates via official API or open web RSS interface
    }

    @Override
    public int getPriority() {
        Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("MOJEEK");
        return cfg.map(SearchProviderConfig::getPriority).orElse(4);
    }

    @Override
    public List<SearchResultItemDto> search(SearchRequestDto request) {
        List<SearchResultItemDto> results = new ArrayList<>();
        if (!isEnabled() || request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return results;
        }

        Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("MOJEEK");
        String apiKey = cfg.map(SearchProviderConfig::getApiKey).orElse("");
        if (apiKey.isBlank()) {
            apiKey = settingService.getSetting("MOJEEK_API_KEY", "");
        }

        int limit = cfg.map(SearchProviderConfig::getResultLimit).orElse(10);
        int timeoutMs = cfg.map(SearchProviderConfig::getTimeoutMs).orElse(3000);

        // 1. If API Key is present, query Mojeek REST JSON API
        if (!apiKey.isBlank()) {
            try {
                results = searchViaMojeekApi(request.getQuery().trim(), apiKey.trim(), limit, timeoutMs);
                if (!results.isEmpty()) {
                    return results;
                }
            } catch (Exception e) {
                logger.warn("Mojeek API search error: {}", e.getMessage());
            }
        }

        // 2. Fallback to Mojeek RSS search
        try {
            results = searchViaMojeekRss(request.getQuery().trim(), limit, timeoutMs);
        } catch (Exception e) {
            logger.debug("Mojeek RSS search error: {}", e.getMessage());
        }

        return results;
    }

    private List<SearchResultItemDto> searchViaMojeekApi(String query, String apiKey, int limit, int timeoutMs) throws Exception {
        List<SearchResultItemDto> results = new ArrayList<>();
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = String.format("https://www.mojeek.com/search?api_key=%s&q=%s&fmt=json&size=%d",
                apiKey, encodedQuery, limit);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", "SmartSearchBot/1.0 (+http://localhost:8080/bot)")
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpClient client = getHttpClient();
        if (client == null) return results;

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 && response.body() != null) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode resultsArray = root.path("response").path("results");
            if (resultsArray.isArray()) {
                for (JsonNode item : resultsArray) {
                    String title = item.path("title").asText();
                    String link = item.path("url").asText();
                    String desc = item.path("desc").asText();
                    String domain = extractDomain(link);

                    SearchResultItemDto dto = new SearchResultItemDto(title, link, desc, "ALL", domain);
                    dto.setSource("MOJEEK");
                    dto.setSourceBadge("Mojeek Search");
                    dto.setSourceBadgeClass("badge-provider-mojeek");
                    dto.addVerifiedProvider("Mojeek");
                    results.add(dto);
                }
            }
        }
        return results;
    }

    private List<SearchResultItemDto> searchViaMojeekRss(String query, int limit, int timeoutMs) throws Exception {
        List<SearchResultItemDto> results = new ArrayList<>();
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://www.mojeek.com/search?q=" + encodedQuery + "&fmt=rss";

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
                .GET()
                .build();

        HttpClient client = getHttpClient();
        if (client == null) return results;

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(response.body())));

            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < Math.min(items.getLength(), limit); i++) {
                Element item = (Element) items.item(i);
                String title = getTagValue(item, "title");
                String link = getTagValue(item, "link");
                String description = getTagValue(item, "description");

                if (link == null || link.isBlank()) continue;

                String domain = extractDomain(link);
                SearchResultItemDto dto = new SearchResultItemDto(
                        (title != null && !title.isBlank()) ? title : link,
                        link,
                        (description != null && !description.isBlank()) ? description : "Mojeek web result for " + query,
                        "ALL",
                        domain
                );
                dto.setSource("MOJEEK");
                dto.setSourceBadge("Mojeek Search");
                dto.setSourceBadgeClass("badge-provider-mojeek");
                dto.addVerifiedProvider("Mojeek");
                results.add(dto);
            }
        }
        return results;
    }

    private String getTagValue(Element element, String tagName) {
        NodeList nl = element.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0 && nl.item(0).getFirstChild() != null) {
            return nl.item(0).getFirstChild().getNodeValue();
        }
        return null;
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : "mojeek.com";
        } catch (Exception e) {
            return "web";
        }
    }
}

