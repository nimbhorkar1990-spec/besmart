package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "blog_posts")
public class BlogPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, unique = true, length = 300)
    private String slug;

    @Column(length = 1000)
    private String summary;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 100)
    private String category = "General";

    @Column(length = 255)
    private String tags;

    @Column(length = 500)
    private String coverImageUrl;

    @Column(length = 255)
    private String coverImageAlt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(length = 100)
    private String authorName;

    @Column(nullable = false, length = 50)
    private String authorRole = "USER"; // "ADMIN", "AGENT", "PROPERTY_OWNER", "USER", "AI"

    @Column(nullable = false)
    private Boolean isAiGenerated = false;

    @Column(nullable = false, length = 50)
    private String status = "DRAFT"; // "DRAFT", "PENDING_APPROVAL", "PUBLISHED", "EXPIRED", "UNPUBLISHED"

    @Column(nullable = false)
    private Integer validityDays = 90;

    private LocalDateTime publishedAt;

    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Integer renewalCount = 0;

    @Column(nullable = false)
    private Long viewsCount = 0L;

    @Column(nullable = false)
    private Long organicClicks = 0L;

    @Column(nullable = false)
    private Long organicImpressions = 0L;

    // --- SEO Metadata Fields ---

    @Column(length = 100)
    private String metaTitle;

    @Column(length = 300)
    private String metaDescription;

    @Column(length = 100)
    private String primaryKeyword;

    @Column(length = 255)
    private String secondaryKeywords;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String schemaMarkup;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String internalLinksJson;

    @Column(nullable = false)
    private Integer seoScore = 0; // 0 - 100

    @Column(nullable = false)
    private Integer readabilityScore = 0; // 0 - 100

    @Column(length = 50)
    private String googleIndexingStatus = "DISCOVERED"; // "INDEXED", "CRAWLED_PENDING", "DISCOVERED", "UNINDEXED"

    private LocalDateTime lastSeoAnalysisAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public BlogPost() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        if ("EXPIRED".equalsIgnoreCase(this.status)) return true;
        if (this.expiresAt != null && LocalDateTime.now().isAfter(this.expiresAt)) {
            return true;
        }
        return false;
    }

    public boolean isLive() {
        return "PUBLISHED".equalsIgnoreCase(this.status) && !isExpired();
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getCoverImageAlt() {
        return coverImageAlt;
    }

    public void setCoverImageAlt(String coverImageAlt) {
        this.coverImageAlt = coverImageAlt;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorRole() {
        return authorRole;
    }

    public void setAuthorRole(String authorRole) {
        this.authorRole = authorRole;
    }

    public Boolean getIsAiGenerated() {
        return isAiGenerated;
    }

    public void setIsAiGenerated(Boolean aiGenerated) {
        isAiGenerated = aiGenerated;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public void setValidityDays(Integer validityDays) {
        this.validityDays = validityDays;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Integer getRenewalCount() {
        return renewalCount;
    }

    public void setRenewalCount(Integer renewalCount) {
        this.renewalCount = renewalCount;
    }

    public Long getViewsCount() {
        return viewsCount;
    }

    public void setViewsCount(Long viewsCount) {
        this.viewsCount = viewsCount;
    }

    public Long getOrganicClicks() {
        return organicClicks;
    }

    public void setOrganicClicks(Long organicClicks) {
        this.organicClicks = organicClicks;
    }

    public Long getOrganicImpressions() {
        return organicImpressions;
    }

    public void setOrganicImpressions(Long organicImpressions) {
        this.organicImpressions = organicImpressions;
    }

    public String getMetaTitle() {
        return metaTitle;
    }

    public void setMetaTitle(String metaTitle) {
        this.metaTitle = metaTitle;
    }

    public String getMetaDescription() {
        return metaDescription;
    }

    public void setMetaDescription(String metaDescription) {
        this.metaDescription = metaDescription;
    }

    public String getPrimaryKeyword() {
        return primaryKeyword;
    }

    public void setPrimaryKeyword(String primaryKeyword) {
        this.primaryKeyword = primaryKeyword;
    }

    public String getSecondaryKeywords() {
        return secondaryKeywords;
    }

    public void setSecondaryKeywords(String secondaryKeywords) {
        this.secondaryKeywords = secondaryKeywords;
    }

    public String getSchemaMarkup() {
        return schemaMarkup;
    }

    public void setSchemaMarkup(String schemaMarkup) {
        this.schemaMarkup = schemaMarkup;
    }

    public String getInternalLinksJson() {
        return internalLinksJson;
    }

    public void setInternalLinksJson(String internalLinksJson) {
        this.internalLinksJson = internalLinksJson;
    }

    public Integer getSeoScore() {
        return seoScore;
    }

    public void setSeoScore(Integer seoScore) {
        this.seoScore = seoScore;
    }

    public Integer getReadabilityScore() {
        return readabilityScore;
    }

    public void setReadabilityScore(Integer readabilityScore) {
        this.readabilityScore = readabilityScore;
    }

    public String getGoogleIndexingStatus() {
        return googleIndexingStatus;
    }

    public void setGoogleIndexingStatus(String googleIndexingStatus) {
        this.googleIndexingStatus = googleIndexingStatus;
    }

    public LocalDateTime getLastSeoAnalysisAt() {
        return lastSeoAnalysisAt;
    }

    public void setLastSeoAnalysisAt(LocalDateTime lastSeoAnalysisAt) {
        this.lastSeoAnalysisAt = lastSeoAnalysisAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
