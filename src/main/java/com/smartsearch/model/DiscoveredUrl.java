package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "discovered_urls", indexes = {
    @Index(name = "idx_disc_url", columnList = "url"),
    @Index(name = "idx_disc_status", columnList = "status"),
    @Index(name = "idx_disc_domain", columnList = "domain")
})
public class DiscoveredUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1500)
    private String url;

    @Column(length = 255)
    private String domain;

    @Column(length = 255)
    private String sourceQuery;

    @Column(length = 50)
    private String sourceProvider; // "GOOGLE", "BING", "BRAVE", "WIKIPEDIA", "INTERNAL_CRAWL"

    @Column(nullable = false, length = 50)
    private String status = "PENDING"; // "PENDING", "QUEUED", "CRAWLED", "FAILED", "DUPLICATE", "SKIPPED"

    @Column(length = 500)
    private String statusReason;

    private Integer depth = 0;

    @Column(nullable = false)
    private LocalDateTime discoveredAt;

    private LocalDateTime processedAt;

    public DiscoveredUrl() {
    }

    public DiscoveredUrl(String url, String domain, String sourceQuery, String sourceProvider, Integer depth) {
        this.url = url;
        this.domain = domain;
        this.sourceQuery = sourceQuery;
        this.sourceProvider = sourceProvider;
        this.depth = depth != null ? depth : 0;
        this.status = "PENDING";
        this.discoveredAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.discoveredAt == null) {
            this.discoveredAt = LocalDateTime.now();
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

    public String getSourceQuery() {
        return sourceQuery;
    }

    public void setSourceQuery(String sourceQuery) {
        this.sourceQuery = sourceQuery;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public void setSourceProvider(String sourceProvider) {
        this.sourceProvider = sourceProvider;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public LocalDateTime getDiscoveredAt() {
        return discoveredAt;
    }

    public void setDiscoveredAt(LocalDateTime discoveredAt) {
        this.discoveredAt = discoveredAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}

