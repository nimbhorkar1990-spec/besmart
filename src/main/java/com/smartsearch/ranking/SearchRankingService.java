package com.smartsearch.ranking;

import com.smartsearch.dto.SearchResultItemDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class SearchRankingService {

    /**
     * Merges Lucene local results and External Search provider results with intelligent multi-factor ranking.
     */
    public List<SearchResultItemDto> rankAndMerge(String query, List<SearchResultItemDto> localResults, List<SearchResultItemDto> externalResults) {
        String cleanQuery = (query != null) ? query.toLowerCase().trim() : "";
        String[] queryTerms = cleanQuery.split("\\s+");

        Map<String, SearchResultItemDto> mergedMap = new LinkedHashMap<>();

        // 1. Process Local Lucene Results
        if (localResults != null) {
            for (SearchResultItemDto item : localResults) {
                if (item.getUrl() == null || item.getUrl().isBlank()) continue;
                String normalizedUrl = normalizeUrlKey(item.getUrl());
                float score = calculateItemScore(item, cleanQuery, queryTerms, 40.0f); // Higher baseline for verified local index
                item.setScore(score);
                if (item.getSource() == null || item.getSource().isBlank()) {
                    item.setSource("LUCENE");
                }
                item.setSourceBadge("SmartSearch Index");
                item.setSourceBadgeClass("bg-success-subtle text-success border border-success-subtle");
                mergedMap.put(normalizedUrl, item);
            }
        }

        // 2. Process External Results (add or boost if also discovered externally)
        if (externalResults != null) {
            for (SearchResultItemDto item : externalResults) {
                if (item.getUrl() == null || item.getUrl().isBlank()) continue;
                String normalizedUrl = normalizeUrlKey(item.getUrl());

                if (mergedMap.containsKey(normalizedUrl)) {
                    // Already indexed locally: give cross-source confirmation bonus!
                    SearchResultItemDto existing = mergedMap.get(normalizedUrl);
                    existing.setScore(existing.getScore() + 20.0f);
                    existing.setSourceBadge("SmartSearch Index (Verified Live)");
                } else {
                    float score = calculateItemScore(item, cleanQuery, queryTerms, 25.0f);
                    item.setScore(score);
                    mergedMap.put(normalizedUrl, item);
                }
            }
        }

        // 3. Sort by composite score descending
        List<SearchResultItemDto> sortedList = new ArrayList<>(mergedMap.values());
        sortedList.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));

        return sortedList;
    }

    private float calculateItemScore(SearchResultItemDto item, String cleanQuery, String[] queryTerms, float baseScore) {
        float score = baseScore;

        String title = (item.getTitle() != null) ? item.getTitle().toLowerCase() : "";
        String snippet = (item.getSnippet() != null) ? item.getSnippet().toLowerCase() : "";
        String domain = (item.getDomain() != null) ? item.getDomain().toLowerCase() : "";

        // Title matching bonus
        if (!cleanQuery.isBlank()) {
            if (title.contains(cleanQuery)) {
                score += 35.0f; // Exact full phrase match in title
            } else {
                for (String term : queryTerms) {
                    if (term.length() > 2 && title.contains(term)) {
                        score += 8.0f;
                    }
                }
            }
        }

        // Snippet matching
        if (!cleanQuery.isBlank()) {
            if (snippet.contains(cleanQuery)) {
                score += 15.0f;
            } else {
                for (String term : queryTerms) {
                    if (term.length() > 2 && snippet.contains(term)) {
                        score += 3.0f;
                    }
                }
            }
        }

        // Domain matching bonus (e.g. query "spring" and domain is "spring.io")
        if (!cleanQuery.isBlank() && domain.contains(cleanQuery.replaceAll("\\s+", ""))) {
            score += 20.0f;
        }

        // Image bonus (rich visual preview)
        if (item.getImageUrl() != null && !item.getImageUrl().isBlank()) {
            score += 5.0f;
        }

        // Existing score if set by Lucene BM25
        if (item.getScore() > 0) {
            score += Math.min(item.getScore(), 25.0f);
        }

        return score;
    }

    private String normalizeUrlKey(String url) {
        try {
            String clean = url.trim().toLowerCase();
            int hashIdx = clean.indexOf("#");
            if (hashIdx != -1) {
                clean = clean.substring(0, hashIdx);
            }
            if (clean.endsWith("/")) {
                clean = clean.substring(0, clean.length() - 1);
            }
            return clean;
        } catch (Exception e) {
            return url.toLowerCase().trim();
        }
    }
}

