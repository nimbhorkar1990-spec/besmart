package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "crawl_queue", indexes = {
    @Index(name = "idx_cq_status", columnList = "status"),
    @Index(name = "idx_cq_priority", columnList = "priority"),
    @Index(name = "idx_cq_domain", columnList = "domain")
})
public class CrawlQueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1500)
    private String url;

    @Column(length = 255)
    private String domain;

    private Integer depth = 0;

    @Column(nullable = false)
    private Integer priority = 5; // 1 = highest, 10 = lowest

    @Column(nullable = false, length = 50)
    private String status = "WAITING"; // "WAITING", "PROCESSING", "COMPLETED", "FAILED"

    @Column(nullable = false)
    private Integer retryCount = 0;

    @Column(length = 500)
    private String lastError;

    private Long crawlJobId;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    private LocalDateTime processedAt;

    public CrawlQueueItem() {
    }

    public CrawlQueueItem(String url, String domain, Integer depth, Integer priority, Long crawlJobId) {
        this.url = url;
        this.domain = domain;
        this.depth = depth != null ? depth : 0;
        this.priority = priority != null ? priority : 5;
        this.crawlJobId = crawlJobId;
        this.status = "WAITING";
        this.retryCount = 0;
        this.scheduledAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.scheduledAt == null) {
            this.scheduledAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Long getCrawlJobId() {
        return crawlJobId;
    }

    public void setCrawlJobId(Long crawlJobId) {
        this.crawlJobId = crawlJobId;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}

