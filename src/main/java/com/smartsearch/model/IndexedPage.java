package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "indexed_pages", indexes = {
    @Index(name = "idx_page_url", columnList = "url"),
    @Index(name = "idx_page_category", columnList = "category"),
    @Index(name = "idx_page_domain", columnList = "domain")
})
public class IndexedPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 1500)
    private String url;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(length = 500)
    private String keywords;

    @Column(nullable = false, length = 50)
    private String category = "ALL";

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 1500)
    private String imageUrl;

    @Column(length = 255)
    private String domain;

    @Column(length = 1500)
    private String canonicalUrl;

    @Column(length = 64)
    private String contentHash;

    @Column(length = 2000)
    private String headings;

    private LocalDateTime publishedAt;

    private Double qualityScore = 50.0;

    @Column(nullable = false, length = 50)
    private String status = "INDEXED"; // INDEXED, PENDING, FAILED

    @Column(nullable = false)
    private LocalDateTime indexedAt;

    @Column
    private LocalDateTime lastCrawledAt;

    public IndexedPage() {
    }

    public IndexedPage(String url, String title, String description, String keywords, String category, String content, String imageUrl, String domain) {
        this.url = url;
        this.title = title;
        this.description = description;
        this.keywords = keywords;
        this.category = (category != null) ? category.toUpperCase() : "ALL";
        this.content = content;
        this.imageUrl = imageUrl;
        this.domain = domain;
        this.status = "INDEXED";
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.indexedAt = now;
        this.lastCrawledAt = now;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(LocalDateTime indexedAt) {
        this.indexedAt = indexedAt;
    }

    public LocalDateTime getLastCrawledAt() {
        return lastCrawledAt;
    }

    public void setLastCrawledAt(LocalDateTime lastCrawledAt) {
        this.lastCrawledAt = lastCrawledAt;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public void setCanonicalUrl(String canonicalUrl) {
        this.canonicalUrl = canonicalUrl;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getHeadings() {
        return headings;
    }

    public void setHeadings(String headings) {
        this.headings = headings;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Double qualityScore) {
        this.qualityScore = qualityScore;
    }
}

