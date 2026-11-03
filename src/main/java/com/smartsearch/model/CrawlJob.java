package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "crawl_jobs")
public class CrawlJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String triggerSource = "AUTO_SEARCH"; // "AUTO_SEARCH", "ADMIN_MANUAL", "SCHEDULED_SWEEP"

    @Column(length = 500)
    private String seedQueryOrUrl;

    @Column(nullable = false, length = 50)
    private String status = "RUNNING"; // "RUNNING", "COMPLETED", "FAILED", "PAUSED"

    @Column(nullable = false)
    private Integer pagesDiscovered = 0;

    @Column(nullable = false)
    private Integer pagesCrawled = 0;

    @Column(nullable = false)
    private Integer pagesIndexed = 0;

    @Column(nullable = false)
    private Integer duplicatesSkipped = 0;

    @Column(nullable = false)
    private Integer errorsCount = 0;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Column(length = 1000)
    private String summaryMessage;

    public CrawlJob() {
    }

    public CrawlJob(String triggerSource, String seedQueryOrUrl) {
        this.triggerSource = triggerSource != null ? triggerSource : "AUTO_SEARCH";
        this.seedQueryOrUrl = seedQueryOrUrl;
        this.status = "RUNNING";
        this.startedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
    }

    public void incrementDiscovered() {
        this.pagesDiscovered++;
    }

    public void incrementCrawled() {
        this.pagesCrawled++;
    }

    public void incrementIndexed() {
        this.pagesIndexed++;
    }

    public void incrementDuplicates() {
        this.duplicatesSkipped++;
    }

    public void incrementErrors() {
        this.errorsCount++;
    }

    public void complete(String message) {
        this.status = "COMPLETED";
        this.completedAt = LocalDateTime.now();
        this.summaryMessage = message;
    }

    public void fail(String message) {
        this.status = "FAILED";
        this.completedAt = LocalDateTime.now();
        this.summaryMessage = message;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTriggerSource() {
        return triggerSource;
    }

    public void setTriggerSource(String triggerSource) {
        this.triggerSource = triggerSource;
    }

    public String getSeedQueryOrUrl() {
        return seedQueryOrUrl;
    }

    public void setSeedQueryOrUrl(String seedQueryOrUrl) {
        this.seedQueryOrUrl = seedQueryOrUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPagesDiscovered() {
        return pagesDiscovered;
    }

    public void setPagesDiscovered(Integer pagesDiscovered) {
        this.pagesDiscovered = pagesDiscovered;
    }

    public Integer getPagesCrawled() {
        return pagesCrawled;
    }

    public void setPagesCrawled(Integer pagesCrawled) {
        this.pagesCrawled = pagesCrawled;
    }

    public Integer getPagesIndexed() {
        return pagesIndexed;
    }

    public void setPagesIndexed(Integer pagesIndexed) {
        this.pagesIndexed = pagesIndexed;
    }

    public Integer getDuplicatesSkipped() {
        return duplicatesSkipped;
    }

    public void setDuplicatesSkipped(Integer duplicatesSkipped) {
        this.duplicatesSkipped = duplicatesSkipped;
    }

    public Integer getErrorsCount() {
        return errorsCount;
    }

    public void setErrorsCount(Integer errorsCount) {
        this.errorsCount = errorsCount;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }

    public void setSummaryMessage(String summaryMessage) {
        this.summaryMessage = summaryMessage;
    }
}

