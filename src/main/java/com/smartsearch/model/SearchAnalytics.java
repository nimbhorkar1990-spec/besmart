package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "search_analytics", indexes = {
    @Index(name = "idx_analytics_query", columnList = "query"),
    @Index(name = "idx_analytics_count", columnList = "searchCount")
})
public class SearchAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String query;

    @Column(nullable = false)
    private Long searchCount = 1L;

    @Column(length = 50)
    private String topCategory = "ALL";

    @Column(nullable = false)
    private LocalDateTime firstSearchedAt;

    @Column(nullable = false)
    private LocalDateTime lastSearchedAt;

    public SearchAnalytics() {
    }

    public SearchAnalytics(String query, String topCategory) {
        this.query = query;
        this.topCategory = topCategory != null ? topCategory : "ALL";
        this.searchCount = 1L;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.firstSearchedAt = now;
        this.lastSearchedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastSearchedAt = LocalDateTime.now();
    }

    public void incrementCount() {
        this.searchCount++;
        this.lastSearchedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Long getSearchCount() {
        return searchCount;
    }

    public void setSearchCount(Long searchCount) {
        this.searchCount = searchCount;
    }

    public String getTopCategory() {
        return topCategory;
    }

    public void setTopCategory(String topCategory) {
        this.topCategory = topCategory;
    }

    public LocalDateTime getFirstSearchedAt() {
        return firstSearchedAt;
    }

    public void setFirstSearchedAt(LocalDateTime firstSearchedAt) {
        this.firstSearchedAt = firstSearchedAt;
    }

    public LocalDateTime getLastSearchedAt() {
        return lastSearchedAt;
    }

    public void setLastSearchedAt(LocalDateTime lastSearchedAt) {
        this.lastSearchedAt = lastSearchedAt;
    }
}

