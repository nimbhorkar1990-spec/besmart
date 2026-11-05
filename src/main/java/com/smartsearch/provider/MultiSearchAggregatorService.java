package com.smartsearch.provider;

import com.smartsearch.dto.SearchRequestDto;
import com.smartsearch.dto.SearchResultItemDto;
import com.smartsearch.model.ApiUsageLog;
import com.smartsearch.model.SearchProviderConfig;
import com.smartsearch.repository.ApiUsageLogRepository;
import com.smartsearch.repository.SearchProviderConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class MultiSearchAggregatorService {

    private static final Logger logger = LoggerFactory.getLogger(MultiSearchAggregatorService.class);

    private final List<SearchProvider> providers;
    private final ApiUsageLogRepository apiUsageLogRepository;
    private final SearchProviderConfigRepository providerConfigRepository;
    private final ExecutorService executor;

    public MultiSearchAggregatorService(List<SearchProvider> providers,
                                        ApiUsageLogRepository apiUsageLogRepository,
                                        SearchProviderConfigRepository providerConfigRepository) {
        this.providers = providers;
        this.apiUsageLogRepository = apiUsageLogRepository;
        this.providerConfigRepository = providerConfigRepository;
        this.executor = Executors.newFixedThreadPool(Math.max(8, providers.size() * 2));
    }

    /**
     * Executes parallel queries across all active search engines (Google, Bing, Yahoo, DuckDuckGo, Brave, etc.),
     * removes duplicate URLs, combines multi-source attributions, and returns unified results.
     */
    public List<SearchResultItemDto> aggregateExternalSearch(SearchRequestDto request) {
        List<SearchResultItemDto> aggregated = new ArrayList<>();
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return aggregated;
        }

        // 1. Filter enabled and configured search providers sorted by priority
        List<SearchProvider> activeProviders = providers.stream()
                .filter(p -> p.isEnabled() && p.isConfigured())
                .sorted(Comparator.comparingInt(SearchProvider::getPriority))
                .toList();

        if (activeProviders.isEmpty()) {
            logger.info("No external search providers currently active/configured.");
            return aggregated;
        }

        // 2. Dispatch parallel queries with dedicated error boundaries per provider
        List<CompletableFuture<ProviderSearchResult>> futures = new ArrayList<>();

        for (SearchProvider provider : activeProviders) {
            CompletableFuture<ProviderSearchResult> future = CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                try {
                    List<SearchResultItemDto> items = provider.search(request);
                    long duration = System.currentTimeMillis() - start;
                    recordUsage(provider.getProviderId(), request.getQuery(), 200, items.size(), duration, null);
                    return new ProviderSearchResult(provider.getProviderId(), provider.getDisplayName(), items, true, null);
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - start;
                    logger.warn("Search provider {} encountered error: {}", provider.getProviderId(), e.getMessage());
                    recordUsage(provider.getProviderId(), request.getQuery(), 500, 0, duration, e.getMessage());
                    return new ProviderSearchResult(provider.getProviderId(), provider.getDisplayName(), Collections.emptyList(), false, e.getMessage());
                }
            }, executor);

            futures.add(future);
        }

        // 3. Graceful aggregate wait (3500ms total timeout) - slow or failing providers will not block
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            allOf.get(3500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            logger.warn("External search provider aggregation timed out after 3.5s; collecting partial responses.");
        } catch (Exception e) {
            logger.warn("Aggregation interrupted: {}", e.getMessage());
        }

        // 4. Combine results with URL deduplication and multi-engine corroboration
        Map<String, SearchResultItemDto> deduplicatedMap = new LinkedHashMap<>();

        for (CompletableFuture<ProviderSearchResult> f : futures) {
            if (f.isDone() && !f.isCompletedExceptionally()) {
                try {
                    ProviderSearchResult result = f.getNow(null);
                    if (result != null && result.items != null) {
                        for (SearchResultItemDto item : result.items) {
                            if (item.getUrl() == null || item.getUrl().isBlank()) continue;

                            String normalizedKey = normalizeUrlKey(item.getUrl());

                            if (deduplicatedMap.containsKey(normalizedKey)) {
                                // Multi-engine confirmation: URL found by multiple search providers!
                                SearchResultItemDto existing = deduplicatedMap.get(normalizedKey);
                                existing.addVerifiedProvider(result.displayName);

                                // Use better snippet or richer attributes if new item has them
                                if ((existing.getSnippet() == null || existing.getSnippet().length() < 50)
                                        && item.getSnippet() != null && item.getSnippet().length() >= 50) {
                                    existing.setSnippet(item.getSnippet());
                                }
                                if ((existing.getImageUrl() == null || existing.getImageUrl().isBlank())
                                        && item.getImageUrl() != null && !item.getImageUrl().isBlank()) {
                                    existing.setImageUrl(item.getImageUrl());
                                }

                                // Update composite multi-provider badge
                                updateMultiSourceBadge(existing);
                            } else {
                                item.addVerifiedProvider(result.displayName);
                                deduplicatedMap.put(normalizedKey, item);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        aggregated.addAll(deduplicatedMap.values());
        return aggregated;
    }

    private void updateMultiSourceBadge(SearchResultItemDto item) {
        List<String> providers = item.getVerifiedProviders();
        if (providers != null && providers.size() > 1) {
            item.setSourceBadge(String.join(" + ", providers) + " Verified");
            item.setSourceBadgeClass("badge bg-primary-subtle text-primary border border-primary-subtle");
        }
    }

    public static String normalizeUrlKey(String url) {
        if (url == null) return "";
        try {
            String clean = url.trim().toLowerCase();

            // 1. Remove fragment
            int hashIdx = clean.indexOf('#');
            if (hashIdx != -1) {
                clean = clean.substring(0, hashIdx);
            }

            // 2. Strip standard tracking query params (utm_*, ref, fbclid, gclid)
            int queryIdx = clean.indexOf('?');
            if (queryIdx != -1) {
                String base = clean.substring(0, queryIdx);
                String queryString = clean.substring(queryIdx + 1);
                String[] params = queryString.split("&");
                List<String> cleanParams = new ArrayList<>();
                for (String p : params) {
                    String paramName = p.split("=")[0].toLowerCase();
                    if (!paramName.startsWith("utm_") &&
                            !paramName.equals("ref") &&
                            !paramName.equals("fbclid") &&
                            !paramName.equals("gclid") &&
                            !paramName.equals("_ga") &&
                            !paramName.equals("source")) {
                        cleanParams.add(p);
                    }
                }
                if (!cleanParams.isEmpty()) {
                    clean = base + "?" + String.join("&", cleanParams);
                } else {
                    clean = base;
                }
            }

            // 3. Strip scheme & www for uniform matching (http/https equivalence)
            clean = clean.replaceFirst("^https?://", "");
            clean = clean.replaceFirst("^www\\.", "");

            // 4. Strip trailing slash
            if (clean.endsWith("/")) {
                clean = clean.substring(0, clean.length() - 1);
            }

            return clean;
        } catch (Exception e) {
            return url.toLowerCase().trim();
        }
    }

    public List<SearchProvider> getAllProviders() {
        return providers.stream()
                .sorted(Comparator.comparingInt(SearchProvider::getPriority))
                .toList();
    }

    private void recordUsage(String providerId, String query, int status, int count, long latency, String error) {
        try {
            ApiUsageLog log = new ApiUsageLog(providerId, query, status, count, latency, error);
            apiUsageLogRepository.save(log);

            providerConfigRepository.findByProviderId(providerId).ifPresent(cfg -> {
                cfg.recordCall(status == 200, error);
                providerConfigRepository.save(cfg);
            });
        } catch (Exception e) {
            logger.debug("Failed to record API usage log: {}", e.getMessage());
        }
    }

    private static class ProviderSearchResult {
        final String providerId;
        final String displayName;
        final List<SearchResultItemDto> items;
        final boolean success;
        final String error;

        ProviderSearchResult(String providerId, String displayName, List<SearchResultItemDto> items, boolean success, String error) {
            this.providerId = providerId;
            this.displayName = displayName;
            this.items = items;
            this.success = success;
            this.error = error;
        }
    }
}
