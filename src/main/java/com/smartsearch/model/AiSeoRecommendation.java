package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_seo_recommendations")
public class AiSeoRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blog_id", nullable = false)
    private BlogPost blog;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(length = 1000)
    private String reason;

    @Column(length = 500)
    private String expectedBenefit;

    @Column(nullable = false, length = 20)
    private String priority = "MEDIUM"; // "HIGH", "MEDIUM", "LOW"

    @Column(nullable = false, length = 50)
    private String actionType; // "OPTIMIZE_META_TAGS", "GENERATE_SLUG", "INSERT_SCHEMA_MARKUP", "ADD_INTERNAL_LINKS", "GENERATE_ALT_TEXT", "IMPROVE_HEADINGS", "IMPROVE_READABILITY"

    @Column(nullable = false)
    private Boolean automationAvailable = true;

    @Column(nullable = false, length = 30)
    private String approvalStatus = "PENDING"; // "PENDING", "APPROVED", "REJECTED", "EXECUTED"

    @Lob
    @Column(columnDefinition = "TEXT")
    private String proposedChange; // json or diff

    @Lob
    @Column(columnDefinition = "TEXT")
    private String aiExplanation;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime reviewedAt;

    @Column(length = 100)
    private String reviewedBy;

    public AiSeoRecommendation() {
        this.createdAt = LocalDateTime.now();
    }

    public AiSeoRecommendation(BlogPost blog, String title, String description, String reason,
                               String expectedBenefit, String priority, String actionType,
                               Boolean automationAvailable, String proposedChange, String aiExplanation) {
        this.blog = blog;
        this.title = title;
        this.description = description;
        this.reason = reason;
        this.expectedBenefit = expectedBenefit;
        this.priority = priority != null ? priority : "MEDIUM";
        this.actionType = actionType;
        this.automationAvailable = automationAvailable != null ? automationAvailable : true;
        this.proposedChange = proposedChange;
        this.aiExplanation = aiExplanation;
        this.approvalStatus = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BlogPost getBlog() {
        return blog;
    }

    public void setBlog(BlogPost blog) {
        this.blog = blog;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getExpectedBenefit() {
        return expectedBenefit;
    }

    public void setExpectedBenefit(String expectedBenefit) {
        this.expectedBenefit = expectedBenefit;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Boolean getAutomationAvailable() {
        return automationAvailable;
    }

    public void setAutomationAvailable(Boolean automationAvailable) {
        this.automationAvailable = automationAvailable;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getProposedChange() {
        return proposedChange;
    }

    public void setProposedChange(String proposedChange) {
        this.proposedChange = proposedChange;
    }

    public String getAiExplanation() {
        return aiExplanation;
    }

    public void setAiExplanation(String aiExplanation) {
        this.aiExplanation = aiExplanation;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }
}

