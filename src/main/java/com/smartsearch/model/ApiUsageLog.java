package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "api_usage_logs", indexes = {
    @Index(name = "idx_aul_provider", columnList = "providerId"),
    @Index(name = "idx_aul_timestamp", columnList = "timestamp")
})
public class ApiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String providerId; // "GOOGLE", "BING", "BRAVE", "WIKIPEDIA"

    @Column(nullable = false, length = 500)
    private String query;

    private Integer responseStatus = 200;

    private Integer resultsFound = 0;

    private Long latencyMs = 0L;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public ApiUsageLog() {
    }

    public ApiUsageLog(String providerId, String query, Integer responseStatus, Integer resultsFound, Long latencyMs, String errorMessage) {
        this.providerId = providerId;
        this.query = query;
        this.responseStatus = responseStatus != null ? responseStatus : 200;
        this.resultsFound = resultsFound != null ? resultsFound : 0;
        this.latencyMs = latencyMs != null ? latencyMs : 0L;
        this.errorMessage = errorMessage;
        this.timestamp = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public Integer getResultsFound() {
        return resultsFound;
    }

    public void setResultsFound(Integer resultsFound) {
        this.resultsFound = resultsFound;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}

