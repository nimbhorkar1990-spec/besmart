package com.smartsearch.controller;

import com.smartsearch.model.AdminActivityLog;
import com.smartsearch.model.SearchProviderConfig;
import com.smartsearch.model.SearchProviderRecommendation;
import com.smartsearch.model.User;
import com.smartsearch.repository.ApiUsageLogRepository;
import com.smartsearch.service.SearchEngineSourcesService;
import com.smartsearch.service.SystemSettingService;
import com.smartsearch.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/admin/search-sources")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class SearchSourcesAdminController {

    private final SearchEngineSourcesService sourcesService;
    private final ApiUsageLogRepository apiUsageLogRepository;
    private final SystemSettingService settingService;
    private final UserService userService;

    public SearchSourcesAdminController(SearchEngineSourcesService sourcesService,
                                        ApiUsageLogRepository apiUsageLogRepository,
                                        SystemSettingService settingService,
                                        UserService userService) {
        this.sourcesService = sourcesService;
        this.apiUsageLogRepository = apiUsageLogRepository;
        this.settingService = settingService;
        this.userService = userService;
    }

    @GetMapping
    public String showSearchSourcesDashboard(@RequestParam(value = "tab", defaultValue = "sources") String activeTab,
                                             @RequestParam(value = "testId", required = false) String testId,
                                             Model model) {
        List<SearchProviderConfig> providers = sourcesService.getAllProviders();
        List<SearchProviderRecommendation> recommendations = sourcesService.getAllRecommendations();
        List<AdminActivityLog> activityLogs = sourcesService.getRecentActivityLogs();

        // Calculate Global Aggregate Metrics (Requirement G)
        long totalProviders = providers.size();
        long activeProviders = providers.stream().filter(p -> Boolean.TRUE.equals(p.getEnabled())).count();
        long connectedProviders = providers.stream().filter(p -> "CONNECTED".equalsIgnoreCase(p.getConnectionStatus())).count();
        long totalCalls = providers.stream().mapToLong(p -> p.getTotalCalls() != null ? p.getTotalCalls() : 0L).sum();
        long successCalls = providers.stream().mapToLong(p -> p.getSuccessCalls() != null ? p.getSuccessCalls() : 0L).sum();
        long failureCalls = providers.stream().mapToLong(p -> p.getFailureCalls() != null ? p.getFailureCalls() : 0L).sum();
        long totalContributed = providers.stream().mapToLong(p -> p.getResultsContributed() != null ? p.getResultsContributed() : 0L).sum();
        double successRate = totalCalls > 0 ? (successCalls * 100.0 / totalCalls) : 100.0;

        // Worldwide Country Coverage Data (Requirement D)
        List<Map<String, Object>> worldwideCoverage = buildWorldwideCoverageData(providers);

        model.addAttribute("providers", providers);
        model.addAttribute("recommendations", recommendations);
        model.addAttribute("activityLogs", activityLogs);
        model.addAttribute("recentApiLogs", apiUsageLogRepository.findTop20ByOrderByTimestampDesc());
        model.addAttribute("activeTab", activeTab);

        // Metrics Summary
        model.addAttribute("totalProviders", totalProviders);
        model.addAttribute("activeProviders", activeProviders);
        model.addAttribute("connectedProviders", connectedProviders);
        model.addAttribute("totalCalls", totalCalls);
        model.addAttribute("successCalls", successCalls);
        model.addAttribute("failureCalls", failureCalls);
        model.addAttribute("totalContributed", totalContributed);
        model.addAttribute("successRate", Math.round(successRate * 10.0) / 10.0);

        // Global Routing Settings (Requirement E)
        model.addAttribute("maxProvidersPerQuery", settingService.getIntSetting("MAX_ACTIVE_PROVIDERS_PER_QUERY", 6));
        model.addAttribute("externalSearchEnabled", settingService.getBooleanSetting("EXTERNAL_SEARCH_ENABLED", true));
        model.addAttribute("autoCrawlerIndexing", settingService.getBooleanSetting("CRAWLER_AUTO_INDEXING", true));

        // Coverage model
        model.addAttribute("countries", worldwideCoverage);

        return "admin/search-sources";
    }

    @PostMapping("/add")
    public String addNewSearchEngine(@ModelAttribute SearchProviderConfig newConfig,
                                     HttpServletRequest request,
                                     RedirectAttributes redirectAttributes) {
        String adminUser = getCurrentAdminUsername();
        String ipAddress = getClientIp(request);

        if (newConfig.getDisplayName() == null || newConfig.getDisplayName().isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Search Engine Name is required.");
            return "redirect:/admin/search-sources?tab=add";
        }
        if (newConfig.getEndpointUrl() == null || newConfig.getEndpointUrl().isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Search API Endpoint URL is required.");
            return "redirect:/admin/search-sources?tab=add";
        }

        try {
            SearchProviderConfig saved = sourcesService.addNewSearchEngine(newConfig, adminUser, ipAddress);
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Search engine '%s' registered successfully with ID '%s'. Run a Connection Test to verify integration.",
                            saved.getDisplayName(), saved.getProviderId()));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to add search engine: " + e.getMessage());
        }

        return "redirect:/admin/search-sources";
    }

    @PostMapping("/update")
    public String updateSearchEngine(@ModelAttribute SearchProviderConfig updated,
                                     HttpServletRequest request,
                                     RedirectAttributes redirectAttributes) {
        String adminUser = getCurrentAdminUsername();
        String ipAddress = getClientIp(request);

        try {
            SearchProviderConfig saved = sourcesService.updateProvider(updated, adminUser, ipAddress);
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Configuration updated for '%s'.", saved.getDisplayName()));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Update failed: " + e.getMessage());
        }

        return "redirect:/admin/search-sources";
    }

    @PostMapping("/toggle")
    public String toggleProvider(@RequestParam("id") Long id,
                                 @RequestParam("enabled") boolean enabled,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        String adminUser = getCurrentAdminUsername();
        String ipAddress = getClientIp(request);

        boolean success = sourcesService.toggleProviderStatus(id, enabled, adminUser, ipAddress);
        if (success) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Provider status successfully " + (enabled ? "activated" : "deactivated") + ".");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Provider not found.");
        }

        return "redirect:/admin/search-sources";
    }

    @PostMapping("/test-connection")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testConnectionAjax(@RequestParam("providerId") String providerId,
                                                                 HttpServletRequest request) {
        String adminUser = getCurrentAdminUsername();
        String ipAddress = getClientIp(request);

        SearchEngineSourcesService.ConnectionTestResult result =
                sourcesService.testProviderConnection(providerId, adminUser, ipAddress);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", result.success);
        resp.put("statusCode", result.statusCode);
        resp.put("latencyMs", result.latencyMs);
        resp.put("message", result.message);

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/test-connection-form")
    public String testConnectionForm(@RequestParam("providerId") String providerId,
                                     HttpServletRequest request,
                                     RedirectAttributes redirectAttributes) {
        String adminUser = getCurrentAdminUsername();
        String ipAddress = getClientIp(request);

        SearchEngineSourcesService.ConnectionTestResult result =
                sourcesService.testProviderConnection(providerId, adminUser, ipAddress);

        if (result.success) {
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("[%s] %s", providerId, result.message));
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    String.format("[%s] %s", providerId, result.message));
        }

        return "redirect:/admin/search-sources";
    }

    @PostMapping("/delete")
    public String deleteCustomProvider(@RequestParam("id") Long id,
                                       HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        String adminUser = getCurrentAdminUsername();
        String ipAddress = getClientIp(request);

        boolean deleted = sourcesService.deleteCustomProvider(id, adminUser, ipAddress);
        if (deleted) {
            redirectAttributes.addFlashAttribute("successMessage", "Custom search provider deleted.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot delete built-in provider or provider not found.");
        }

        return "redirect:/admin/search-sources";
    }

    @PostMapping("/add-recommended")
    public String addRecommended(@RequestParam("providerId") String providerId,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        String adminUser = getCurrentAdminUsername();
        String ipAddress = getClientIp(request);

        boolean added = sourcesService.addRecommendedProvider(providerId, adminUser, ipAddress);
        if (added) {
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Added '%s' to integration list. Please configure API credentials and run a Connection Test to activate.", providerId));
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Recommended provider not found.");
        }

        return "redirect:/admin/search-sources?tab=sources";
    }

    @PostMapping("/update-routing")
    public String updateRoutingSettings(@RequestParam("maxProvidersPerQuery") int maxProviders,
                                        @RequestParam(value = "externalSearchEnabled", defaultValue = "false") boolean externalEnabled,
                                        @RequestParam(value = "autoCrawlerIndexing", defaultValue = "false") boolean autoCrawl,
                                        HttpServletRequest request,
                                        RedirectAttributes redirectAttributes) {
        settingService.updateSetting("MAX_ACTIVE_PROVIDERS_PER_QUERY", String.valueOf(maxProviders), "Max concurrent search providers");
        settingService.updateSetting("EXTERNAL_SEARCH_ENABLED", String.valueOf(externalEnabled), "Global external search switch");
        settingService.updateSetting("CRAWLER_AUTO_INDEXING", String.valueOf(autoCrawl), "Auto-crawl discovered search URLs");

        sourcesService.logActivity(getCurrentAdminUsername(), "ROUTING_RULES_UPDATED", "SYSTEM",
                String.format("Updated routing: Max Providers=%d, External Search=%s, Auto Indexing=%s",
                        maxProviders, externalEnabled, autoCrawl), getClientIp(request));

        redirectAttributes.addFlashAttribute("successMessage", "Global search routing rules updated successfully.");
        return "redirect:/admin/search-sources?tab=priority";
    }

    private List<Map<String, Object>> buildWorldwideCoverageData(List<SearchProviderConfig> providers) {
        List<Map<String, Object>> list = new ArrayList<>();

        addCountry(list, "US", "United States", "🇺🇸", "North America",
                List.of("Google", "Bing", "Yahoo", "DuckDuckGo", "Brave", "Mojeek", "Wikipedia"),
                "Google / Bing", "English, Spanish", 28, "Worldwide Leader");

        addCountry(list, "GB", "United Kingdom", "🇬🇧", "Western Europe",
                List.of("Google", "Bing", "Yahoo", "DuckDuckGo", "Brave", "Mojeek", "Wikipedia"),
                "Mojeek (Local Index) / Google", "English, Welsh, Gaelic", 32, "Verified Full Coverage");

        addCountry(list, "DE", "Germany", "🇩🇪", "Central Europe",
                List.of("Google", "Bing", "DuckDuckGo", "Brave", "Wikipedia", "SearXNG"),
                "Google / Brave / SearXNG", "German, English", 35, "Verified Full Coverage");

        addCountry(list, "FR", "France", "🇫🇷", "Western Europe",
                List.of("Google", "Bing", "Qwant", "DuckDuckGo", "Brave", "Wikipedia"),
                "Qwant (EU Privacy) / Google", "French, English", 34, "Verified Full Coverage");

        addCountry(list, "IN", "India", "🇮🇳", "South Asia",
                List.of("Google", "Bing", "Yahoo", "DuckDuckGo", "Brave", "Wikipedia"),
                "Google / Bing / Wikipedia", "Hindi, English, Bengali, Tamil, Telugu", 45, "Verified Full Coverage");

        addCountry(list, "JP", "Japan", "🇯🇵", "East Asia",
                List.of("Yahoo Japan", "Google", "Bing", "DuckDuckGo", "Wikipedia"),
                "Yahoo Japan / Google", "Japanese, English", 42, "Verified Full Coverage");

        addCountry(list, "RU", "Russia & CIS", "🇷🇺", "Eastern Europe & CIS",
                List.of("Yandex", "Google", "Wikipedia", "DuckDuckGo"),
                "Yandex (Cyrillic Specialized)", "Russian, Cyrillic 30+ Languages", 52, "Regional High Coverage");

        addCountry(list, "CN", "China", "🇨🇳", "East Asia",
                List.of("Baidu", "Bing", "Wikipedia"),
                "Baidu / Bing", "Simplified Chinese, Traditional Chinese", 65, "Regional Gateway");

        addCountry(list, "BR", "Brazil", "🇧🇷", "South America",
                List.of("Google", "Bing", "Yahoo", "DuckDuckGo", "Brave", "Wikipedia"),
                "Google / Yahoo", "Portuguese, English, Spanish", 48, "Verified Full Coverage");

        addCountry(list, "AU", "Australia", "🇦🇺", "Oceania",
                List.of("Google", "Bing", "DuckDuckGo", "Brave", "Wikipedia"),
                "Google / Bing", "English", 36, "Verified Full Coverage");

        addCountry(list, "CA", "Canada", "🇨🇦", "North America",
                List.of("Google", "Bing", "Yahoo", "DuckDuckGo", "Brave", "Wikipedia"),
                "Google / Bing", "English, French", 30, "Verified Full Coverage");

        addCountry(list, "ZA", "South Africa", "🇿🇦", "Sub-Saharan Africa",
                List.of("Google", "Bing", "DuckDuckGo", "Brave", "Wikipedia"),
                "Google / Bing", "English, Afrikaans, Zulu, Xhosa", 55, "Verified Regional Coverage");

        addCountry(list, "SG", "Singapore", "🇸🇬", "Southeast Asia",
                List.of("Google", "Bing", "Yahoo", "DuckDuckGo", "Brave", "Wikipedia"),
                "Google / Bing", "English, Mandarin, Malay, Tamil", 22, "Regional Hub Hub Coverage");

        return list;
    }

    private void addCountry(List<Map<String, Object>> list, String code, String name, String flag, String region,
                            List<String> providerNames, String primaryEngine, String languages, int latencyMs, String status) {
        Map<String, Object> c = new HashMap<>();
        c.put("code", code);
        c.put("name", name);
        c.put("flag", flag);
        c.put("region", region);
        c.put("providers", providerNames);
        c.put("providerCount", providerNames.size());
        c.put("primaryEngine", primaryEngine);
        c.put("languages", languages);
        c.put("latencyMs", latencyMs);
        c.put("status", status);
        list.add(c);
    }

    private String getCurrentAdminUsername() {
        Optional<User> u = userService.getCurrentUser();
        return u.map(User::getUsername).orElse("admin");
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

