package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "search_results", indexes = {
    @Index(name = "idx_sr_query", columnList = "queryHash"),
    @Index(name = "idx_sr_created", columnList = "createdAt")
})
public class SearchResultCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String queryHash; // Normalized query hash

    @Column(nullable = false, length = 500)
    private String rawQuery;

    @Column(length = 50)
    private String category = "ALL";

    @Lob
    @Column(columnDefinition = "TEXT")
    private String resultsJson; // Cached JSON of SearchResultItemDto list

    private Long totalHits = 0L;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public SearchResultCache() {
    }

    public SearchResultCache(String queryHash, String rawQuery, String category, String resultsJson, Long totalHits, int ttlMinutes) {
        this.queryHash = queryHash;
        this.rawQuery = rawQuery;
        this.category = category != null ? category : "ALL";
        this.resultsJson = resultsJson;
        this.totalHits = totalHits != null ? totalHits : 0L;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusMinutes(ttlMinutes > 0 ? ttlMinutes : 60);
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQueryHash() {
        return queryHash;
    }

    public void setQueryHash(String queryHash) {
        this.queryHash = queryHash;
    }

    public String getRawQuery() {
        return rawQuery;
    }

    public void setRawQuery(String rawQuery) {
        this.rawQuery = rawQuery;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getResultsJson() {
        return resultsJson;
    }

    public void setResultsJson(String resultsJson) {
        this.resultsJson = resultsJson;
    }

    public Long getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(Long totalHits) {
        this.totalHits = totalHits;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}

