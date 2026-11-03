package com.smartsearch.scheduler;

import com.smartsearch.queue.CrawlQueueManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledCrawlTask {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledCrawlTask.class);

    private final CrawlQueueManager crawlQueueManager;

    public ScheduledCrawlTask(CrawlQueueManager crawlQueueManager) {
        this.crawlQueueManager = crawlQueueManager;
    }

    /**
     * Polls the pending queue every 4 seconds to process discovered web URLs.
     */
    @Scheduled(fixedDelay = 4000)
    public void runQueueWorker() {
        try {
            crawlQueueManager.processNextQueueBatch();
            crawlQueueManager.sweepStaleCrawlJobs();
        } catch (Exception e) {
            logger.warn("Scheduled crawl batch encountered an error: {}", e.getMessage());
        }
    }
}

