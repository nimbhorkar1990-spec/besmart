package com.smartsearch.controller;

import com.smartsearch.dto.ApiConfigDto;
import com.smartsearch.dto.IndexPageDto;
import com.smartsearch.model.Category;
import com.smartsearch.model.IndexedPage;
import com.smartsearch.model.SearchAnalytics;
import com.smartsearch.model.User;
import com.smartsearch.repository.IndexedPageRepository;
import com.smartsearch.repository.UserRepository;
import com.smartsearch.service.AnalyticsService;
import com.smartsearch.service.LuceneService;
import com.smartsearch.service.SystemSettingService;
import com.smartsearch.service.UserService;
import com.smartsearch.service.WebCrawlerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final AnalyticsService analyticsService;
    private final LuceneService luceneService;
    private final WebCrawlerService webCrawlerService;
    private final SystemSettingService settingService;
    private final IndexedPageRepository indexedPageRepository;
    private final UserRepository userRepository;
    private final com.smartsearch.queue.CrawlQueueManager crawlQueueManager;
    private final com.smartsearch.repository.SearchProviderConfigRepository providerConfigRepository;
    private final com.smartsearch.repository.DiscoveredUrlRepository discoveredUrlRepository;
    private final com.smartsearch.repository.CrawledPageRepository crawledPageRepository;
    private final com.smartsearch.repository.CrawlJobRepository crawlJobRepository;
    private final com.smartsearch.repository.CrawlQueueItemRepository crawlQueueItemRepository;
    private final com.smartsearch.repository.CrawlErrorLogRepository crawlErrorLogRepository;
    private final com.smartsearch.repository.DuplicateRecordRepository duplicateRecordRepository;
    private final com.smartsearch.repository.ApiUsageLogRepository apiUsageLogRepository;

    public AdminController(UserService userService,
                           AnalyticsService analyticsService,
                           LuceneService luceneService,
                           WebCrawlerService webCrawlerService,
                           SystemSettingService settingService,
                           IndexedPageRepository indexedPageRepository,
                           UserRepository userRepository,
                           com.smartsearch.queue.CrawlQueueManager crawlQueueManager,
                           com.smartsearch.repository.SearchProviderConfigRepository providerConfigRepository,
                           com.smartsearch.repository.DiscoveredUrlRepository discoveredUrlRepository,
                           com.smartsearch.repository.CrawledPageRepository crawledPageRepository,
                           com.smartsearch.repository.CrawlJobRepository crawlJobRepository,
                           com.smartsearch.repository.CrawlQueueItemRepository crawlQueueItemRepository,
                           com.smartsearch.repository.CrawlErrorLogRepository crawlErrorLogRepository,
                           com.smartsearch.repository.DuplicateRecordRepository duplicateRecordRepository,
                           com.smartsearch.repository.ApiUsageLogRepository apiUsageLogRepository) {
        this.userService = userService;
        this.analyticsService = analyticsService;
        this.luceneService = luceneService;
        this.webCrawlerService = webCrawlerService;
        this.settingService = settingService;
        this.indexedPageRepository = indexedPageRepository;
        this.userRepository = userRepository;
        this.crawlQueueManager = crawlQueueManager;
        this.providerConfigRepository = providerConfigRepository;
        this.discoveredUrlRepository = discoveredUrlRepository;
        this.crawledPageRepository = crawledPageRepository;
        this.crawlJobRepository = crawlJobRepository;
        this.crawlQueueItemRepository = crawlQueueItemRepository;
        this.crawlErrorLogRepository = crawlErrorLogRepository;
        this.duplicateRecordRepository = duplicateRecordRepository;
        this.apiUsageLogRepository = apiUsageLogRepository;
    }

    @ModelAttribute
    public void addCommonAttributes(Model model) {
        userService.getCurrentUser().ifPresent(u -> model.addAttribute("currentUser", u));
    }

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        Map<String, Object> analyticsSummary = analyticsService.getAnalyticsSummary();
        Map<String, Object> luceneStats = luceneService.getIndexStats();

        model.addAttribute("analytics", analyticsSummary);
        model.addAttribute("luceneStats", luceneStats);
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalIndexedPages", indexedPageRepository.count());
        model.addAttribute("topQueries", analyticsService.getTopQueries(8));
        model.addAttribute("recentPages", indexedPageRepository.findTop50ByOrderByIndexedAtDesc().stream().limit(6).toList());

        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String users(@RequestParam(value = "search", required = false) String search,
                        @RequestParam(value = "page", defaultValue = "1") int page,
                        Model model) {
        int pageNum = Math.max(page - 1, 0);
        Page<User> usersPage = userService.getAllUsers(search, PageRequest.of(pageNum, 10));

        model.addAttribute("usersPage", usersPage);
        model.addAttribute("search", search != null ? search : "");
        model.addAttribute("currentPage", page);
        return "admin/users";
    }

    @PostMapping("/users/toggle-status")
    public String toggleUserStatus(@RequestParam("id") Long id, RedirectAttributes redirectAttributes) {
        userService.toggleUserStatus(id);
        redirectAttributes.addFlashAttribute("successMessage", "User account status updated.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/delete")
    public String deleteUser(@RequestParam("id") Long id, RedirectAttributes redirectAttributes) {
        userService.deleteUser(id);
        redirectAttributes.addFlashAttribute("successMessage", "User deleted successfully.");
        return "redirect:/admin/users";
    }

    @GetMapping("/indexer")
    public String indexer(@RequestParam(value = "page", defaultValue = "1") int page,
                          @RequestParam(value = "search", required = false) String search,
                          Model model) {
        int pageNum = Math.max(page - 1, 0);
        Page<IndexedPage> indexedPages;
        if (search != null && !search.isBlank()) {
            indexedPages = indexedPageRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(search, search, PageRequest.of(pageNum, 10));
        } else {
            indexedPages = indexedPageRepository.findAll(PageRequest.of(pageNum, 10));
        }

        model.addAttribute("indexedPages", indexedPages);
        model.addAttribute("luceneStats", luceneService.getIndexStats());
        model.addAttribute("indexPageDto", new IndexPageDto());
        model.addAttribute("categories", Category.values());
        model.addAttribute("currentPage", page);
        model.addAttribute("search", search != null ? search : "");
        return "admin/indexer";
    }

    @PostMapping("/indexer/crawl")
    public String crawlAndIndex(@Valid @ModelAttribute("indexPageDto") IndexPageDto dto,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid URL or input.");
            return "redirect:/admin/indexer";
        }
        try {
            webCrawlerService.crawlAndIndex(dto);
            redirectAttributes.addFlashAttribute("successMessage", "URL crawled and indexed into Lucene successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Crawling error: " + e.getMessage());
        }
        return "redirect:/admin/indexer";
    }

    @PostMapping("/indexer/reindex")
    public String reindexAll(RedirectAttributes redirectAttributes) {
        try {
            luceneService.clearIndex();
            luceneService.indexDocuments(indexedPageRepository.findAll());
            redirectAttributes.addFlashAttribute("successMessage", "All database pages successfully re-indexed into Apache Lucene!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Re-indexing error: " + e.getMessage());
        }
        return "redirect:/admin/indexer";
    }

    @PostMapping("/indexer/delete")
    public String deleteIndexedPage(@RequestParam("id") Long id, RedirectAttributes redirectAttributes) {
        indexedPageRepository.findById(id).ifPresent(p -> {
            luceneService.deleteDocument(p.getUrl());
            indexedPageRepository.delete(p);
        });
        redirectAttributes.addFlashAttribute("successMessage", "Page removed from Lucene index and database.");
        return "redirect:/admin/indexer";
    }

    @GetMapping("/analytics")
    public String analytics(@RequestParam(value = "page", defaultValue = "1") int page,
                            @RequestParam(value = "search", required = false) String search,
                            Model model) {
        int pageNum = Math.max(page - 1, 0);
        Page<SearchAnalytics> analyticsPage = analyticsService.getPopularQueriesPaged(search, PageRequest.of(pageNum, 15));

        model.addAttribute("analyticsPage", analyticsPage);
        model.addAttribute("summary", analyticsService.getAnalyticsSummary());
        model.addAttribute("currentPage", page);
        model.addAttribute("search", search != null ? search : "");
        return "admin/analytics";
    }

    @PostMapping("/analytics/delete")
    public String deleteKeywordAnalytics(@RequestParam("id") Long id,
                                         @RequestParam(value = "page", defaultValue = "1") int page,
                                         @RequestParam(value = "search", required = false) String search,
                                         @RequestParam(value = "from", required = false) String from,
                                         RedirectAttributes redirectAttributes) {
        Optional<String> deletedQuery = analyticsService.deleteQueryAnalytics(id);
        if (deletedQuery.isPresent()) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Search keyword '" + deletedQuery.get() + "' was successfully deleted from analytics.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Search keyword could not be found.");
        }

        if ("dashboard".equalsIgnoreCase(from)) {
            return "redirect:/admin/dashboard";
        }

        String redirectUrl = "redirect:/admin/analytics?page=" + page;
        if (search != null && !search.isBlank()) {
            redirectUrl += "&search=" + search.trim();
        }
        return redirectUrl;
    }

    @PostMapping("/analytics/clear-all")
    public String clearAllAnalytics(RedirectAttributes redirectAttributes) {
        analyticsService.clearAllAnalytics();
        redirectAttributes.addFlashAttribute("successMessage", "All search keyword analytics have been deleted successfully.");
        return "redirect:/admin/analytics";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("apiConfig", settingService.getApiConfig());
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String updateSettings(@ModelAttribute("apiConfig") ApiConfigDto dto, RedirectAttributes redirectAttributes) {
        settingService.updateApiConfig(dto);
        redirectAttributes.addFlashAttribute("successMessage", "System & Search API settings updated successfully!");
        return "redirect:/admin/settings";
    }

    @GetMapping("/system")
    public String systemInfo(Model model) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

        Map<String, Object> systemMetrics = new HashMap<>();
        long heapUsedMb = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMaxMb = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        systemMetrics.put("heapUsedMb", heapUsedMb);
        systemMetrics.put("heapMaxMb", heapMaxMb);
        systemMetrics.put("heapPercent", Math.round(((double) heapUsedMb / (heapMaxMb > 0 ? heapMaxMb : 1)) * 100));
        systemMetrics.put("threadCount", threadBean.getThreadCount());
        systemMetrics.put("osName", osBean.getName() + " " + osBean.getArch());
        systemMetrics.put("availableProcessors", osBean.getAvailableProcessors());
        systemMetrics.put("javaVersion", System.getProperty("java.version"));
        systemMetrics.put("javaVendor", System.getProperty("java.vendor"));
        systemMetrics.put("luceneStats", luceneService.getIndexStats());

        model.addAttribute("metrics", systemMetrics);
        return "admin/system";
    }

    @GetMapping("/crawler")
    public String crawlerDashboard(
            @RequestParam(value = "errorType", required = false) String errorType,
            @RequestParam(value = "searchError", required = false) String searchError,
            @RequestParam(value = "jobId", required = false) Long jobId,
            Model model) {
        model.addAttribute("queueMetrics", crawlQueueManager.getQueueMetrics());
        model.addAttribute("providers", providerConfigRepository.findAllByOrderByPriorityAsc());
        model.addAttribute("recentJobs", crawlJobRepository.findTop20ByOrderByStartedAtDesc());
        model.addAttribute("recentApiLogs", apiUsageLogRepository.findTop20ByOrderByTimestampDesc());
        model.addAttribute("isCrawlerPaused", crawlQueueManager.isPaused());

        // Crawler settings
        model.addAttribute("maxDepth", settingService.getIntSetting("CRAWLER_MAX_DEPTH", 2));
        model.addAttribute("maxPagesPerDomain", settingService.getIntSetting("CRAWLER_MAX_PAGES_PER_DOMAIN", 10));
        model.addAttribute("delayMs", settingService.getIntSetting("CRAWLER_DELAY_MS", 500));
        model.addAttribute("crawlerEnabled", settingService.getBooleanSetting("CRAWLER_ENABLED", true));

        // Crawl Safety & Error Log Filtering & Analytics
        List<com.smartsearch.model.CrawlErrorLog> errors;
        boolean hasTypeFilter = errorType != null && !errorType.isBlank() && !"ALL".equalsIgnoreCase(errorType);
        boolean hasSearchFilter = searchError != null && !searchError.isBlank();

        if (jobId != null) {
            errors = crawlErrorLogRepository.findByCrawlJobIdOrderByOccurredAtDesc(jobId);
        } else if (hasTypeFilter && hasSearchFilter) {
            errors = crawlErrorLogRepository.findByErrorTypeAndDomainContainingIgnoreCaseOrderByOccurredAtDesc(errorType, searchError.trim());
        } else if (hasTypeFilter) {
            errors = crawlErrorLogRepository.findByErrorTypeOrderByOccurredAtDesc(errorType);
        } else if (hasSearchFilter) {
            errors = crawlErrorLogRepository.findByDomainContainingIgnoreCaseOrUrlContainingIgnoreCaseOrderByOccurredAtDesc(searchError.trim(), searchError.trim());
        } else {
            errors = crawlErrorLogRepository.findTop50ByOrderByOccurredAtDesc();
        }

        model.addAttribute("recentErrors", errors);
        model.addAttribute("selectedErrorType", errorType != null ? errorType : "ALL");
        model.addAttribute("searchError", searchError != null ? searchError : "");
        model.addAttribute("selectedJobId", jobId);
        model.addAttribute("distinctErrorTypes", crawlErrorLogRepository.findDistinctErrorTypes());

        // Safety stats
        long ssrfBlocked = crawlErrorLogRepository.countByErrorType("SSRF_BLOCKED")
                + crawlErrorLogRepository.countByErrorType("PRIVATE_IP_BLOCKED")
                + crawlErrorLogRepository.countByErrorType("RESTRICTED_PORT")
                + crawlErrorLogRepository.countByErrorType("DISALLOWED_EXTENSION");
        long robotsDisallowed = crawlErrorLogRepository.countByErrorType("ROBOTS_DISALLOWED");
        long httpErrors = crawlErrorLogRepository.countByErrorType("HTTP_STATUS");
        long timeouts = crawlErrorLogRepository.countByErrorType("TIMEOUT");
        long dnsFailures = crawlErrorLogRepository.countByErrorType("DNS_FAILURE");
        long totalErrors = crawlErrorLogRepository.count();

        Map<String, Object> safetyStats = new HashMap<>();
        safetyStats.put("ssrfBlocked", ssrfBlocked);
        safetyStats.put("robotsDisallowed", robotsDisallowed);
        safetyStats.put("httpErrors", httpErrors);
        safetyStats.put("timeouts", timeouts);
        safetyStats.put("dnsFailures", dnsFailures);
        safetyStats.put("totalErrors", totalErrors);
        model.addAttribute("safetyStats", safetyStats);

        return "admin/crawler";
    }

    @PostMapping("/crawler/toggle-pause")
    public String toggleCrawlerPause(RedirectAttributes redirectAttributes) {
        boolean currentlyPaused = crawlQueueManager.isPaused();
        crawlQueueManager.setPaused(!currentlyPaused);
        String msg = !currentlyPaused ? "Automated crawler paused." : "Automated crawler resumed.";
        redirectAttributes.addFlashAttribute("successMessage", msg);
        return "redirect:/admin/crawler";
    }

    @PostMapping("/crawler/retry-failed")
    public String retryFailedUrls(RedirectAttributes redirectAttributes) {
        crawlQueueManager.retryAllFailed();
        redirectAttributes.addFlashAttribute("successMessage", "Failed URLs re-queued for processing with exponential backoff.");
        return "redirect:/admin/crawler";
    }

    @PostMapping("/crawler/settings")
    public String updateCrawlerSettings(@RequestParam("maxDepth") int maxDepth,
                                        @RequestParam("maxPagesPerDomain") int maxPagesPerDomain,
                                        @RequestParam("delayMs") int delayMs,
                                        @RequestParam(value = "crawlerEnabled", defaultValue = "false") boolean crawlerEnabled,
                                        RedirectAttributes redirectAttributes) {
        settingService.updateSetting("CRAWLER_MAX_DEPTH", String.valueOf(maxDepth), "Max crawl depth");
        settingService.updateSetting("CRAWLER_MAX_PAGES_PER_DOMAIN", String.valueOf(maxPagesPerDomain), "Max pages per domain");
        settingService.updateSetting("CRAWLER_DELAY_MS", String.valueOf(delayMs), "Crawl delay in ms");
        settingService.updateSetting("CRAWLER_ENABLED", String.valueOf(crawlerEnabled), "Crawler enabled");

        redirectAttributes.addFlashAttribute("successMessage", "Web crawler policies and limits updated successfully.");
        return "redirect:/admin/crawler";
    }

    @PostMapping("/crawler/provider/update")
    public String updateProvider(@RequestParam("id") Long id,
                                 @RequestParam(value = "enabled", defaultValue = "false") boolean enabled,
                                 @RequestParam(value = "apiKey", required = false) String apiKey,
                                 @RequestParam(value = "searchEngineId", required = false) String searchEngineId,
                                 @RequestParam(value = "priority", defaultValue = "1") int priority,
                                 RedirectAttributes redirectAttributes) {
        providerConfigRepository.findById(id).ifPresent(p -> {
            p.setEnabled(enabled);
            p.setApiKey(apiKey != null ? apiKey.trim() : "");
            p.setSearchEngineId(searchEngineId != null ? searchEngineId.trim() : "");
            p.setPriority(priority);
            providerConfigRepository.save(p);

            // Also synchronize with SystemSettingService
            if ("GOOGLE".equalsIgnoreCase(p.getProviderId())) {
                settingService.updateSetting("GOOGLE_API_KEY", p.getApiKey(), "Google Custom Search API Key");
                settingService.updateSetting("GOOGLE_SEARCH_ENGINE_ID", p.getSearchEngineId(), "Google Search Engine CX ID");
                settingService.updateSetting("GOOGLE_SEARCH_ENABLED", String.valueOf(enabled), "Google search active");
            } else if ("BING".equalsIgnoreCase(p.getProviderId())) {
                settingService.updateSetting("BING_API_KEY", p.getApiKey(), "Bing Search API Key");
                settingService.updateSetting("BING_SEARCH_ENABLED", String.valueOf(enabled), "Bing search active");
            } else if ("BRAVE".equalsIgnoreCase(p.getProviderId())) {
                settingService.updateSetting("BRAVE_API_KEY", p.getApiKey(), "Brave Search API Key");
                settingService.updateSetting("BRAVE_SEARCH_ENABLED", String.valueOf(enabled), "Brave search active");
            }
        });

        redirectAttributes.addFlashAttribute("successMessage", "Search provider configuration updated successfully.");
        return "redirect:/admin/crawler";
    }

    @PostMapping("/crawler/purge-outdated")
    public String purgeOutdatedCrawls(RedirectAttributes redirectAttributes) {
        try {
            crawlErrorLogRepository.deleteAll();
            redirectAttributes.addFlashAttribute("successMessage", "Cleared crawl error logs and refreshed crawler state.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error clearing logs: " + e.getMessage());
        }
        return "redirect:/admin/crawler";
    }

    @PostMapping("/crawler/errors/clear")
    public String clearErrorLogs(@RequestParam(value = "errorType", required = false) String errorType,
                                 RedirectAttributes redirectAttributes) {
        try {
            if (errorType != null && !errorType.isBlank() && !"ALL".equalsIgnoreCase(errorType)) {
                crawlErrorLogRepository.deleteByErrorType(errorType);
                redirectAttributes.addFlashAttribute("successMessage", "Cleared all error logs of category: " + errorType);
            } else {
                crawlErrorLogRepository.deleteAll();
                redirectAttributes.addFlashAttribute("successMessage", "All crawl safety & error logs cleared.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to clear error logs: " + e.getMessage());
        }
        return "redirect:/admin/crawler";
    }

    @PostMapping("/crawler/errors/{id}/delete")
    public String deleteSingleErrorLog(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            crawlErrorLogRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Error log record #" + id + " deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete error record: " + e.getMessage());
        }
        return "redirect:/admin/crawler";
    }

    @GetMapping("/crawler/errors/{id}/details")
    @ResponseBody
    public ResponseEntity<?> getErrorDetails(@PathVariable("id") Long id) {
        return crawlErrorLogRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/crawler/jobs/clear")
    public String clearCrawlJobs(RedirectAttributes redirectAttributes) {
        try {
            crawlQueueManager.clearAllJobs();
            redirectAttributes.addFlashAttribute("successMessage", "Recent crawl jobs history cleared successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to clear jobs: " + e.getMessage());
        }
        return "redirect:/admin/crawler";
    }
}

