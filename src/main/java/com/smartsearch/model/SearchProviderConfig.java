package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "search_providers")
public class SearchProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String providerId; // "GOOGLE", "BING", "BRAVE", "WIKIPEDIA"

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(length = 255)
    private String apiKey;

    @Column(length = 255)
    private String searchEngineId; // e.g. for Google cx

    @Column(length = 500)
    private String endpointUrl;

    @Column(nullable = false)
    private Integer priority = 1; // 1 = Highest

    @Column(nullable = false)
    private Long totalCalls = 0L;

    @Column(nullable = false)
    private Long successCalls = 0L;

    @Column(nullable = false)
    private Long failureCalls = 0L;

    private LocalDateTime lastCalledAt;

    @Column(length = 500)
    private String lastError;

    public SearchProviderConfig() {
    }

    public SearchProviderConfig(String providerId, String displayName, Boolean enabled, String apiKey, String searchEngineId, String endpointUrl, Integer priority) {
        this.providerId = providerId;
        this.displayName = displayName;
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.searchEngineId = searchEngineId;
        this.endpointUrl = endpointUrl;
        this.priority = priority != null ? priority : 1;
    }

    public void recordCall(boolean success, String error) {
        this.totalCalls++;
        if (success) {
            this.successCalls++;
            this.lastError = null;
        } else {
            this.failureCalls++;
            this.lastError = error;
        }
        this.lastCalledAt = LocalDateTime.now();
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSearchEngineId() {
        return searchEngineId;
    }

    public void setSearchEngineId(String searchEngineId) {
        this.searchEngineId = searchEngineId;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Long getTotalCalls() {
        return totalCalls;
    }

    public void setTotalCalls(Long totalCalls) {
        this.totalCalls = totalCalls;
    }

    public Long getSuccessCalls() {
        return successCalls;
    }

    public void setSuccessCalls(Long successCalls) {
        this.successCalls = successCalls;
    }

    public Long getFailureCalls() {
        return failureCalls;
    }

    public void setFailureCalls(Long failureCalls) {
        this.failureCalls = failureCalls;
    }

    public LocalDateTime getLastCalledAt() {
        return lastCalledAt;
    }

    public void setLastCalledAt(LocalDateTime lastCalledAt) {
        this.lastCalledAt = lastCalledAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}

