package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "crawled_pages", indexes = {
    @Index(name = "idx_cp_url", columnList = "url"),
    @Index(name = "idx_cp_domain", columnList = "domain"),
    @Index(name = "idx_cp_crawled_at", columnList = "crawledAt")
})
public class CrawledPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1500)
    private String url;

    @Column(length = 255)
    private String domain;

    @Column(length = 1500)
    private String canonicalUrl;

    @Column(length = 500)
    private String title;

    private Integer statusCode = 200;

    private Long responseTimeMs = 0L;

    private Long contentSizeBytes = 0L;

    @Column(length = 64)
    private String contentHash; // SHA-256 fingerprint

    @Column(length = 100)
    private String contentType;

    @Column(nullable = false)
    private LocalDateTime crawledAt;

    public CrawledPage() {
    }

    public CrawledPage(String url, String domain, String canonicalUrl, String title, Integer statusCode, Long responseTimeMs, Long contentSizeBytes, String contentHash, String contentType) {
        this.url = url;
        this.domain = domain;
        this.canonicalUrl = canonicalUrl;
        this.title = title;
        this.statusCode = statusCode != null ? statusCode : 200;
        this.responseTimeMs = responseTimeMs != null ? responseTimeMs : 0L;
        this.contentSizeBytes = contentSizeBytes != null ? contentSizeBytes : 0L;
        this.contentHash = contentHash;
        this.contentType = contentType;
        this.crawledAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.crawledAt == null) {
            this.crawledAt = LocalDateTime.now();
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

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public void setCanonicalUrl(String canonicalUrl) {
        this.canonicalUrl = canonicalUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public Long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public Long getContentSizeBytes() {
        return contentSizeBytes;
    }

    public void setContentSizeBytes(Long contentSizeBytes) {
        this.contentSizeBytes = contentSizeBytes;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public LocalDateTime getCrawledAt() {
        return crawledAt;
    }

    public void setCrawledAt(LocalDateTime crawledAt) {
        this.crawledAt = crawledAt;
    }
}

