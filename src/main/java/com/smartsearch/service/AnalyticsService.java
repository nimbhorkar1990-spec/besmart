package com.smartsearch.service;

import com.smartsearch.model.SearchAnalytics;
import com.smartsearch.model.SearchHistory;
import com.smartsearch.model.User;
import com.smartsearch.repository.SearchAnalyticsRepository;
import com.smartsearch.repository.SearchHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AnalyticsService {

    private final SearchAnalyticsRepository analyticsRepository;
    private final SearchHistoryRepository historyRepository;

    public AnalyticsService(SearchAnalyticsRepository analyticsRepository, SearchHistoryRepository historyRepository) {
        this.analyticsRepository = analyticsRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public void trackSearch(String query, String category, User user, String ipAddress) {
        if (query == null || query.trim().length() < 2) {
            return;
        }
        String cleanQuery = query.trim().toLowerCase();

        // 1. Update Aggregate Analytics
        Optional<SearchAnalytics> existingOpt = analyticsRepository.findByQueryIgnoreCase(cleanQuery);
        if (existingOpt.isPresent()) {
            SearchAnalytics existing = existingOpt.get();
            existing.incrementCount();
            if (category != null && !category.equalsIgnoreCase("ALL")) {
                existing.setTopCategory(category);
            }
            analyticsRepository.save(existing);
        } else {
            SearchAnalytics newRecord = new SearchAnalytics(cleanQuery, category);
            analyticsRepository.save(newRecord);
        }

        // 2. Record User History if authenticated
        if (user != null) {
            SearchHistory history = new SearchHistory(user, query.trim(), category, ipAddress);
            historyRepository.save(history);
        }
    }

    public List<String> getSuggestions(String prefix, int limit) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return List.of();
        }
        return analyticsRepository.findSuggestionsByPrefix(prefix.trim().toLowerCase(), PageRequest.of(0, limit));
    }

    public List<SearchAnalytics> getTopQueries(int limit) {
        return analyticsRepository.findTop10ByOrderBySearchCountDesc();
    }

    public Page<SearchAnalytics> getPopularQueriesPaged(Pageable pageable) {
        return analyticsRepository.findAllByOrderBySearchCountDesc(pageable);
    }

    public Page<SearchAnalytics> getPopularQueriesPaged(String search, Pageable pageable) {
        if (search != null && !search.trim().isEmpty()) {
            return analyticsRepository.findByQueryContainingIgnoreCaseOrderBySearchCountDesc(search.trim(), pageable);
        }
        return analyticsRepository.findAllByOrderBySearchCountDesc(pageable);
    }

    @Transactional
    public Optional<String> deleteQueryAnalytics(Long id) {
        Optional<SearchAnalytics> recordOpt = analyticsRepository.findById(id);
        if (recordOpt.isPresent()) {
            String query = recordOpt.get().getQuery();
            analyticsRepository.deleteById(id);
            return Optional.of(query);
        }
        return Optional.empty();
    }

    @Transactional
    public void clearAllAnalytics() {
        analyticsRepository.deleteAll();
    }

    public Map<String, Object> getAnalyticsSummary() {
        Map<String, Object> summary = new HashMap<>();
        Long totalSearches = analyticsRepository.sumTotalSearches();
        long uniqueQueries = analyticsRepository.count();
        long searchesPast24h = historyRepository.countBySearchedAtAfter(LocalDateTime.now().minusHours(24));
        long searchesPast7d = historyRepository.countBySearchedAtAfter(LocalDateTime.now().minusDays(7));

        summary.put("totalSearches", totalSearches != null ? totalSearches : 0L);
        summary.put("uniqueQueries", uniqueQueries);
        summary.put("searchesPast24h", searchesPast24h);
        summary.put("searchesPast7d", searchesPast7d);

        return summary;
    }
}

