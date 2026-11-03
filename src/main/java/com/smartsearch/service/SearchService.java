package com.smartsearch.service;

import com.smartsearch.dto.SearchRequestDto;
import com.smartsearch.dto.SearchResponseDto;
import com.smartsearch.dto.SearchResponseDto.KnowledgeCardDto;
import com.smartsearch.dto.SearchResultItemDto;
import com.smartsearch.model.CrawlJob;
import com.smartsearch.model.SearchQueryRecord;
import com.smartsearch.model.User;
import com.smartsearch.provider.MultiSearchAggregatorService;
import com.smartsearch.queue.CrawlQueueManager;
import com.smartsearch.ranking.SearchRankingService;
import com.smartsearch.repository.CrawlJobRepository;
import com.smartsearch.repository.SearchQueryRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    private final LuceneService luceneService;
    private final ExternalSearchService externalSearchService;
    private final MultiSearchAggregatorService multiSearchAggregatorService;
    private final SearchRankingService searchRankingService;
    private final CrawlQueueManager crawlQueueManager;
    private final CrawlJobRepository crawlJobRepository;
    private final SearchQueryRecordRepository searchQueryRecordRepository;
    private final AnalyticsService analyticsService;
    private final SystemSettingService settingService;
    private final UserService userService;

    public SearchService(LuceneService luceneService,
                         ExternalSearchService externalSearchService,
                         MultiSearchAggregatorService multiSearchAggregatorService,
                         SearchRankingService searchRankingService,
                         CrawlQueueManager crawlQueueManager,
                         CrawlJobRepository crawlJobRepository,
                         SearchQueryRecordRepository searchQueryRecordRepository,
                         AnalyticsService analyticsService,
                         SystemSettingService settingService,
                         UserService userService) {
        this.luceneService = luceneService;
        this.externalSearchService = externalSearchService;
        this.multiSearchAggregatorService = multiSearchAggregatorService;
        this.searchRankingService = searchRankingService;
        this.crawlQueueManager = crawlQueueManager;
        this.crawlJobRepository = crawlJobRepository;
        this.searchQueryRecordRepository = searchQueryRecordRepository;
        this.analyticsService = analyticsService;
        this.settingService = settingService;
        this.userService = userService;
    }

    public SearchResponseDto performSearch(SearchRequestDto request, String ipAddress) {
        long startTime = System.currentTimeMillis();
        String query = request.getQuery() != null ? request.getQuery().trim() : "";
        String category = request.getCategory() != null ? request.getCategory().toUpperCase() : "ALL";

        int page = Math.max(request.getPage(), 1);
        int pageSize = request.getSize() > 0 ? request.getSize() : settingService.getIntSetting("DEFAULT_PAGE_SIZE", 10);
        request.setSize(pageSize);

        Optional<User> currentUser = userService.getCurrentUser();

        // 1. Record analytics
        if (!query.isBlank()) {
            analyticsService.trackSearch(query, category, currentUser.orElse(null), ipAddress);
        }

        List<SearchResultItemDto> results = new ArrayList<>();
        long totalHits = 0;
        KnowledgeCardDto knowledgeCard = null;
        String discoveryStatus = null;
        int externalDiscoveredCount = 0;
        boolean crawlerActive = false;

        // 2. Direct URL Detection (e.g., user entered https://example.com)
        if (query.startsWith("http://") || query.startsWith("https://")) {
            crawlQueueManager.enqueueDiscoveredUrls(List.of(query), query, "USER_INPUT_URL", null);
            crawlerActive = true;
            discoveryStatus = "SmartSearch Crawler: Enqueued direct URL for automated crawling & indexing.";
        }

        // 3. Category Specific Search Handlers
        switch (category) {
            case "IMAGES" -> {
                results = externalSearchService.searchImages(query);
                totalHits = results.size();
                discoveryStatus = "SmartSearch Media Engine: Aggregated image assets.";
            }
            case "VIDEOS" -> {
                results = externalSearchService.searchVideos(query);
                totalHits = results.size();
                discoveryStatus = "SmartSearch Media Engine: Aggregated video streams.";
            }
            case "NEWS" -> {
                results = externalSearchService.searchNews(query);
                totalHits = results.size();
                discoveryStatus = "SmartSearch News Engine: Aggregated live news articles.";
            }
            case "MAPS" -> {
                results = externalSearchService.searchMaps(query);
                totalHits = results.size();
                discoveryStatus = "SmartSearch Location Engine: Aggregated map points.";
            }
            case "SHOPPING" -> {
                results = externalSearchService.searchShopping(query);
                totalHits = results.size();
                discoveryStatus = "SmartSearch Shopping Engine: Aggregated product listings.";
            }
            default -> {
                // "ALL" Category: Multi-source search, crawling, and indexing workflow

                // Step 2 & 3: Local Lucene Index Check & Freshness Assessment
                List<SearchResultItemDto> luceneResults = new ArrayList<>();
                long luceneHits = 0;

                try {
                    Map<String, Object> luceneSearch = luceneService.search(request);
                    @SuppressWarnings("unchecked")
                    List<SearchResultItemDto> items = (List<SearchResultItemDto>) luceneSearch.get("items");
                    luceneResults = items;
                    luceneHits = (long) luceneSearch.get("totalHits");
                } catch (Exception e) {
                    logger.warn("Lucene search error: {}", e.getMessage());
                }

                // Step 4: External Search Trigger (if local index is insufficient or missing)
                boolean externalEnabled = settingService.getBooleanSetting("EXTERNAL_SEARCH_ENABLED", true);
                List<SearchResultItemDto> externalResults = new ArrayList<>();
                boolean externalTriggered = false;

                // Threshold: If local results < 3 or user explicitly queries, fetch live external search
                if (externalEnabled && !query.isBlank() && (luceneHits < 3 || page == 1)) {
                    externalResults = multiSearchAggregatorService.aggregateExternalSearch(request);
                    externalTriggered = !externalResults.isEmpty();
                }

                // Step 5 & 6: URL Discovery & Automated Crawling Queue
                if (externalTriggered) {
                    List<String> discoveredUrls = externalResults.stream()
                            .map(SearchResultItemDto::getUrl)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();

                    externalDiscoveredCount = discoveredUrls.size();

                    if (!discoveredUrls.isEmpty()) {
                        // Create persistent CrawlJob audit record
                        CrawlJob crawlJob = new CrawlJob("AUTO_SEARCH", query);
                        crawlJob.setPagesDiscovered(discoveredUrls.size());
                        crawlJobRepository.save(crawlJob);

                        // Enqueue discovered URLs into background crawling queue
                        crawlQueueManager.enqueueDiscoveredUrls(discoveredUrls, query, "MULTI_SEARCH_ENGINE", crawlJob.getId());
                        crawlerActive = true;

                        discoveryStatus = String.format(
                                "Multi-Engine Discovery Active: %d live external web results · %d URLs queued for automated crawler & Lucene indexing",
                                externalResults.size(), discoveredUrls.size()
                        );
                    }
                } else if (luceneHits > 0) {
                    discoveryStatus = String.format("Local Index Engine: %d verified indexed documents found in Apache Lucene", luceneHits);
                } else {
                    discoveryStatus = "SmartSearch Index: No results found locally. Discovery queue ready.";
                }

                // Step 8 & 9: Search Result Ranking & Multi-Source Merge
                results = searchRankingService.rankAndMerge(query, luceneResults, externalResults);
                totalHits = Math.max(results.size(), luceneHits);

                // Fetch Knowledge Card for ALL category on page 1
                if (!query.isBlank() && page == 1) {
                    try {
                        knowledgeCard = externalSearchService.fetchKnowledgeCard(query);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // 4. Record search queries table (search_queries)
        long durationMs = System.currentTimeMillis() - startTime;
        try {
            SearchQueryRecord queryRecord = new SearchQueryRecord(
                    query, category, totalHits, durationMs,
                    (externalDiscoveredCount > 0 ? "HYBRID_EXTERNAL" : "LUCENE_LOCAL"),
                    externalDiscoveredCount > 0, ipAddress
            );
            searchQueryRecordRepository.save(queryRecord);
        } catch (Exception e) {
            logger.debug("Failed to save search_query record: {}", e.getMessage());
        }

        // 5. Mark Saved Status for current user
        if (currentUser.isPresent()) {
            User user = currentUser.get();
            for (SearchResultItemDto item : results) {
                if (item.getUrl() != null && userService.isResultSaved(user, item.getUrl())) {
                    item.setSaved(true);
                }
            }
        }

        // 6. Pagination Slice for Combined Results
        int fromIndex = Math.min((page - 1) * pageSize, results.size());
        int toIndex = Math.min(fromIndex + pageSize, results.size());
        List<SearchResultItemDto> pagedResults = (fromIndex < results.size())
                ? results.subList(fromIndex, toIndex)
                : Collections.emptyList();

        // 7. Build Response
        SearchResponseDto response = new SearchResponseDto(query, category, totalHits, page, pageSize);
        response.setResults(pagedResults);
        response.setKnowledgeCard(knowledgeCard);
        response.setDiscoveryStatus(discoveryStatus);
        response.setExternalDiscoveredCount(externalDiscoveredCount);
        response.setCrawlerActive(crawlerActive);

        // 8. Generate Related Searches
        if (!query.isBlank()) {
            List<String> related = generateRelatedSearches(query);
            response.setRelatedSearches(related);
        }

        double durationSec = durationMs / 1000.0;
        response.setSearchDurationMs(durationSec);

        return response;
    }

    private List<String> generateRelatedSearches(String query) {
        String q = query.trim();
        List<String> list = new ArrayList<>();
        list.add(q + " tutorial");
        list.add(q + " definition and overview");
        list.add("best " + q + " examples 2026");
        list.add("how does " + q + " work");
        list.add(q + " open source projects");
        return list;
    }
}
