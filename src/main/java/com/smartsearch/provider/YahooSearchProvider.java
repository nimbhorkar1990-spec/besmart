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
import java.net.URLDecoder;
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
public class YahooSearchProvider implements SearchProvider {

    private static final Logger logger = LoggerFactory.getLogger(YahooSearchProvider.class);

    private final SystemSettingService settingService;
    private final SearchProviderConfigRepository providerConfigRepository;
    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public YahooSearchProvider(SystemSettingService settingService,
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
                        logger.warn("Failed to initialize HttpClient for Yahoo provider: {}", e.getMessage());
                    }
                }
            }
        }
        return httpClient;
    }

    @Override
    public String getProviderId() {
        return "YAHOO";
    }

    @Override
    public String getDisplayName() {
        return "Yahoo Search";
    }

    @Override
    public boolean isEnabled() {
        Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("YAHOO");
        return cfg.map(SearchProviderConfig::getEnabled)
                .orElseGet(() -> settingService.getBooleanSetting("YAHOO_SEARCH_ENABLED", true));
    }

    @Override
    public boolean isConfigured() {
        // Yahoo is configured out of the box via Yahoo open syndication RSS and supports optional RapidAPI key
        return true;
    }

    @Override
    public int getPriority() {
        Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("YAHOO");
        return cfg.map(SearchProviderConfig::getPriority).orElse(3);
    }

    @Override
    public List<SearchResultItemDto> search(SearchRequestDto request) {
        List<SearchResultItemDto> results = new ArrayList<>();
        if (!isEnabled() || request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return results;
        }

        Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("YAHOO");
        String apiKey = cfg.map(SearchProviderConfig::getApiKey).orElse("");
        if (apiKey.isBlank()) {
            apiKey = settingService.getSetting("YAHOO_API_KEY", "");
        }

        // 1. If API key is present, attempt RapidAPI / Yahoo Search API
        if (!apiKey.isBlank()) {
            try {
                results = searchViaRapidApi(request.getQuery().trim(), apiKey.trim());
                if (!results.isEmpty()) {
                    return results;
                }
            } catch (Exception e) {
                logger.warn("Yahoo API search with key failed, falling back to open syndication: {}", e.getMessage());
            }
        }

        // 2. Open syndication fallback (Yahoo RSS Search Feed)
        try {
            results = searchViaYahooRss(request.getQuery().trim());
        } catch (Exception e) {
            logger.warn("Yahoo RSS syndication search error: {}", e.getMessage());
        }

        return results;
    }

    private List<SearchResultItemDto> searchViaRapidApi(String query, String apiKey) throws Exception {
        List<SearchResultItemDto> results = new ArrayList<>();
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://yahoo-search.p.rapidapi.com/web?q=" + encodedQuery + "&count=10";

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .header("X-RapidAPI-Key", apiKey)
                .header("X-RapidAPI-Host", "yahoo-search.p.rapidapi.com")
                .header("Accept", "application/json")
                .header("User-Agent", "SmartSearchBot/1.0 (+http://localhost:8080/bot)")
                .GET()
                .build();

        HttpClient client = getHttpClient();
        if (client == null) return results;

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.path("results");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    String title = item.path("title").asText();
                    String link = item.path("url").asText();
                    String snippet = item.path("description").asText(item.path("snippet").asText());
                    String domain = extractDomain(link);

                    SearchResultItemDto dto = new SearchResultItemDto(title, link, snippet, "ALL", domain);
                    dto.setSource("YAHOO");
                    dto.setSourceBadge("Yahoo Search");
                    dto.setSourceBadgeClass("badge-provider-yahoo");
                    dto.addVerifiedProvider("Yahoo");
                    results.add(dto);
                }
            }
        }
        return results;
    }

    private List<SearchResultItemDto> searchViaYahooRss(String query) throws Exception {
        List<SearchResultItemDto> results = new ArrayList<>();
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://news.search.yahoo.com/rss?p=" + encodedQuery;

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(4))
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
            for (int i = 0; i < Math.min(items.getLength(), 10); i++) {
                Element item = (Element) items.item(i);
                String title = getTagValue(item, "title");
                String rawLink = getTagValue(item, "link");
                String description = getTagValue(item, "description");
                String pubDate = getTagValue(item, "pubDate");

                if (rawLink == null || rawLink.isBlank()) continue;

                String cleanLink = extractRealUrl(rawLink);
                String cleanSnippet = stripHtml(description);
                String domain = extractDomain(cleanLink);

                SearchResultItemDto dto = new SearchResultItemDto(
                        (title != null && !title.isBlank()) ? title : cleanLink,
                        cleanLink,
                        (cleanSnippet != null && !cleanSnippet.isBlank()) ? cleanSnippet : "Yahoo syndicated search result for " + query,
                        "ALL",
                        domain
                );
                dto.setSource("YAHOO");
                dto.setSourceBadge("Yahoo Search");
                dto.setSourceBadgeClass("badge-provider-yahoo");
                dto.setPublishedDate(pubDate);
                dto.addVerifiedProvider("Yahoo");
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

    private String extractRealUrl(String rawUrl) {
        if (rawUrl == null) return "";
        try {
            // Yahoo RSS link pattern: https://r.search.yahoo.com/.../RU=https%3a%2f%2fexample.com.../RK=...
            int ruIndex = rawUrl.indexOf("/RU=");
            if (ruIndex != -1) {
                int start = ruIndex + 4;
                int end = rawUrl.indexOf("/RK=", start);
                String encodedTarget = (end != -1) ? rawUrl.substring(start, end) : rawUrl.substring(start);
                return URLDecoder.decode(encodedTarget, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return rawUrl;
    }

    private String stripHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", "").replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim();
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : "yahoo.com";
        } catch (Exception e) {
            return "web";
        }
    }
}

