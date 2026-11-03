package com.smartsearch.queue;

import com.smartsearch.crawler.DomainRateLimiter;
import com.smartsearch.crawler.RobotsTxtEvaluator;
import com.smartsearch.crawler.UrlSecurityValidator;
import com.smartsearch.extractor.ExtractedPageData;
import com.smartsearch.extractor.WebContentExtractor;
import com.smartsearch.model.*;
import com.smartsearch.ranking.DuplicateDetector;
import com.smartsearch.repository.*;
import com.smartsearch.service.LuceneService;
import com.smartsearch.service.SystemSettingService;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CrawlQueueManager {

    private static final Logger logger = LoggerFactory.getLogger(CrawlQueueManager.class);

    private final DiscoveredUrlRepository discoveredUrlRepository;
    private final CrawledPageRepository crawledPageRepository;
    private final IndexedPageRepository indexedPageRepository;
    private final CrawlJobRepository crawlJobRepository;
    private final CrawlQueueItemRepository crawlQueueItemRepository;
    private final CrawlErrorLogRepository crawlErrorLogRepository;
    private final LuceneService luceneService;
    private final SystemSettingService settingService;
    private final UrlSecurityValidator securityValidator;
    private final RobotsTxtEvaluator robotsTxtEvaluator;
    private final DomainRateLimiter rateLimiter;
    private final WebContentExtractor contentExtractor;
    private final DuplicateDetector duplicateDetector;

    // In-memory queues
    private final BlockingQueue<CrawlQueueItem> pendingQueue = new LinkedBlockingQueue<>(5000);
    private final ConcurrentLinkedQueue<String> activeCrawlingQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ExtractedPageData> extractionQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<IndexedPage> indexingQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> failedQueue = new ConcurrentLinkedQueue<>();

    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> domainPageCount = new ConcurrentHashMap<>();

    private final AtomicBoolean isCrawlerPaused = new AtomicBoolean(false);
    private final ExecutorService workerPool;

    public CrawlQueueManager(DiscoveredUrlRepository discoveredUrlRepository,
                             CrawledPageRepository crawledPageRepository,
                             IndexedPageRepository indexedPageRepository,
                             CrawlJobRepository crawlJobRepository,
                             CrawlQueueItemRepository crawlQueueItemRepository,
                             CrawlErrorLogRepository crawlErrorLogRepository,
                             LuceneService luceneService,
                             SystemSettingService settingService,
                             UrlSecurityValidator securityValidator,
                             RobotsTxtEvaluator robotsTxtEvaluator,
                             DomainRateLimiter rateLimiter,
                             WebContentExtractor contentExtractor,
                             DuplicateDetector duplicateDetector) {
        this.discoveredUrlRepository = discoveredUrlRepository;
        this.crawledPageRepository = crawledPageRepository;
        this.indexedPageRepository = indexedPageRepository;
        this.crawlJobRepository = crawlJobRepository;
        this.crawlQueueItemRepository = crawlQueueItemRepository;
        this.crawlErrorLogRepository = crawlErrorLogRepository;
        this.luceneService = luceneService;
        this.settingService = settingService;
        this.securityValidator = securityValidator;
        this.robotsTxtEvaluator = robotsTxtEvaluator;
        this.rateLimiter = rateLimiter;
        this.contentExtractor = contentExtractor;
        this.duplicateDetector = duplicateDetector;

        int threads = Math.max(3, Runtime.getRuntime().availableProcessors());
        this.workerPool = Executors.newFixedThreadPool(threads);
    }

    /**
     * Enqueues discovered URLs from search providers or crawler links.
     */
    public void enqueueDiscoveredUrls(List<String> rawUrls, String sourceQuery, String sourceProvider, Long crawlJobId) {
        if (rawUrls == null || rawUrls.isEmpty()) return;

        int enqueuedCount = 0;
        for (String rawUrl : rawUrls) {
            if (rawUrl == null || rawUrl.isBlank()) continue;
            String normalizedUrl = securityValidator.normalizeUrl(rawUrl);

            UrlSecurityValidator.ValidationResult valResult = securityValidator.validate(normalizedUrl);
            if (!valResult.isSafe()) {
                if (!"DISALLOWED_EXTENSION".equals(valResult.getErrorType())) {
                    logCrawlError(normalizedUrl, valResult.getErrorType(), 400, valResult.getReason(), crawlJobId);
                }
                continue;
            }

            String domain = securityValidator.extractDomain(normalizedUrl);

            // Record discovered URL
            if (!discoveredUrlRepository.existsByUrl(normalizedUrl)) {
                DiscoveredUrl disc = new DiscoveredUrl(normalizedUrl, domain, sourceQuery, sourceProvider, 0);
                discoveredUrlRepository.save(disc);
            }

            if (visitedUrls.contains(normalizedUrl)) {
                continue;
            }

            // Create queue item
            CrawlQueueItem queueItem = new CrawlQueueItem(normalizedUrl, domain, 0, 5, crawlJobId);
            crawlQueueItemRepository.save(queueItem);

            pendingQueue.offer(queueItem);
            enqueuedCount++;
        }

        // If no new items could be queued (e.g. all already visited or filtered), check if job can finalize
        if (enqueuedCount == 0 && crawlJobId != null) {
            checkAndUpdateJobCompletionById(crawlJobId);
        }
    }

    /**
     * Processes pending items in the queue (invoked by background workers or scheduled sweeps).
     */
    public void processNextQueueBatch() {
        if (isCrawlerPaused.get()) {
            return;
        }

        int maxPerBatch = 6;
        for (int i = 0; i < maxPerBatch; i++) {
            CrawlQueueItem item = pendingQueue.poll();
            if (item == null) break;

            workerPool.submit(() -> crawlAndProcessItem(item));
        }
    }

    private void crawlAndProcessItem(CrawlQueueItem item) {
        String url = item.getUrl();
        String domain = item.getDomain();
        Long jobId = item.getCrawlJobId();

        if (visitedUrls.contains(url)) {
            item.setStatus("SKIPPED_VISITED");
            try {
                crawlQueueItemRepository.save(item);
            } catch (Exception ignored) {}
            checkAndUpdateJobCompletionById(jobId);
            return;
        }

        // Re-verify crawl safety right before outbound connection
        UrlSecurityValidator.ValidationResult valResult = securityValidator.validate(url);
        if (!valResult.isSafe()) {
            logger.warn("Pre-crawl safety guard blocked URL: {} (Reason: {})", url, valResult.getReason());
            if (!"DISALLOWED_EXTENSION".equals(valResult.getErrorType())) {
                logCrawlError(url, valResult.getErrorType(), 400, valResult.getReason(), jobId);
            }
            item.setStatus("BLOCKED_SAFETY");
            item.setLastError(valResult.getReason());
            try {
                crawlQueueItemRepository.save(item);
            } catch (Exception ignored) {}
            checkAndUpdateJobCompletionById(jobId);
            return;
        }

        // Domain rate check (scoped per crawl job to allow multiple distinct topics to crawl domain)
        int maxPagesPerDomain = settingService.getIntSetting("CRAWLER_MAX_PAGES_PER_DOMAIN", 10);
        String domainKey = (jobId != null ? jobId : 0) + ":" + domain;
        int currentCount = domainPageCount.getOrDefault(domainKey, 0);
        if (currentCount >= maxPagesPerDomain) {
            logger.debug("Domain limit reached for job {} on {}: {} pages", jobId, domain, currentCount);
            item.setStatus("SKIPPED_DOMAIN_LIMIT");
            try {
                crawlQueueItemRepository.save(item);
            } catch (Exception ignored) {}
            checkAndUpdateJobCompletionById(jobId);
            return;
        }

        // Robots.txt check
        if (!robotsTxtEvaluator.isAllowed(url)) {
            logCrawlError(url, "ROBOTS_DISALLOWED", 403, "Disallowed by website robots.txt policy", jobId);
            item.setStatus("DISALLOWED_ROBOTS");
            item.setLastError("Blocked by robots.txt");
            try {
                crawlQueueItemRepository.save(item);
            } catch (Exception ignored) {}
            checkAndUpdateJobCompletionById(jobId);
            return;
        }

        // Rate limiting
        int crawlDelay = robotsTxtEvaluator.getCrawlDelaySeconds(url);
        rateLimiter.awaitPoliteness(domain, crawlDelay);

        activeCrawlingQueue.add(url);
        item.setStatus("PROCESSING");
        try {
            crawlQueueItemRepository.save(item);
        } catch (Exception ignored) {
        }

        long start = System.currentTimeMillis();
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("SmartSearchBot/1.0 (+http://localhost:8080/bot; indexing@smartsearch.local)")
                    .timeout(6000)
                    .maxBodySize(1024 * 1024) // 1MB limit
                    .followRedirects(true)
                    .execute();

            int statusCode = response.statusCode();
            long responseTime = System.currentTimeMillis() - start;

            if (statusCode >= 200 && statusCode < 300) {
                // Read body bytes BEFORE parsing (Jsoup nulls out byteData upon parse())
                long contentSizeBytes = 0L;
                try {
                    byte[] bytes = response.bodyAsBytes();
                    if (bytes != null) {
                        contentSizeBytes = bytes.length;
                    }
                } catch (Exception ignored) {
                }

                Document doc = response.parse();
                visitedUrls.add(url);
                domainPageCount.put(domainKey, currentCount + 1);

                // Content Extraction Queue
                ExtractedPageData extracted = contentExtractor.extract(doc, url);
                extractionQueue.add(extracted);

                // Save CrawledPage record
                CrawledPage crawled = new CrawledPage(
                        url, domain, extracted.getCanonicalUrl(), extracted.getTitle(),
                        statusCode, responseTime, contentSizeBytes,
                        extracted.getContentHash(), response.contentType()
                );
                crawledPageRepository.save(crawled);

                // Check Duplicates
                boolean isDuplicate = duplicateDetector.isDuplicateAndRecord(extracted);

                if (!isDuplicate) {
                    // Indexing Queue & Database store
                    saveAndIndexPage(extracted, jobId);
                }

                // If depth allows, discover internal links
                int maxDepth = settingService.getIntSetting("CRAWLER_MAX_DEPTH", 2);
                if (item.getDepth() < maxDepth && extracted.getDiscoveredInternalLinks() != null) {
                    enqueueInternalLinks(extracted.getDiscoveredInternalLinks(), item.getDepth() + 1, jobId);
                }

                item.setStatus("COMPLETED");
                item.setProcessedAt(LocalDateTime.now());
                crawlQueueItemRepository.save(item);

                // Update job counters & evaluate completion
                if (jobId != null) {
                    crawlJobRepository.findById(jobId).ifPresent(job -> {
                        job.incrementCrawled();
                        if (!isDuplicate) job.incrementIndexed();
                        else job.incrementDuplicates();
                        crawlJobRepository.save(job);
                        checkAndUpdateJobCompletion(job);
                    });
                }
            } else {
                logCrawlError(url, "HTTP_STATUS", statusCode, "HTTP status " + statusCode, jobId);
                item.setStatus("FAILED");
                item.setLastError("HTTP " + statusCode);
                crawlQueueItemRepository.save(item);
                failedQueue.add(url);
                checkAndUpdateJobCompletionById(jobId);
            }
        } catch (HttpStatusException httpEx) {
            int statusCode = httpEx.getStatusCode();
            logger.warn("HTTP error {} fetching {}: {}", statusCode, url, httpEx.getMessage());
            logCrawlError(url, "HTTP_STATUS", statusCode, "HTTP Status " + statusCode + ": " + httpEx.getMessage(), jobId);
            item.setStatus("FAILED");
            item.setLastError("HTTP " + statusCode);
            crawlQueueItemRepository.save(item);
            failedQueue.add(url);
            checkAndUpdateJobCompletionById(jobId);
        } catch (SocketTimeoutException timeoutEx) {
            logger.warn("Timeout crawling {}: {}", url, timeoutEx.getMessage());
            logCrawlError(url, "TIMEOUT", 408, "Connection or read timed out (6s threshold): " + timeoutEx.getMessage(), jobId);
            item.setStatus("FAILED");
            item.setLastError("Timeout 408");
            crawlQueueItemRepository.save(item);
            failedQueue.add(url);
            checkAndUpdateJobCompletionById(jobId);
        } catch (UnknownHostException uhe) {
            logger.warn("DNS resolution failure for {}: {}", url, uhe.getMessage());
            logCrawlError(url, "DNS_FAILURE", 0, "Host DNS could not be resolved: " + uhe.getMessage(), jobId);
            item.setStatus("FAILED");
            item.setLastError("DNS failure");
            crawlQueueItemRepository.save(item);
            failedQueue.add(url);
            checkAndUpdateJobCompletionById(jobId);
        } catch (SSLException sslEx) {
            logger.warn("SSL/TLS handshake failure for {}: {}", url, sslEx.getMessage());
            logCrawlError(url, "SSL_ERROR", 0, "SSL certificate or TLS handshake failure: " + sslEx.getMessage(), jobId);
            item.setStatus("FAILED");
            item.setLastError("SSL failure");
            crawlQueueItemRepository.save(item);
            failedQueue.add(url);
            checkAndUpdateJobCompletionById(jobId);
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - start;
            logger.warn("Crawler worker exception on {}: {}", url, e.getMessage(), e);
            logCrawlError(url, "PARSING_ERROR", 0, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), jobId);
            item.setStatus("FAILED");
            item.setLastError(e.getMessage());
            item.setRetryCount(item.getRetryCount() + 1);
            crawlQueueItemRepository.save(item);
            failedQueue.add(url);
            checkAndUpdateJobCompletionById(jobId);
        } finally {
            activeCrawlingQueue.remove(url);
        }
    }

    private void saveAndIndexPage(ExtractedPageData data, Long jobId) {
        String url = data.getUrl();
        Optional<IndexedPage> existingOpt = indexedPageRepository.findByUrl(url);

        IndexedPage page = existingOpt.orElseGet(IndexedPage::new);
        page.setUrl(url);
        page.setTitle(data.getTitle());
        page.setDescription(data.getDescription());
        page.setKeywords(data.getKeywords());
        page.setContent(data.getContent());
        page.setImageUrl(data.getImageUrl());
        page.setDomain(data.getDomain());
        page.setCanonicalUrl(data.getCanonicalUrl());
        page.setContentHash(data.getContentHash());
        page.setHeadings(data.getHeadings());
        page.setPublishedAt(data.getPublishedDate());
        page.setQualityScore(data.getQualityScore());
        page.setCategory("ALL");
        page.setStatus("INDEXED");
        page.setLastCrawledAt(LocalDateTime.now());

        IndexedPage saved = indexedPageRepository.save(page);
        indexingQueue.add(saved);

        // Index in Apache Lucene
        try {
            luceneService.indexDocument(saved);
        } catch (Exception e) {
            logger.error("Failed to index page {} into Lucene: {}", url, e.getMessage());
        }
    }

    private void enqueueInternalLinks(List<String> links, int depth, Long jobId) {
        for (String link : links) {
            if (visitedUrls.contains(link)) continue;
            UrlSecurityValidator.ValidationResult valResult = securityValidator.validate(link);
            if (!valResult.isSafe()) {
                if ("DISALLOWED_EXTENSION".equals(valResult.getErrorType())) {
                    continue; // Skip static media/asset links quietly without treating as crawl errors
                }
                logCrawlError(link, valResult.getErrorType(), 400, valResult.getReason(), jobId);
                continue;
            }

            String domain = securityValidator.extractDomain(link);
            CrawlQueueItem item = new CrawlQueueItem(link, domain, depth, 6, jobId);
            try {
                crawlQueueItemRepository.save(item);
                if (jobId != null) {
                    crawlJobRepository.findById(jobId).ifPresent(job -> {
                        job.incrementDiscovered();
                        crawlJobRepository.save(job);
                    });
                }
            } catch (Exception ignored) {
            }
            pendingQueue.offer(item);
        }
    }

    private void logCrawlError(String url, String type, Integer status, String msg, Long jobId) {
        try {
            String domain = securityValidator.extractDomain(url);
            String truncated = (msg != null && msg.length() > 950) ? msg.substring(0, 950) : msg;
            CrawlErrorLog log = new CrawlErrorLog(url, domain, type, status, truncated, jobId);
            crawlErrorLogRepository.save(log);

            if (jobId != null) {
                crawlJobRepository.findById(jobId).ifPresent(job -> {
                    job.incrementErrors();
                    crawlJobRepository.save(job);
                    checkAndUpdateJobCompletion(job);
                });
            }
        } catch (Exception ignored) {
        }
    }

    public void checkAndUpdateJobCompletionById(Long jobId) {
        if (jobId == null) return;
        crawlJobRepository.findById(jobId).ifPresent(this::checkAndUpdateJobCompletion);
    }

    public void checkAndUpdateJobCompletion(CrawlJob job) {
        if (job == null || !"RUNNING".equals(job.getStatus())) return;
        Long jobId = job.getId();
        if (jobId == null) return;

        try {
            long remaining = crawlQueueItemRepository.countByCrawlJobIdAndStatusIn(jobId, List.of("WAITING", "PROCESSING"));
            if (remaining == 0) {
                if (job.getPagesCrawled() > 0) {
                    job.complete("Completed: " + job.getPagesCrawled() + " crawled, " + job.getPagesIndexed() + " indexed.");
                } else if (job.getErrorsCount() > 0) {
                    job.fail("Failed: Encountered " + job.getErrorsCount() + " network or policy errors.");
                } else {
                    job.complete("Completed: All discovered URLs already indexed or skipped.");
                }
                crawlJobRepository.save(job);
                logger.info("CrawlJob #{} status updated to: {}", jobId, job.getStatus());
            }
        } catch (Exception e) {
            logger.debug("Error checking job completion for #{}: {}", jobId, e.getMessage());
        }
    }

    public void sweepStaleCrawlJobs() {
        try {
            List<CrawlJob> runningJobs = crawlJobRepository.findByStatus("RUNNING");
            for (CrawlJob job : runningJobs) {
                Long jobId = job.getId();
                long remaining = crawlQueueItemRepository.countByCrawlJobIdAndStatusIn(jobId, List.of("WAITING", "PROCESSING"));
                if (remaining == 0) {
                    checkAndUpdateJobCompletion(job);
                } else if (job.getStartedAt() != null && job.getStartedAt().isBefore(LocalDateTime.now().minusSeconds(45))) {
                    job.complete("Completed: Threshold reached (" + job.getPagesCrawled() + " crawled).");
                    crawlJobRepository.save(job);
                    logger.info("Sweep: Auto-finalized crawl job #{} after timeout.", jobId);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void clearAllJobs() {
        try {
            crawlQueueItemRepository.deleteAll();
            crawlJobRepository.deleteAll();
            domainPageCount.clear();
            visitedUrls.clear();
            pendingQueue.clear();
            activeCrawlingQueue.clear();
            extractionQueue.clear();
            indexingQueue.clear();
            failedQueue.clear();
        } catch (Exception e) {
            logger.warn("Error clearing all crawl jobs: {}", e.getMessage());
        }
    }

    public void setPaused(boolean paused) {
        isCrawlerPaused.set(paused);
    }

    public boolean isPaused() {
        return isCrawlerPaused.get();
    }

    public void retryAllFailed() {
        List<CrawlQueueItem> failed = crawlQueueItemRepository.findTop20ByStatusOrderByPriorityAscScheduledAtAsc("FAILED");
        for (CrawlQueueItem item : failed) {
            item.setStatus("WAITING");
            item.setScheduledAt(LocalDateTime.now());
            crawlQueueItemRepository.save(item);
            pendingQueue.offer(item);
        }
    }

    public Map<String, Object> getQueueMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("pendingQueueSize", pendingQueue.size());
        metrics.put("activeCrawlingCount", activeCrawlingQueue.size());
        metrics.put("extractionQueueSize", extractionQueue.size());
        metrics.put("indexingQueueSize", indexingQueue.size());
        metrics.put("failedCount", failedQueue.size());
        metrics.put("isPaused", isCrawlerPaused.get());
        metrics.put("totalDiscovered", discoveredUrlRepository.count());
        metrics.put("totalCrawled", crawledPageRepository.count());
        metrics.put("totalIndexed", indexedPageRepository.count());
        metrics.put("totalDuplicates", duplicateDetector != null ? 0 : 0);
        return metrics;
    }
}

