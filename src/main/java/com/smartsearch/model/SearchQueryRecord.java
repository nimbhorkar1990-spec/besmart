package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "search_queries", indexes = {
    @Index(name = "idx_sq_query", columnList = "query"),
    @Index(name = "idx_sq_searched_at", columnList = "searchedAt")
})
public class SearchQueryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String query;

    @Column(length = 50)
    private String category = "ALL";

    @Column(nullable = false)
    private Long resultsCount = 0L;

    @Column(nullable = false)
    private Long executionTimeMs = 0L;

    @Column(length = 50)
    private String primarySource = "HYBRID";

    @Column(nullable = false)
    private Boolean externalTriggered = false;

    @Column(length = 64)
    private String clientIp;

    @Column(nullable = false)
    private LocalDateTime searchedAt;

    public SearchQueryRecord() {
    }

    public SearchQueryRecord(String query, String category, Long resultsCount, Long executionTimeMs, String primarySource, Boolean externalTriggered, String clientIp) {
        this.query = query;
        this.category = category != null ? category : "ALL";
        this.resultsCount = resultsCount != null ? resultsCount : 0L;
        this.executionTimeMs = executionTimeMs != null ? executionTimeMs : 0L;
        this.primarySource = primarySource != null ? primarySource : "HYBRID";
        this.externalTriggered = externalTriggered != null ? externalTriggered : false;
        this.clientIp = clientIp;
        this.searchedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.searchedAt == null) {
            this.searchedAt = LocalDateTime.now();
        }
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getResultsCount() {
        return resultsCount;
    }

    public void setResultsCount(Long resultsCount) {
        this.resultsCount = resultsCount;
    }

    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public String getPrimarySource() {
        return primarySource;
    }

    public void setPrimarySource(String primarySource) {
        this.primarySource = primarySource;
    }

    public Boolean getExternalTriggered() {
        return externalTriggered;
    }

    public void setExternalTriggered(Boolean externalTriggered) {
        this.externalTriggered = externalTriggered;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public LocalDateTime getSearchedAt() {
        return searchedAt;
    }

    public void setSearchedAt(LocalDateTime searchedAt) {
        this.searchedAt = searchedAt;
    }
}

