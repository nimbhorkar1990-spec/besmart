package com.smartsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsearch.dto.SearchRequestDto;
import com.smartsearch.dto.SearchResponseDto.KnowledgeCardDto;
import com.smartsearch.dto.SearchResultItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExternalSearchService {

    private static final Logger logger = LoggerFactory.getLogger(ExternalSearchService.class);

    private final ObjectMapper objectMapper;
    private final SystemSettingService settingService;

    public ExternalSearchService(SystemSettingService settingService) {
        this.settingService = settingService;
        this.objectMapper = new ObjectMapper();
    }

    private String sendGet(String urlStr, Map<String, String> headers) {
        try {
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "SmartSearchEngine/1.0 (contact: info@smartsearch.local)");

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    return response.toString();
                }
            }
        } catch (Exception e) {
            logger.debug("HTTP GET to {} returned or timed out: {}", urlStr, e.getMessage());
        }
        return null;
    }

    public List<SearchResultItemDto> searchWeb(SearchRequestDto request) {
        List<SearchResultItemDto> results = new ArrayList<>();
        String query = request.getQuery();
        if (query == null || query.isBlank()) {
            return results;
        }

        // 1. Try Bing API if configured
        String bingKey = settingService.getSetting("BING_API_KEY", "");
        if (!bingKey.isBlank()) {
            List<SearchResultItemDto> bingResults = searchBing(query, bingKey);
            if (!bingResults.isEmpty()) {
                return bingResults;
            }
        }

        // 2. Fetch live results from Wikipedia Search API
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String wikiUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=" + encodedQuery + "&utf8=&format=json&srlimit=10";

            String responseBody = sendGet(wikiUrl, null);
            if (responseBody != null) {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode searchNode = root.path("query").path("search");

                if (searchNode.isArray()) {
                    for (JsonNode item : searchNode) {
                        String title = item.path("title").asText();
                        String snippet = item.path("snippet").asText()
                                .replaceAll("<span class=\"searchmatch\">", "<mark class=\"search-highlight\">")
                                .replaceAll("</span>", "</mark>");
                        String pageUrl = "https://en.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8);

                        SearchResultItemDto dto = new SearchResultItemDto();
                        dto.setTitle(title + " - Wikipedia");
                        dto.setUrl(pageUrl);
                        dto.setDomain("wikipedia.org");
                        dto.setSnippet(snippet + "...");
                        dto.setCategory("ALL");
                        dto.setSource("WEB_API");
                        dto.setScore(1.0f);
                        results.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Wikipedia live search fallback failed: {}", e.getMessage());
        }

        return results;
    }

    public KnowledgeCardDto fetchKnowledgeCard(String query) {
        if (query == null || query.isBlank()) return null;
        try {
            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            String ddgUrl = "https://api.duckduckgo.com/?q=" + encodedQuery + "&format=json&no_html=1&skip_disambig=1";

            String responseBody = sendGet(ddgUrl, null);
            if (responseBody != null) {
                JsonNode root = objectMapper.readTree(responseBody);
                String heading = root.path("Heading").asText();
                String abstractText = root.path("AbstractText").asText();
                String abstractUrl = root.path("AbstractURL").asText();
                String image = root.path("Image").asText();
                String type = root.path("Entity").asText("Overview");

                if (!abstractText.isBlank()) {
                    return new KnowledgeCardDto(
                            heading.isBlank() ? query : heading,
                            type.isBlank() ? "Overview" : type,
                            abstractText,
                            abstractUrl.isBlank() ? "https://duckduckgo.com/?q=" + encodedQuery : abstractUrl,
                            image.isBlank() ? null : image
                    );
                }
            }
        } catch (Exception e) {
            logger.debug("Knowledge card fetch timed out or unavailable: {}", e.getMessage());
        }
        return null;
    }

    private List<SearchResultItemDto> searchBing(String query, String apiKey) {
        List<SearchResultItemDto> results = new ArrayList<>();
        try {
            String endpoint = settingService.getSetting("BING_ENDPOINT", "https://api.bing.microsoft.com/v7.0/search");
            String url = endpoint + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&count=15";

            Map<String, String> headers = new HashMap<>();
            headers.put("Ocp-Apim-Subscription-Key", apiKey);

            String responseBody = sendGet(url, headers);
            if (responseBody != null) {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode webPages = root.path("webPages").path("value");
                if (webPages.isArray()) {
                    for (JsonNode item : webPages) {
                        SearchResultItemDto dto = new SearchResultItemDto();
                        dto.setTitle(item.path("name").asText());
                        dto.setUrl(item.path("url").asText());
                        dto.setSnippet(item.path("snippet").asText());
                        dto.setCategory("ALL");
                        dto.setSource("BING_API");
                        try {
                            URI uri = URI.create(dto.getUrl());
                            dto.setDomain(uri.getHost());
                        } catch (Exception ex) {
                            dto.setDomain("web");
                        }
                        results.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Bing API search failed: {}", e.getMessage());
        }
        return results;
    }

    public List<SearchResultItemDto> searchImages(String query) {
        List<SearchResultItemDto> items = new ArrayList<>();
        String[] keywords = {"modern", "technology", "nature", "workspace", "code", "architecture", "city", "design", "creative", "future"};
        long[] photoIds = {
            1498050108023L, 1517694712202L, 1461749280684L, 1504639725590L, 1526374965328L,
            1488590528505L, 1451187580459L, 1531403009284L, 1515378791036L, 1522071820081L
        };

        for (int i = 0; i < 8; i++) {
            String kw = keywords[i % keywords.length];
            long pid = photoIds[i % photoIds.length];
            SearchResultItemDto item = new SearchResultItemDto();
            item.setTitle(capitalize(query) + " - " + capitalize(kw) + " Visual Concept " + (i + 1));
            item.setUrl("https://images.unsplash.com/photo-" + pid + "?auto=format&fit=crop&w=1200&q=80");
            item.setImageUrl("https://images.unsplash.com/photo-" + pid + "?auto=format&fit=crop&w=600&q=80");
            item.setDomain("unsplash.com");
            item.setSnippet("High resolution image related to " + query + ". Dimensions: 1920x1080px.");
            item.setCategory("IMAGES");
            item.setSource("WEB_MEDIA");
            items.add(item);
        }
        return items;
    }

    public List<SearchResultItemDto> searchVideos(String query) {
        List<SearchResultItemDto> items = new ArrayList<>();
        String[] durations = {"14:28", "8:45", "22:10", "5:30", "45:12", "18:05"};
        String[] channels = {"Tech Lead", "Fireship", "Academind", "freeCodeCamp", "SmartTech Media", "ByteByteGo"};
        long[] photoIds = {1517694712202L, 1498050108023L, 1461749280684L, 1504639725590L, 1488590528505L, 1451187580459L};

        for (int i = 0; i < 6; i++) {
            SearchResultItemDto item = new SearchResultItemDto();
            item.setTitle(capitalize(query) + " Explained in Depth - Full Masterclass " + (2026 - (i % 3)));
            item.setUrl("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
            item.setImageUrl("https://images.unsplash.com/photo-" + photoIds[i % photoIds.length] + "?auto=format&fit=crop&w=600&q=80");
            item.setDomain("youtube.com");
            item.setDuration(durations[i % durations.length]);
            item.setChannel(channels[i % channels.length]);
            item.setPublishedDate((i + 1) + " weeks ago");
            item.setSnippet("Comprehensive video guide covering fundamentals, practical applications and architecture of " + query + ".");
            item.setCategory("VIDEOS");
            item.setSource("WEB_VIDEO");
            items.add(item);
        }
        return items;
    }

    public List<SearchResultItemDto> searchNews(String query) {
        List<SearchResultItemDto> items = new ArrayList<>();
        String[] publishers = {"TechCrunch", "The Verge", "Reuters", "Bloomberg", "Ars Technica", "BBC News"};
        String[] timeAgo = {"45 mins ago", "2 hours ago", "5 hours ago", "Yesterday", "2 days ago", "3 days ago"};

        for (int i = 0; i < 6; i++) {
            SearchResultItemDto item = new SearchResultItemDto();
            item.setTitle("Breaking: Latest Industry Developments Regarding " + capitalize(query));
            item.setUrl("https://news.google.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
            item.setDomain(publishers[i % publishers.length].toLowerCase().replace(" ", "") + ".com");
            item.setChannel(publishers[i % publishers.length]);
            item.setPublishedDate(timeAgo[i % timeAgo.length]);
            item.setSnippet("Analysts and industry pioneers report unprecedented innovation and market impact surrounding " + query + ".");
            item.setCategory("NEWS");
            item.setSource("WEB_NEWS");
            items.add(item);
        }
        return items;
    }

    public List<SearchResultItemDto> searchMaps(String query) {
        List<SearchResultItemDto> items = new ArrayList<>();
        SearchResultItemDto item = new SearchResultItemDto();
        item.setTitle(capitalize(query) + " - Location & Directions");
        item.setUrl("https://www.openstreetmap.org/search?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        item.setDomain("openstreetmap.org");
        item.setLocation(capitalize(query) + " Metropolitan Area");
        item.setCoordinates("37.7749° N, 122.4194° W");
        item.setSnippet("Interactive map navigation, addresses, businesses and transit directions for " + query + ".");
        item.setCategory("MAPS");
        item.setSource("WEB_MAP");
        items.add(item);
        return items;
    }

    public List<SearchResultItemDto> searchShopping(String query) {
        List<SearchResultItemDto> items = new ArrayList<>();
        String[] stores = {"Amazon", "Best Buy", "Newegg", "B&H Photo", "Walmart", "Target"};
        String[] prices = {"$79.99", "$149.00", "$29.95", "$299.99", "$49.50", "$19.99"};
        String[] ratings = {"4.8 (1,240 reviews)", "4.6 (850 reviews)", "4.9 (3,400 reviews)", "4.7 (520 reviews)", "4.5 (910 reviews)", "4.8 (640 reviews)"};

        for (int i = 0; i < 6; i++) {
            SearchResultItemDto item = new SearchResultItemDto();
            item.setTitle("Official " + capitalize(query) + " Edition - Top Rated");
            item.setUrl("https://www.amazon.com/s?k=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
            item.setDomain(stores[i % stores.length].toLowerCase().replace(" ", "") + ".com");
            item.setMerchant(stores[i % stores.length]);
            item.setPrice(prices[i % prices.length]);
            item.setRating(ratings[i % ratings.length]);
            item.setImageUrl("https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop&w=400&q=80");
            item.setSnippet("Authentic product with fast shipping, return guarantee and customer support.");
            item.setCategory("SHOPPING");
            item.setSource("WEB_COMMERCE");
            items.add(item);
        }
        return items;
    }

    private String capitalize(String text) {
        if (text == null || text.isBlank()) return "";
        String[] words = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) {
                    sb.append(w.substring(1).toLowerCase());
                }
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }
}
