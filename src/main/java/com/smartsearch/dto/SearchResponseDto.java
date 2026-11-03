package com.smartsearch.dto;

import java.util.ArrayList;
import java.util.List;

public class SearchResponseDto {
    private String query;
    private String category;
    private long totalHits;
    private int page;
    private int totalPages;
    private int pageSize;
    private double searchDurationMs;
    private List<SearchResultItemDto> results = new ArrayList<>();
    private List<String> relatedSearches = new ArrayList<>();
    private String correction; // "Did you mean: ..."
    private boolean hasNext;
    private boolean hasPrevious;
    private KnowledgeCardDto knowledgeCard;
    private String discoveryStatus;
    private int externalDiscoveredCount;
    private boolean crawlerActive;

    public SearchResponseDto() {
    }

    public SearchResponseDto(String query, String category, long totalHits, int page, int pageSize) {
        this.query = query;
        this.category = category;
        this.totalHits = totalHits;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = (int) Math.ceil((double) totalHits / pageSize);
        this.hasNext = page < totalPages;
        this.hasPrevious = page > 1;
    }

    public static class KnowledgeCardDto {
        private String title;
        private String subtitle;
        private String description;
        private String sourceUrl;
        private String imageUrl;

        public KnowledgeCardDto() {
        }

        public KnowledgeCardDto(String title, String subtitle, String description, String sourceUrl, String imageUrl) {
            this.title = title;
            this.subtitle = subtitle;
            this.description = description;
            this.sourceUrl = sourceUrl;
            this.imageUrl = imageUrl;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public void setSubtitle(String subtitle) {
            this.subtitle = subtitle;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSourceUrl() {
            return sourceUrl;
        }

        public void setSourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public long getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(long totalHits) {
        this.totalHits = totalHits;
        this.totalPages = (pageSize > 0) ? (int) Math.ceil((double) totalHits / pageSize) : 0;
        this.hasNext = page < totalPages;
        this.hasPrevious = page > 1;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public double getSearchDurationMs() {
        return searchDurationMs;
    }

    public void setSearchDurationMs(double searchDurationMs) {
        this.searchDurationMs = searchDurationMs;
    }

    public List<SearchResultItemDto> getResults() {
        return results;
    }

    public void setResults(List<SearchResultItemDto> results) {
        this.results = results;
    }

    public List<String> getRelatedSearches() {
        return relatedSearches;
    }

    public void setRelatedSearches(List<String> relatedSearches) {
        this.relatedSearches = relatedSearches;
    }

    public String getCorrection() {
        return correction;
    }

    public void setCorrection(String correction) {
        this.correction = correction;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }

    public boolean isHasPrevious() {
        return hasPrevious;
    }

    public void setHasPrevious(boolean hasPrevious) {
        this.hasPrevious = hasPrevious;
    }

    public KnowledgeCardDto getKnowledgeCard() {
        return knowledgeCard;
    }

    public void setKnowledgeCard(KnowledgeCardDto knowledgeCard) {
        this.knowledgeCard = knowledgeCard;
    }

    public String getDiscoveryStatus() {
        return discoveryStatus;
    }

    public void setDiscoveryStatus(String discoveryStatus) {
        this.discoveryStatus = discoveryStatus;
    }

    public int getExternalDiscoveredCount() {
        return externalDiscoveredCount;
    }

    public void setExternalDiscoveredCount(int externalDiscoveredCount) {
        this.externalDiscoveredCount = externalDiscoveredCount;
    }

    public boolean isCrawlerActive() {
        return crawlerActive;
    }

    public void setCrawlerActive(boolean crawlerActive) {
        this.crawlerActive = crawlerActive;
    }
}

