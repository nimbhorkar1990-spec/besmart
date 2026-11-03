package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "duplicate_records", indexes = {
    @Index(name = "idx_dup_hash", columnList = "contentHash"),
    @Index(name = "idx_dup_orig_url", columnList = "originalUrl")
})
public class DuplicateRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1500)
    private String duplicateUrl;

    @Column(nullable = false, length = 1500)
    private String originalUrl;

    @Column(length = 64)
    private String contentHash;

    @Column(nullable = false, length = 50)
    private String duplicateType = "CANONICAL"; // "CANONICAL", "CONTENT_HASH", "NORMALIZED_URL"

    @Column(nullable = false)
    private LocalDateTime detectedAt;

    public DuplicateRecord() {
    }

    public DuplicateRecord(String duplicateUrl, String originalUrl, String contentHash, String duplicateType) {
        this.duplicateUrl = duplicateUrl;
        this.originalUrl = originalUrl;
        this.contentHash = contentHash;
        this.duplicateType = duplicateType != null ? duplicateType : "CANONICAL";
        this.detectedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.detectedAt == null) {
            this.detectedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDuplicateUrl() {
        return duplicateUrl;
    }

    public void setDuplicateUrl(String duplicateUrl) {
        this.duplicateUrl = duplicateUrl;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getDuplicateType() {
        return duplicateType;
    }

    public void setDuplicateType(String duplicateType) {
        this.duplicateType = duplicateType;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(LocalDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }
}

