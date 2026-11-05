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
    private String providerId; // "GOOGLE", "BING", "YAHOO", "DUCKDUCKGO", "BRAVE", "WIKIPEDIA", "MOJEEK", "YANDEX", etc.

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(length = 500)
    private String websiteUrl;

    @Column(length = 500)
    private String endpointUrl;

    @Column(length = 500)
    private String docUrl;

    @Column(length = 255)
    private String apiKey;

    @Column(length = 255)
    private String searchEngineId; // e.g. for Google cx

    @Column(length = 50)
    private String authMethod = "API_KEY_QUERY"; // "API_KEY_QUERY", "API_KEY_HEADER", "BEARER_TOKEN", "OPEN_INTERFACE", "NONE"

    @Column(length = 100)
    private String authHeaderName; // e.g. "Authorization", "X-Subscription-Token", "Ocp-Apim-Subscription-Key"

    @Column(length = 255)
    private String supportedRegions = "Worldwide";

    @Column(length = 255)
    private String supportedLanguages = "100+ Languages";

    @Column(nullable = false)
    private Integer priority = 1; // 1 = Highest

    @Column(nullable = false)
    private Integer resultLimit = 10;

    @Column(nullable = false)
    private Integer timeoutMs = 3000;

    @Column(nullable = false)
    private Boolean isCustom = false;

    @Column(nullable = false)
    private Boolean isFallback = false;

    @Column(length = 50)
    private String pricingTier = "FREEMIUM"; // "FREE", "FREEMIUM", "PAID"

    @Column(length = 50)
    private String integrationStatus = "INTEGRATED"; // "INTEGRATED", "PENDING_SETUP", "DEPRECATED"

    @Column(length = 50)
    private String connectionStatus = "UNTESTED"; // "CONNECTED", "DISCONNECTED", "UNTESTED", "ERROR"

    @Column(length = 50)
    private String apiStatus = "READY"; // "READY", "ACTIVE", "KEY_REQUIRED", "RATE_LIMITED", "ERROR"

    @Column(nullable = false)
    private Long totalCalls = 0L;

    @Column(nullable = false)
    private Long successCalls = 0L;

    @Column(nullable = false)
    private Long failureCalls = 0L;

    @Column(nullable = false)
    private Long resultsContributed = 0L;

    private LocalDateTime lastCalledAt;

    @Column(length = 500)
    private String lastError;

    private LocalDateTime lastPingAt;

    private Long lastPingLatencyMs;

    @Column(length = 500)
    private String lastPingMessage;

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
            this.connectionStatus = "CONNECTED";
            this.apiStatus = "ACTIVE";
        } else {
            this.failureCalls++;
            this.lastError = error;
            this.connectionStatus = "ERROR";
            this.apiStatus = "ERROR";
        }
        this.lastCalledAt = LocalDateTime.now();
    }

    public void recordContribution(int count) {
        if (count > 0) {
            this.resultsContributed += count;
        }
    }

    public void recordPing(boolean success, long latencyMs, String message) {
        this.lastPingAt = LocalDateTime.now();
        this.lastPingLatencyMs = latencyMs;
        this.lastPingMessage = message;
        if (success) {
            this.connectionStatus = "CONNECTED";
            this.apiStatus = "READY";
            this.lastError = null;
        } else {
            this.connectionStatus = "DISCONNECTED";
            this.apiStatus = "ERROR";
            this.lastError = message;
        }
    }

    public boolean isConfiguredProperly() {
        if ("WIKIPEDIA".equalsIgnoreCase(providerId) || "DUCKDUCKGO".equalsIgnoreCase(providerId) || "YAHOO".equalsIgnoreCase(providerId)) {
            return true;
        }
        return apiKey != null && !apiKey.isBlank();
    }

    // --- Getters and Setters ---

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

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getDocUrl() {
        return docUrl;
    }

    public void setDocUrl(String docUrl) {
        this.docUrl = docUrl;
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

    public String getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public void setAuthHeaderName(String authHeaderName) {
        this.authHeaderName = authHeaderName;
    }

    public String getSupportedRegions() {
        return supportedRegions;
    }

    public void setSupportedRegions(String supportedRegions) {
        this.supportedRegions = supportedRegions;
    }

    public String getSupportedLanguages() {
        return supportedLanguages;
    }

    public void setSupportedLanguages(String supportedLanguages) {
        this.supportedLanguages = supportedLanguages;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getResultLimit() {
        return resultLimit;
    }

    public void setResultLimit(Integer resultLimit) {
        this.resultLimit = resultLimit;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Boolean getIsCustom() {
        return isCustom;
    }

    public void setIsCustom(Boolean isCustom) {
        this.isCustom = isCustom;
    }

    public Boolean getIsFallback() {
        return isFallback;
    }

    public void setIsFallback(Boolean isFallback) {
        this.isFallback = isFallback;
    }

    public String getPricingTier() {
        return pricingTier;
    }

    public void setPricingTier(String pricingTier) {
        this.pricingTier = pricingTier;
    }

    public String getIntegrationStatus() {
        return integrationStatus;
    }

    public void setIntegrationStatus(String integrationStatus) {
        this.integrationStatus = integrationStatus;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    public String getApiStatus() {
        return apiStatus;
    }

    public void setApiStatus(String apiStatus) {
        this.apiStatus = apiStatus;
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

    public Long getResultsContributed() {
        return resultsContributed;
    }

    public void setResultsContributed(Long resultsContributed) {
        this.resultsContributed = resultsContributed;
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

    public LocalDateTime getLastPingAt() {
        return lastPingAt;
    }

    public void setLastPingAt(LocalDateTime lastPingAt) {
        this.lastPingAt = lastPingAt;
    }

    public Long getLastPingLatencyMs() {
        return lastPingLatencyMs;
    }

    public void setLastPingLatencyMs(Long lastPingLatencyMs) {
        this.lastPingLatencyMs = lastPingLatencyMs;
    }

    public String getLastPingMessage() {
        return lastPingMessage;
    }

    public void setLastPingMessage(String lastPingMessage) {
        this.lastPingMessage = lastPingMessage;
    }
}
