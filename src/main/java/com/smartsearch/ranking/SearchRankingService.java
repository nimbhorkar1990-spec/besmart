package com.smartsearch.ranking;

import com.smartsearch.dto.SearchResultItemDto;
import com.smartsearch.provider.MultiSearchAggregatorService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SearchRankingService {

    /**
     * Merges Lucene local results and multi-source external search results
     * with intelligent multi-factor ranking (relevance, quality, freshness, reliability, domain diversity).
     */
    public List<SearchResultItemDto> rankAndMerge(String query, List<SearchResultItemDto> localResults, List<SearchResultItemDto> externalResults) {
        String cleanQuery = (query != null) ? query.toLowerCase().trim() : "";
        String[] queryTerms = cleanQuery.split("\\s+");

        Map<String, SearchResultItemDto> mergedMap = new LinkedHashMap<>();

        // 1. Process Local Lucene Results
        if (localResults != null) {
            for (SearchResultItemDto item : localResults) {
                if (item.getUrl() == null || item.getUrl().isBlank()) continue;
                String normalizedUrl = MultiSearchAggregatorService.normalizeUrlKey(item.getUrl());
                float score = calculateItemScore(item, cleanQuery, queryTerms, 35.0f);
                item.setScore(score);
                if (item.getSource() == null || item.getSource().isBlank()) {
                    item.setSource("LUCENE");
                }
                item.setSourceBadge("SmartSearch Index");
                item.setSourceBadgeClass("bg-success-subtle text-success border border-success-subtle");
                item.addVerifiedProvider("SmartSearch Index");
                mergedMap.put(normalizedUrl, item);
            }
        }

        // 2. Process External Multi-Engine Results
        if (externalResults != null) {
            for (SearchResultItemDto item : externalResults) {
                if (item.getUrl() == null || item.getUrl().isBlank()) continue;
                String normalizedUrl = MultiSearchAggregatorService.normalizeUrlKey(item.getUrl());

                if (mergedMap.containsKey(normalizedUrl)) {
                    // Local index + External engine cross-confirmation bonus!
                    SearchResultItemDto existing = mergedMap.get(normalizedUrl);
                    existing.setScore(existing.getScore() + 25.0f);
                    for (String provider : item.getVerifiedProviders()) {
                        existing.addVerifiedProvider(provider);
                    }
                    existing.setSourceBadge("SmartSearch Index (Live Verified: " + String.join(", ", item.getVerifiedProviders()) + ")");
                    existing.setSourceBadgeClass("bg-primary-subtle text-primary border border-primary-subtle");

                    if ((existing.getSnippet() == null || existing.getSnippet().length() < 50) && item.getSnippet() != null) {
                        existing.setSnippet(item.getSnippet());
                    }
                    if ((existing.getImageUrl() == null || existing.getImageUrl().isBlank()) && item.getImageUrl() != null) {
                        existing.setImageUrl(item.getImageUrl());
                    }
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

        // 4. Apply Domain Diversity Balancing (avoid same website dominating top results)
        return applyDomainDiversity(sortedList, 2);
    }

    private float calculateItemScore(SearchResultItemDto item, String cleanQuery, String[] queryTerms, float baseScore) {
        float score = baseScore;

        String title = (item.getTitle() != null) ? item.getTitle().toLowerCase() : "";
        String snippet = (item.getSnippet() != null) ? item.getSnippet().toLowerCase() : "";
        String domain = (item.getDomain() != null) ? item.getDomain().toLowerCase() : "";
        String url = (item.getUrl() != null) ? item.getUrl().toLowerCase() : "";

        // --- A. Query Relevance ---
        if (!cleanQuery.isBlank()) {
            // Title matching
            if (title.contains(cleanQuery)) {
                score += 35.0f; // Exact phrase in title
            } else {
                int matchedTerms = 0;
                for (String term : queryTerms) {
                    if (term.length() > 2 && title.contains(term)) {
                        score += 7.0f;
                        matchedTerms++;
                    }
                }
                if (matchedTerms == queryTerms.length && queryTerms.length > 1) {
                    score += 15.0f; // All query terms present in title
                }
            }

            // Snippet matching
            if (snippet.contains(cleanQuery)) {
                score += 15.0f; // Exact phrase in snippet
            } else {
                for (String term : queryTerms) {
                    if (term.length() > 2 && snippet.contains(term)) {
                        score += 3.0f;
                    }
                }
            }

            // Domain exact match bonus (e.g. query "spring boot" and domain is "spring.io")
            String cleanQueryNoSpace = cleanQuery.replaceAll("\\s+", "");
            if (domain.contains(cleanQueryNoSpace)) {
                score += 20.0f;
            }

            // URL slug match
            if (url.contains(cleanQueryNoSpace)) {
                score += 8.0f;
            }
        }

        // --- B. Content Quality & Presentation ---
        // Rich snippet length & readability
        if (snippet.length() > 80 && !snippet.contains("no snippet available")) {
            score += 8.0f;
        }

        // Rich visual media thumbnail
        if (item.getImageUrl() != null && !item.getImageUrl().isBlank()) {
            score += 5.0f;
        }

        // Penalize dead/error titles
        if (title.contains("404") || title.contains("not found") || title.contains("access denied") || title.contains("error 403")) {
            score -= 50.0f;
        }

        // --- C. Multi-Engine Consensus & Reliability ---
        List<String> verifiedProviders = item.getVerifiedProviders();
        if (verifiedProviders != null && verifiedProviders.size() > 1) {
            // Substantial confirmation bonus for each additional search engine that corroborated this URL
            score += (verifiedProviders.size() - 1) * 20.0f;
        }

        // Trusted TLD Authority
        if (domain.endsWith(".gov") || domain.contains(".gov.")) {
            score += 12.0f;
        } else if (domain.endsWith(".edu") || domain.contains(".edu.")) {
            score += 10.0f;
        } else if (domain.endsWith(".org")) {
            score += 5.0f;
        }

        // HTTPS security protocol
        if (url.startsWith("https://")) {
            score += 3.0f;
        }

        // --- D. Freshness & Recency ---
        String published = (item.getPublishedDate() != null) ? item.getPublishedDate().toLowerCase() : "";
        if (published.contains("hour") || published.contains("today") || published.contains("yesterday") ||
                published.contains("day ago") || published.contains("days ago") || published.contains("mins ago")) {
            score += 10.0f;
        } else if (published.contains("2026") || title.contains("2026") || snippet.contains("2026")) {
            score += 6.0f;
        } else if (published.contains("2025") || title.contains("2025") || snippet.contains("2025")) {
            score += 4.0f;
        }

        // Existing score if set by Lucene BM25
        if (item.getScore() > 0) {
            score += Math.min(item.getScore(), 25.0f);
        }

        return score;
    }

    /**
     * Ensures that no single domain monopolizes more than maxPerDomain entries in the top 10 results.
     * Surpassing entries from the same domain are gracefully shifted down after the top batch.
     */
    private List<SearchResultItemDto> applyDomainDiversity(List<SearchResultItemDto> items, int maxPerDomain) {
        if (items.size() <= maxPerDomain) {
            return items;
        }

        List<SearchResultItemDto> diverseTopList = new ArrayList<>();
        List<SearchResultItemDto> deferredList = new ArrayList<>();
        Map<String, Integer> domainCounts = new HashMap<>();

        for (SearchResultItemDto item : items) {
            String domain = (item.getDomain() != null) ? item.getDomain().toLowerCase() : "unknown";
            int count = domainCounts.getOrDefault(domain, 0);

            if (count < maxPerDomain || diverseTopList.size() >= 10) {
                domainCounts.put(domain, count + 1);
                diverseTopList.add(item);
            } else {
                deferredList.add(item);
            }
        }

        diverseTopList.addAll(deferredList);
        return diverseTopList;
    }
}
