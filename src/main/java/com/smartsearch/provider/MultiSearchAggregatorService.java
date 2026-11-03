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
        this.executor = Executors.newFixedThreadPool(Math.max(4, providers.size()));
    }

    public List<SearchResultItemDto> aggregateExternalSearch(SearchRequestDto request) {
        List<SearchResultItemDto> aggregated = new ArrayList<>();
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return aggregated;
        }

        List<SearchProvider> activeProviders = providers.stream()
                .filter(p -> p.isEnabled() && p.isConfigured())
                .sorted(Comparator.comparingInt(SearchProvider::getPriority))
                .toList();

        if (activeProviders.isEmpty()) {
            logger.info("No external search providers currently active/configured.");
            return aggregated;
        }

        List<CompletableFuture<List<SearchResultItemDto>>> futures = new ArrayList<>();

        for (SearchProvider provider : activeProviders) {
            CompletableFuture<List<SearchResultItemDto>> future = CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                try {
                    List<SearchResultItemDto> items = provider.search(request);
                    long duration = System.currentTimeMillis() - start;
                    recordUsage(provider.getProviderId(), request.getQuery(), 200, items.size(), duration, null);
                    return items;
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - start;
                    logger.error("Provider {} failed: {}", provider.getProviderId(), e.getMessage());
                    recordUsage(provider.getProviderId(), request.getQuery(), 500, 0, duration, e.getMessage());
                    return Collections.emptyList();
                }
            }, executor);

            futures.add(future);
        }

        // Wait for all with a graceful total timeout (3500ms max)
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            allOf.get(3500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            logger.warn("External search provider aggregation timed out after 3.5s; collecting partial responses.");
        } catch (Exception e) {
            logger.warn("Aggregation interrupted: {}", e.getMessage());
        }

        Set<String> seenUrls = new HashSet<>();
        for (CompletableFuture<List<SearchResultItemDto>> f : futures) {
            if (f.isDone() && !f.isCompletedExceptionally()) {
                try {
                    List<SearchResultItemDto> items = f.getNow(Collections.emptyList());
                    for (SearchResultItemDto item : items) {
                        if (item.getUrl() != null && seenUrls.add(item.getUrl().toLowerCase().trim())) {
                            aggregated.add(item);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return aggregated;
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
}

