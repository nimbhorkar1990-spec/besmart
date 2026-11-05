package com.smartsearch.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsearch.dto.SearchRequestDto;
import com.smartsearch.dto.SearchResultItemDto;
import com.smartsearch.model.SearchProviderConfig;
import com.smartsearch.repository.SearchProviderConfigRepository;
import com.smartsearch.service.SystemSettingService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
public class DuckDuckGoSearchProvider implements SearchProvider {

    private static final Logger logger = LoggerFactory.getLogger(DuckDuckGoSearchProvider.class);

    private final SystemSettingService settingService;
    private final SearchProviderConfigRepository providerConfigRepository;
    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public DuckDuckGoSearchProvider(SystemSettingService settingService,
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
                        logger.warn("Failed to initialize HttpClient for DuckDuckGo provider: {}", e.getMessage());
                    }
                }
            }
        }
        return httpClient;
    }

    @Override
    public String getProviderId() {
        return "DUCKDUCKGO";
    }

    @Override
    public String getDisplayName() {
        return "DuckDuckGo Search";
    }

    @Override
    public boolean isEnabled() {
        Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("DUCKDUCKGO");
        return cfg.map(SearchProviderConfig::getEnabled)
                .orElseGet(() -> settingService.getBooleanSetting("DUCKDUCKGO_SEARCH_ENABLED", true));
    }

    @Override
    public boolean isConfigured() {
        return true; // DuckDuckGo works out-of-the-box via official API and HTML interface
    }

    @Override
    public int getPriority() {
        Optional<SearchProviderConfig> cfg = providerConfigRepository.findByProviderId("DUCKDUCKGO");
        return cfg.map(SearchProviderConfig::getPriority).orElse(2);
    }

    @Override
    public List<SearchResultItemDto> search(SearchRequestDto request) {
        List<SearchResultItemDto> results = new ArrayList<>();
        if (!isEnabled() || request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return results;
        }

        String query = request.getQuery().trim();

        // 1. First attempt: DuckDuckGo Instant Answer / Topics API
        try {
            List<SearchResultItemDto> apiResults = searchInstantAnswerApi(query);
            results.addAll(apiResults);
        } catch (Exception e) {
            logger.debug("DuckDuckGo Instant Answer API call error: {}", e.getMessage());
        }

        // 2. Second attempt: DuckDuckGo HTML Lite interface if API produced few or no direct web results
        if (results.size() < 5) {
            try {
                List<SearchResultItemDto> htmlResults = searchHtmlLite(query);
                for (SearchResultItemDto item : htmlResults) {
                    if (results.stream().noneMatch(r -> r.getUrl().equalsIgnoreCase(item.getUrl()))) {
                        results.add(item);
                    }
                }
            } catch (Exception e) {
                logger.debug("DuckDuckGo HTML lite search error: {}", e.getMessage());
            }
        }

        return results;
    }

    private List<SearchResultItemDto> searchInstantAnswerApi(String query) throws Exception {
        List<SearchResultItemDto> results = new ArrayList<>();
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://api.duckduckgo.com/?q=" + encodedQuery + "&format=json&no_html=1&skip_disambig=1";

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(3))
                .header("User-Agent", "SmartSearchBot/1.0 (+http://localhost:8080/bot; info@smartsearch.local)")
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpClient client = getHttpClient();
        if (client == null) return results;

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 && response.body() != null) {
            JsonNode root = objectMapper.readTree(response.body());

            // 1. Primary Abstract Result
            String abstractUrl = root.path("AbstractURL").asText(null);
            String abstractText = root.path("AbstractText").asText(null);
            String heading = root.path("Heading").asText(null);
            String image = root.path("Image").asText(null);

            if (abstractUrl != null && !abstractUrl.isBlank() && abstractText != null && !abstractText.isBlank()) {
                SearchResultItemDto mainDto = new SearchResultItemDto(
                        (heading != null && !heading.isBlank()) ? heading : query,
                        abstractUrl,
                        abstractText,
                        "ALL",
                        extractDomain(abstractUrl)
                );
                mainDto.setSource("DUCKDUCKGO");
                mainDto.setSourceBadge("DuckDuckGo");
                mainDto.setSourceBadgeClass("badge-provider-ddg");
                if (image != null && !image.isBlank()) {
                    mainDto.setImageUrl(image.startsWith("http") ? image : "https://duckduckgo.com" + image);
                }
                mainDto.addVerifiedProvider("DuckDuckGo");
                results.add(mainDto);
            }

            // 2. Direct Results Array
            JsonNode resultsArray = root.path("Results");
            if (resultsArray.isArray()) {
                for (JsonNode rNode : resultsArray) {
                    processDuckNode(rNode, query, results);
                }
            }

            // 3. RelatedTopics Array
            JsonNode relatedTopics = root.path("RelatedTopics");
            if (relatedTopics.isArray()) {
                for (JsonNode tNode : relatedTopics) {
                    if (tNode.has("Topics") && tNode.path("Topics").isArray()) {
                        for (JsonNode subTopic : tNode.path("Topics")) {
                            processDuckNode(subTopic, query, results);
                        }
                    } else {
                        processDuckNode(tNode, query, results);
                    }
                }
            }
        }
        return results;
    }

    private void processDuckNode(JsonNode node, String query, List<SearchResultItemDto> results) {
        String firstUrl = node.path("FirstURL").asText(null);
        String text = node.path("Text").asText(null);
        String iconUrl = node.path("Icon").path("URL").asText(null);

        if (firstUrl == null || firstUrl.isBlank() || text == null || text.isBlank()) {
            return;
        }

        String title = text;
        String snippet = text;
        int dashIdx = text.indexOf(" - ");
        if (dashIdx > 0 && dashIdx < 60) {
            title = text.substring(0, dashIdx).trim();
            snippet = text.substring(dashIdx + 3).trim();
        } else if (text.length() > 60) {
            title = text.substring(0, 60).trim() + "...";
        }

        SearchResultItemDto dto = new SearchResultItemDto(
                title,
                firstUrl,
                snippet,
                "ALL",
                extractDomain(firstUrl)
        );
        dto.setSource("DUCKDUCKGO");
        dto.setSourceBadge("DuckDuckGo");
        dto.setSourceBadgeClass("badge-provider-ddg");
        if (iconUrl != null && !iconUrl.isBlank()) {
            dto.setImageUrl(iconUrl.startsWith("http") ? iconUrl : "https://duckduckgo.com" + iconUrl);
        }
        dto.addVerifiedProvider("DuckDuckGo");
        results.add(dto);
    }

    private List<SearchResultItemDto> searchHtmlLite(String query) throws Exception {
        List<SearchResultItemDto> results = new ArrayList<>();
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://html.duckduckgo.com/html/?q=" + encodedQuery;

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .GET()
                .build();

        HttpClient client = getHttpClient();
        if (client == null) return results;

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
            Document doc = Jsoup.parse(response.body());
            Elements resultElements = doc.select(".result");

            for (Element el : resultElements) {
                Element linkEl = el.selectFirst(".result__title a");
                Element snippetEl = el.selectFirst(".result__snippet");

                if (linkEl == null) continue;

                String rawHref = linkEl.attr("href");
                String title = linkEl.text().trim();
                String snippet = (snippetEl != null) ? snippetEl.text().trim() : "";

                String realUrl = extractDuckTargetUrl(rawHref);
                if (realUrl.isBlank()) continue;

                SearchResultItemDto dto = new SearchResultItemDto(
                        !title.isBlank() ? title : realUrl,
                        realUrl,
                        !snippet.isBlank() ? snippet : "DuckDuckGo web result for " + query,
                        "ALL",
                        extractDomain(realUrl)
                );
                dto.setSource("DUCKDUCKGO");
                dto.setSourceBadge("DuckDuckGo");
                dto.setSourceBadgeClass("badge-provider-ddg");
                dto.addVerifiedProvider("DuckDuckGo");
                results.add(dto);

                if (results.size() >= 10) break;
            }
        }
        return results;
    }

    private String extractDuckTargetUrl(String href) {
        if (href == null || href.isBlank()) return "";
        try {
            if (href.startsWith("//")) {
                href = "https:" + href;
            }
            if (href.contains("uddg=")) {
                int start = href.indexOf("uddg=") + 5;
                int end = href.indexOf("&", start);
                String targetEncoded = (end != -1) ? href.substring(start, end) : href.substring(start);
                return URLDecoder.decode(targetEncoded, StandardCharsets.UTF_8);
            }
            if (href.startsWith("http://") || href.startsWith("https://")) {
                return href;
            }
        } catch (Exception ignored) {
        }
        return href;
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : "duckduckgo.com";
        } catch (Exception e) {
            return "web";
        }
    }
}

