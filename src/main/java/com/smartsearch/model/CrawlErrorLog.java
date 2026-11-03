package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "crawl_errors", indexes = {
    @Index(name = "idx_ce_domain", columnList = "domain"),
    @Index(name = "idx_ce_type", columnList = "errorType"),
    @Index(name = "idx_ce_occurred", columnList = "occurredAt")
})
public class CrawlErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1500)
    private String url;

    @Column(length = 255)
    private String domain;

    @Column(nullable = false, length = 50)
    private String errorType; // "SSRF_BLOCKED", "ROBOTS_DISALLOWED", "HTTP_STATUS", "TIMEOUT", "DNS_FAILURE", "PARSING_ERROR"

    private Integer httpStatusCode;

    @Column(length = 1000)
    private String errorMessage;

    private Long crawlJobId;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    public CrawlErrorLog() {
    }

    public CrawlErrorLog(String url, String domain, String errorType, Integer httpStatusCode, String errorMessage, Long crawlJobId) {
        this.url = url;
        this.domain = domain;
        this.errorType = errorType != null ? errorType : "UNKNOWN";
        this.httpStatusCode = httpStatusCode;
        this.errorMessage = errorMessage;
        this.crawlJobId = crawlJobId;
        this.occurredAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.occurredAt == null) {
            this.occurredAt = LocalDateTime.now();
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

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getCrawlJobId() {
        return crawlJobId;
    }

    public void setCrawlJobId(Long crawlJobId) {
        this.crawlJobId = crawlJobId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getBadgeClass() {
        if (errorType == null) return "bg-secondary-subtle text-secondary";
        return switch (errorType) {
            case "SSRF_BLOCKED", "PRIVATE_IP_BLOCKED", "RESTRICTED_PORT" -> "bg-danger text-white";
            case "ROBOTS_DISALLOWED" -> "bg-warning-subtle text-warning-emphasis border border-warning";
            case "HTTP_STATUS" -> "bg-danger-subtle text-danger";
            case "TIMEOUT" -> "bg-secondary-subtle text-secondary";
            case "DNS_FAILURE", "SSL_ERROR" -> "bg-info-subtle text-info-emphasis";
            case "DISALLOWED_EXTENSION" -> "bg-dark-subtle text-dark";
            default -> "bg-light text-dark border";
        };
    }
}

