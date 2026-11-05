package com.smartsearch.service;

import com.smartsearch.model.AdminActivityLog;
import com.smartsearch.model.SearchProviderConfig;
import com.smartsearch.model.SearchProviderRecommendation;
import com.smartsearch.repository.AdminActivityLogRepository;
import com.smartsearch.repository.SearchProviderConfigRepository;
import com.smartsearch.repository.SearchProviderRecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SearchEngineSourcesService {

    private static final Logger logger = LoggerFactory.getLogger(SearchEngineSourcesService.class);

    private final SearchProviderConfigRepository providerConfigRepository;
    private final SearchProviderRecommendationRepository recommendationRepository;
    private final AdminActivityLogRepository activityLogRepository;
    private final SystemSettingService settingService;
    private HttpClient httpClient;

    public SearchEngineSourcesService(SearchProviderConfigRepository providerConfigRepository,
                                      SearchProviderRecommendationRepository recommendationRepository,
                                      AdminActivityLogRepository activityLogRepository,
                                      SystemSettingService settingService) {
        this.providerConfigRepository = providerConfigRepository;
        this.recommendationRepository = recommendationRepository;
        this.activityLogRepository = activityLogRepository;
        this.settingService = settingService;
    }

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(4))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                }
            }
        }
        return httpClient;
    }

    public List<SearchProviderConfig> getAllProviders() {
        return providerConfigRepository.findAllByOrderByPriorityAsc();
    }

    public Optional<SearchProviderConfig> getProviderById(Long id) {
        return providerConfigRepository.findById(id);
    }

    public Optional<SearchProviderConfig> getProviderByProviderId(String providerId) {
        return providerConfigRepository.findByProviderId(providerId);
    }

    // --- Live Connection Testing (Requirement H & B) ---
    @Transactional
    public ConnectionTestResult testProviderConnection(String providerId, String adminUser, String ipAddress) {
        Optional<SearchProviderConfig> opt = providerConfigRepository.findByProviderId(providerId);
        if (opt.isEmpty()) {
            return new ConnectionTestResult(false, 404, 0, "Provider not found in configuration.");
        }

        SearchProviderConfig config = opt.get();
        long startTime = System.currentTimeMillis();
        boolean success = false;
        int statusCode = 0;
        String message;

        try {
            HttpRequest.Builder reqBuilder = buildTestProbeRequest(config);
            if (reqBuilder == null) {
                message = "Provider lacks a valid test probe URL or configuration.";
                config.recordPing(false, 0, message);
                providerConfigRepository.save(config);
                return new ConnectionTestResult(false, 400, 0, message);
            }

            HttpClient client = getHttpClient();
            HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - startTime;
            statusCode = response.statusCode();

            // 200 OK or 400/403 with live response proves endpoint is reachable & connected
            if (statusCode == 200) {
                success = true;
                message = String.format("HTTP 200 OK · Connection verified in %d ms · API ready and responsive.", latency);
                config.setIntegrationStatus("INTEGRATED");
                config.setConnectionStatus("CONNECTED");
                config.setApiStatus("READY");
            } else if (statusCode == 401 || statusCode == 403) {
                // Endpoint online, requires/rejected credentials
                message = String.format("HTTP %d · Endpoint reachable in %d ms, but credentials missing or rejected.", statusCode, latency);
                config.setConnectionStatus("CONNECTED");
                config.setApiStatus("KEY_REQUIRED");
            } else if (statusCode == 429) {
                message = String.format("HTTP 429 Too Many Requests · Rate limit reached in %d ms.", latency);
                config.setConnectionStatus("CONNECTED");
                config.setApiStatus("RATE_LIMITED");
            } else {
                message = String.format("HTTP %d · Provider returned status code in %d ms.", statusCode, latency);
                config.setConnectionStatus("DEGRADED");
                config.setApiStatus("ERROR");
            }

            config.recordPing(success, latency, message);
            providerConfigRepository.save(config);

            logActivity(adminUser != null ? adminUser : "system", "CONNECTION_TESTED", providerId,
                    String.format("Tested connection: %s (Status: %d, Latency: %d ms)", config.getDisplayName(), statusCode, latency), ipAddress);

            return new ConnectionTestResult(success, statusCode, latency, message);

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            message = "Connection failed: " + e.getMessage();
            config.recordPing(false, latency, message);
            config.setConnectionStatus("DISCONNECTED");
            config.setApiStatus("ERROR");
            providerConfigRepository.save(config);

            logActivity(adminUser != null ? adminUser : "system", "CONNECTION_TEST_FAILED", providerId,
                    String.format("Connection test failed for %s: %s", config.getDisplayName(), e.getMessage()), ipAddress);

            return new ConnectionTestResult(false, 500, latency, message);
        }
    }

    private HttpRequest.Builder buildTestProbeRequest(SearchProviderConfig config) {
        String pid = config.getProviderId().toUpperCase();
        String testUrl = null;
        Map<String, String> headers = new HashMap<>();

        switch (pid) {
            case "GOOGLE" -> {
                String key = (config.getApiKey() != null) ? config.getApiKey().trim() : "";
                String cx = (config.getSearchEngineId() != null) ? config.getSearchEngineId().trim() : "";
                if (!key.isBlank() && !cx.isBlank()) {
                    testUrl = String.format("https://www.googleapis.com/customsearch/v1?key=%s&cx=%s&q=test&num=1", key, cx);
                } else {
                    testUrl = "https://www.googleapis.com/customsearch/v1";
                }
            }
            case "BING" -> {
                testUrl = "https://api.bing.microsoft.com/v7.0/search?q=test&count=1";
                if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                    headers.put("Ocp-Apim-Subscription-Key", config.getApiKey().trim());
                }
            }
            case "BRAVE" -> {
                testUrl = "https://api.search.brave.com/res/v1/web/search?q=test&count=1";
                if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                    headers.put("X-Subscription-Token", config.getApiKey().trim());
                }
            }
            case "YAHOO" -> {
                if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                    testUrl = "https://yahoo-search.p.rapidapi.com/web?q=test&count=1";
                    headers.put("X-RapidAPI-Key", config.getApiKey().trim());
                    headers.put("X-RapidAPI-Host", "yahoo-search.p.rapidapi.com");
                } else {
                    testUrl = "https://news.search.yahoo.com/rss?p=test";
                }
            }
            case "DUCKDUCKGO" -> testUrl = "https://api.duckduckgo.com/?q=test&format=json&no_html=1";
            case "WIKIPEDIA" -> testUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=test&format=json&srlimit=1";
            case "MOJEEK" -> {
                if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                    testUrl = "https://www.mojeek.com/search?api_key=" + config.getApiKey().trim() + "&q=test&fmt=json&size=1";
                } else {
                    testUrl = "https://www.mojeek.com/search?q=test&fmt=rss";
                }
            }
            case "YANDEX" -> {
                testUrl = (config.getEndpointUrl() != null && !config.getEndpointUrl().isBlank())
                        ? config.getEndpointUrl() + "?query=test&limit=1"
                        : "https://searchapi.yandex.net/v2/web/search?query=test&limit=1";
                if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                    headers.put("Authorization", "Api-Key " + config.getApiKey().trim());
                }
            }
            case "SEARXNG" -> {
                String ep = (config.getEndpointUrl() != null && !config.getEndpointUrl().isBlank())
                        ? config.getEndpointUrl() : "https://searx.be/search";
                testUrl = ep + "?q=test&format=json";
            }
            default -> {
                if (config.getEndpointUrl() != null && !config.getEndpointUrl().isBlank()) {
                    testUrl = config.getEndpointUrl();
                    if (!testUrl.contains("q=")) {
                        testUrl += (testUrl.contains("?") ? "&" : "?") + "q=test";
                    }
                    if ("API_KEY_HEADER".equalsIgnoreCase(config.getAuthMethod()) && config.getApiKey() != null) {
                        headers.put(config.getAuthHeaderName() != null ? config.getAuthHeaderName() : "X-API-KEY", config.getApiKey());
                    } else if ("BEARER_TOKEN".equalsIgnoreCase(config.getAuthMethod()) && config.getApiKey() != null) {
                        headers.put("Authorization", "Bearer " + config.getApiKey());
                    }
                } else if (config.getWebsiteUrl() != null && !config.getWebsiteUrl().isBlank()) {
                    testUrl = config.getWebsiteUrl();
                }
            }
        }

        if (testUrl == null) return null;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(testUrl))
                .timeout(Duration.ofMillis(config.getTimeoutMs() != null ? config.getTimeoutMs() : 3000))
                .header("User-Agent", "SmartSearchBot/1.0 (+http://localhost:8080/bot)")
                .header("Accept", "*/*")
                .GET();

        headers.forEach(builder::header);
        return builder;
    }

    // --- Add New Custom Search Engine (Requirement B) ---
    @Transactional
    public SearchProviderConfig addNewSearchEngine(SearchProviderConfig newConfig, String adminUser, String ipAddress) {
        String cleanId = newConfig.getDisplayName().toUpperCase().replaceAll("[^A-Z0-9]", "_");
        if (cleanId.isBlank()) {
            cleanId = "CUSTOM_" + System.currentTimeMillis();
        }
        if (cleanId.length() > 40) {
            cleanId = cleanId.substring(0, 40);
        }

        // Ensure unique providerId
        String finalId = cleanId;
        int counter = 1;
        while (providerConfigRepository.findByProviderId(finalId).isPresent()) {
            finalId = cleanId + "_" + counter++;
        }

        newConfig.setProviderId(finalId);
        newConfig.setIsCustom(true);
        newConfig.setIntegrationStatus("PENDING_SETUP");
        newConfig.setConnectionStatus("UNTESTED");
        newConfig.setApiStatus("KEY_REQUIRED");
        if (newConfig.getResultLimit() == null || newConfig.getResultLimit() <= 0) {
            newConfig.setResultLimit(10);
        }
        if (newConfig.getTimeoutMs() == null || newConfig.getTimeoutMs() <= 0) {
            newConfig.setTimeoutMs(3000);
        }

        SearchProviderConfig saved = providerConfigRepository.save(newConfig);

        logActivity(adminUser, "PROVIDER_ADDED", saved.getProviderId(),
                String.format("Added new search provider '%s' (Endpoint: %s)", saved.getDisplayName(), saved.getEndpointUrl()), ipAddress);

        return saved;
    }

    // --- Update Provider Configuration (Requirement E, H) ---
    @Transactional
    public SearchProviderConfig updateProvider(SearchProviderConfig updated, String adminUser, String ipAddress) {
        SearchProviderConfig existing = providerConfigRepository.findById(updated.getId())
                .orElseThrow(() -> new IllegalArgumentException("Provider with id " + updated.getId() + " not found."));

        existing.setDisplayName(updated.getDisplayName());
        existing.setEnabled(Boolean.TRUE.equals(updated.getEnabled()));
        existing.setPriority(updated.getPriority() != null ? updated.getPriority() : 1);
        existing.setResultLimit(updated.getResultLimit() != null ? updated.getResultLimit() : 10);
        existing.setTimeoutMs(updated.getTimeoutMs() != null ? updated.getTimeoutMs() : 3000);
        existing.setIsFallback(Boolean.TRUE.equals(updated.getIsFallback()));
        existing.setPricingTier(updated.getPricingTier());

        if (updated.getWebsiteUrl() != null) existing.setWebsiteUrl(updated.getWebsiteUrl().trim());
        if (updated.getEndpointUrl() != null) existing.setEndpointUrl(updated.getEndpointUrl().trim());
        if (updated.getDocUrl() != null) existing.setDocUrl(updated.getDocUrl().trim());
        if (updated.getApiKey() != null) existing.setApiKey(updated.getApiKey().trim());
        if (updated.getSearchEngineId() != null) existing.setSearchEngineId(updated.getSearchEngineId().trim());
        if (updated.getAuthMethod() != null) existing.setAuthMethod(updated.getAuthMethod());
        if (updated.getAuthHeaderName() != null) existing.setAuthHeaderName(updated.getAuthHeaderName().trim());
        if (updated.getSupportedRegions() != null) existing.setSupportedRegions(updated.getSupportedRegions().trim());
        if (updated.getSupportedLanguages() != null) existing.setSupportedLanguages(updated.getSupportedLanguages().trim());

        SearchProviderConfig saved = providerConfigRepository.save(existing);

        logActivity(adminUser, "PROVIDER_UPDATED", saved.getProviderId(),
                String.format("Updated settings for provider '%s' (Priority: #%d, Enabled: %s)", saved.getDisplayName(), saved.getPriority(), saved.getEnabled()), ipAddress);

        return saved;
    }

    // --- Toggle Provider Status ---
    @Transactional
    public boolean toggleProviderStatus(Long id, boolean enabled, String adminUser, String ipAddress) {
        Optional<SearchProviderConfig> opt = providerConfigRepository.findById(id);
        if (opt.isPresent()) {
            SearchProviderConfig cfg = opt.get();
            cfg.setEnabled(enabled);
            providerConfigRepository.save(cfg);

            logActivity(adminUser, enabled ? "PROVIDER_ACTIVATED" : "PROVIDER_DEACTIVATED", cfg.getProviderId(),
                    String.format("Provider '%s' was %s by administrator.", cfg.getDisplayName(), enabled ? "enabled" : "disabled"), ipAddress);
            return true;
        }
        return false;
    }

    // --- Delete Custom Provider ---
    @Transactional
    public boolean deleteCustomProvider(Long id, String adminUser, String ipAddress) {
        Optional<SearchProviderConfig> opt = providerConfigRepository.findById(id);
        if (opt.isPresent()) {
            SearchProviderConfig cfg = opt.get();
            if (Boolean.TRUE.equals(cfg.getIsCustom())) {
                providerConfigRepository.delete(cfg);
                logActivity(adminUser, "PROVIDER_DELETED", cfg.getProviderId(),
                        String.format("Custom provider '%s' was deleted by administrator.", cfg.getDisplayName()), ipAddress);
                return true;
            }
        }
        return false;
    }

    // --- Discover & Recommend Providers (Requirement C) ---
    public List<SearchProviderRecommendation> getAllRecommendations() {
        return recommendationRepository.findAllByOrderByQualityRatingDesc();
    }

    @Transactional
    public boolean addRecommendedProvider(String providerId, String adminUser, String ipAddress) {
        Optional<SearchProviderRecommendation> recOpt = recommendationRepository.findByProviderId(providerId);
        if (recOpt.isEmpty()) return false;

        SearchProviderRecommendation rec = recOpt.get();
        rec.setIsAdded(true);
        recommendationRepository.save(rec);

        // Check if config already exists or create new
        Optional<SearchProviderConfig> existing = providerConfigRepository.findByProviderId(providerId);
        if (existing.isEmpty()) {
            SearchProviderConfig cfg = new SearchProviderConfig();
            cfg.setProviderId(rec.getProviderId());
            cfg.setDisplayName(rec.getName());
            cfg.setWebsiteUrl(rec.getWebsiteUrl());
            cfg.setEndpointUrl(rec.getApiEndpoint());
            cfg.setDocUrl(rec.getDocUrl());
            cfg.setSupportedRegions(rec.getGlobalCoverage());
            cfg.setSupportedLanguages(rec.getSupportedLanguages());
            cfg.setPricingTier(rec.getPricingModel());
            cfg.setEnabled(false); // Created in deactivated state pending admin setup & connection test
            cfg.setIntegrationStatus("PENDING_SETUP");
            cfg.setConnectionStatus("UNTESTED");
            cfg.setApiStatus("KEY_REQUIRED");
            cfg.setPriority(6);
            cfg.setIsCustom(false);
            providerConfigRepository.save(cfg);
        } else {
            SearchProviderConfig cfg = existing.get();
            cfg.setEnabled(true);
            providerConfigRepository.save(cfg);
        }

        logActivity(adminUser, "RECOMMENDATION_ADDED", providerId,
                String.format("Added recommended provider '%s' into active integration directory.", rec.getName()), ipAddress);

        return true;
    }

    public void logActivity(String adminUsername, String action, String targetProviderId, String details, String ipAddress) {
        try {
            AdminActivityLog log = new AdminActivityLog(adminUsername, action, targetProviderId, details, ipAddress);
            activityLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Failed to write admin activity log: {}", e.getMessage());
        }
    }

    public List<AdminActivityLog> getRecentActivityLogs() {
        return activityLogRepository.findTop50ByOrderByTimestampDesc();
    }

    public static class ConnectionTestResult {
        public final boolean success;
        public final int statusCode;
        public final long latencyMs;
        public final String message;

        public ConnectionTestResult(boolean success, int statusCode, long latencyMs, String message) {
            this.success = success;
            this.statusCode = statusCode;
            this.latencyMs = latencyMs;
            this.message = message;
        }
    }
}

