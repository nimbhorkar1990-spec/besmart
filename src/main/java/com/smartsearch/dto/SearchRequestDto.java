package com.smartsearch.dto;

public class SearchRequestDto {
    private String query = "";
    private String category = "ALL";
    private int page = 1;
    private int size = 10;
    private String exactMatch = "";
    private String site = "";
    private String dateFilter = "ALL"; // ALL, DAY, WEEK, MONTH, YEAR
    private String contentType = "ALL";
    private String sortBy = "RELEVANCE"; // RELEVANCE, DATE
    private String safeSearch = "MODERATE"; // OFF, MODERATE, STRICT

    public SearchRequestDto() {
    }

    public SearchRequestDto(String query, String category, int page) {
        this.query = query;
        this.category = (category != null) ? category : "ALL";
        this.page = Math.max(page, 1);
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getQ() {
        return query;
    }

    public void setQ(String q) {
        this.query = q;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(page, 1);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = (size > 0 && size <= 50) ? size : 10;
    }

    public String getExactMatch() {
        return exactMatch;
    }

    public void setExactMatch(String exactMatch) {
        this.exactMatch = exactMatch;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getDateFilter() {
        return dateFilter;
    }

    public void setDateFilter(String dateFilter) {
        this.dateFilter = dateFilter;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSafeSearch() {
        return safeSearch;
    }

    public void setSafeSearch(String safeSearch) {
        this.safeSearch = safeSearch;
    }
}

