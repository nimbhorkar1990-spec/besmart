package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "blog_publishing_permissions")
public class BlogPublishingPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Section B: Enable / Disable Blog Creation Permissions ---
    @Column(nullable = false)
    private Boolean allowAllUsersCreate = true;

    @Column(nullable = false)
    private Boolean allowAgentsCreate = true;

    @Column(nullable = false)
    private Boolean allowPropertyOwnersCreate = true;

    @Column(nullable = false)
    private Boolean allowAdminsCreate = true;

    @Column(nullable = false)
    private Boolean allowAiGenerate = true;

    @Column(nullable = false)
    private Boolean allowAiAutoPublish = false;

    @Column(nullable = false)
    private Boolean requireAdminApproval = false;

    // --- Section D: Default Blog Validity Settings ---
    @Column(nullable = false)
    private Integer defaultValidityDays = 90; // 30, 60, 90, 180, 365

    @Column(nullable = false)
    private Boolean allowCustomValidity = true;

    @Column(nullable = false)
    private Boolean autoExpireAndUnpublish = true;

    @Column(nullable = false)
    private Boolean allowBlogRenewal = true;

    // --- Section G: SEO Automation Permission Management ---
    @Column(nullable = false)
    private Boolean automaticSeoMasterSwitch = true;

    @Column(nullable = false)
    private Boolean autoMetaTitleOpt = true;

    @Column(nullable = false)
    private Boolean autoMetaDescOpt = true;

    @Column(nullable = false)
    private Boolean autoKeywordSuggestions = true;

    @Column(nullable = false)
    private Boolean autoInternalLinking = true;

    @Column(nullable = false)
    private Boolean autoImageAltText = true;

    @Column(nullable = false)
    private Boolean autoSchemaMarkup = true;

    @Column(nullable = false)
    private Boolean autoSitemapUpdate = true;

    @Column(nullable = false)
    private Boolean autoSeoContentAnalysis = true;

    @Column(nullable = false)
    private Boolean autoIndexingSubmission = false;

    @Column(nullable = false)
    private Boolean autoSeoMonitoring = true;

    // --- Task Two: Section E Assistant Automation & Control Permissions ---
    @Column(nullable = false)
    private Boolean allowAiImageGeneration = true;

    @Column(nullable = false)
    private Boolean allowAiVideoIntegration = true;

    @Column(nullable = false)
    private Boolean allowAiKeywordResearch = true;

    @Column(nullable = false)
    private Boolean allowAiInternalLinking = true;

    @Column(nullable = false)
    private Boolean allowAiBacklinkRecommendations = true;

    @Column(nullable = false)
    private Boolean allowAiScheduledGeneration = true;

    private LocalDateTime updatedAt;

    public BlogPublishingPermission() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    @PrePersist
    protected void onSave() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean canRoleCreate(String role) {
        if (role == null) return allowAllUsersCreate;
        String r = role.toUpperCase();
        if (r.contains("ADMIN")) return allowAdminsCreate;
        if (r.contains("AGENT") || r.contains("EMPLOYER")) return allowAgentsCreate;
        if (r.contains("OWNER") || r.contains("PROPERTY")) return allowPropertyOwnersCreate;
        if (r.contains("AI")) return allowAiGenerate;
        return allowAllUsersCreate;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getAllowAllUsersCreate() {
        return allowAllUsersCreate;
    }

    public void setAllowAllUsersCreate(Boolean allowAllUsersCreate) {
        this.allowAllUsersCreate = allowAllUsersCreate;
    }

    public Boolean getAllowAgentsCreate() {
        return allowAgentsCreate;
    }

    public void setAllowAgentsCreate(Boolean allowAgentsCreate) {
        this.allowAgentsCreate = allowAgentsCreate;
    }

    public Boolean getAllowPropertyOwnersCreate() {
        return allowPropertyOwnersCreate;
    }

    public void setAllowPropertyOwnersCreate(Boolean allowPropertyOwnersCreate) {
        this.allowPropertyOwnersCreate = allowPropertyOwnersCreate;
    }

    public Boolean getAllowAdminsCreate() {
        return allowAdminsCreate;
    }

    public void setAllowAdminsCreate(Boolean allowAdminsCreate) {
        this.allowAdminsCreate = allowAdminsCreate;
    }

    public Boolean getAllowAiGenerate() {
        return allowAiGenerate;
    }

    public void setAllowAiGenerate(Boolean allowAiGenerate) {
        this.allowAiGenerate = allowAiGenerate;
    }

    public Boolean getAllowAiAutoPublish() {
        return allowAiAutoPublish;
    }

    public void setAllowAiAutoPublish(Boolean allowAiAutoPublish) {
        this.allowAiAutoPublish = allowAiAutoPublish;
    }

    public Boolean getRequireAdminApproval() {
        return requireAdminApproval;
    }

    public void setRequireAdminApproval(Boolean requireAdminApproval) {
        this.requireAdminApproval = requireAdminApproval;
    }

    public Integer getDefaultValidityDays() {
        return defaultValidityDays;
    }

    public void setDefaultValidityDays(Integer defaultValidityDays) {
        this.defaultValidityDays = defaultValidityDays;
    }

    public Boolean getAllowCustomValidity() {
        return allowCustomValidity;
    }

    public void setAllowCustomValidity(Boolean allowCustomValidity) {
        this.allowCustomValidity = allowCustomValidity;
    }

    public Boolean getAutoExpireAndUnpublish() {
        return autoExpireAndUnpublish;
    }

    public void setAutoExpireAndUnpublish(Boolean autoExpireAndUnpublish) {
        this.autoExpireAndUnpublish = autoExpireAndUnpublish;
    }

    public Boolean getAllowBlogRenewal() {
        return allowBlogRenewal;
    }

    public void setAllowBlogRenewal(Boolean allowBlogRenewal) {
        this.allowBlogRenewal = allowBlogRenewal;
    }

    public Boolean getAutomaticSeoMasterSwitch() {
        return automaticSeoMasterSwitch;
    }

    public void setAutomaticSeoMasterSwitch(Boolean automaticSeoMasterSwitch) {
        this.automaticSeoMasterSwitch = automaticSeoMasterSwitch;
    }

    public Boolean getAutoMetaTitleOpt() {
        return autoMetaTitleOpt;
    }

    public void setAutoMetaTitleOpt(Boolean autoMetaTitleOpt) {
        this.autoMetaTitleOpt = autoMetaTitleOpt;
    }

    public Boolean getAutoMetaDescOpt() {
        return autoMetaDescOpt;
    }

    public void setAutoMetaDescOpt(Boolean autoMetaDescOpt) {
        this.autoMetaDescOpt = autoMetaDescOpt;
    }

    public Boolean getAutoKeywordSuggestions() {
        return autoKeywordSuggestions;
    }

    public void setAutoKeywordSuggestions(Boolean autoKeywordSuggestions) {
        this.autoKeywordSuggestions = autoKeywordSuggestions;
    }

    public Boolean getAutoInternalLinking() {
        return autoInternalLinking;
    }

    public void setAutoInternalLinking(Boolean autoInternalLinking) {
        this.autoInternalLinking = autoInternalLinking;
    }

    public Boolean getAutoImageAltText() {
        return autoImageAltText;
    }

    public void setAutoImageAltText(Boolean autoImageAltText) {
        this.autoImageAltText = autoImageAltText;
    }

    public Boolean getAutoSchemaMarkup() {
        return autoSchemaMarkup;
    }

    public void setAutoSchemaMarkup(Boolean autoSchemaMarkup) {
        this.autoSchemaMarkup = autoSchemaMarkup;
    }

    public Boolean getAutoSitemapUpdate() {
        return autoSitemapUpdate;
    }

    public void setAutoSitemapUpdate(Boolean autoSitemapUpdate) {
        this.autoSitemapUpdate = autoSitemapUpdate;
    }

    public Boolean getAutoSeoContentAnalysis() {
        return autoSeoContentAnalysis;
    }

    public void setAutoSeoContentAnalysis(Boolean autoSeoContentAnalysis) {
        this.autoSeoContentAnalysis = autoSeoContentAnalysis;
    }

    public Boolean getAutoIndexingSubmission() {
        return autoIndexingSubmission;
    }

    public void setAutoIndexingSubmission(Boolean autoIndexingSubmission) {
        this.autoIndexingSubmission = autoIndexingSubmission;
    }

    public Boolean getAutoSeoMonitoring() {
        return autoSeoMonitoring;
    }

    public void setAutoSeoMonitoring(Boolean autoSeoMonitoring) {
        this.autoSeoMonitoring = autoSeoMonitoring;
    }

    public Boolean getAllowAiImageGeneration() {
        return allowAiImageGeneration;
    }

    public void setAllowAiImageGeneration(Boolean allowAiImageGeneration) {
        this.allowAiImageGeneration = allowAiImageGeneration;
    }

    public Boolean getAllowAiVideoIntegration() {
        return allowAiVideoIntegration;
    }

    public void setAllowAiVideoIntegration(Boolean allowAiVideoIntegration) {
        this.allowAiVideoIntegration = allowAiVideoIntegration;
    }

    public Boolean getAllowAiKeywordResearch() {
        return allowAiKeywordResearch;
    }

    public void setAllowAiKeywordResearch(Boolean allowAiKeywordResearch) {
        this.allowAiKeywordResearch = allowAiKeywordResearch;
    }

    public Boolean getAllowAiInternalLinking() {
        return allowAiInternalLinking;
    }

    public void setAllowAiInternalLinking(Boolean allowAiInternalLinking) {
        this.allowAiInternalLinking = allowAiInternalLinking;
    }

    public Boolean getAllowAiBacklinkRecommendations() {
        return allowAiBacklinkRecommendations;
    }

    public void setAllowAiBacklinkRecommendations(Boolean allowAiBacklinkRecommendations) {
        this.allowAiBacklinkRecommendations = allowAiBacklinkRecommendations;
    }

    public Boolean getAllowAiScheduledGeneration() {
        return allowAiScheduledGeneration;
    }

    public void setAllowAiScheduledGeneration(Boolean allowAiScheduledGeneration) {
        this.allowAiScheduledGeneration = allowAiScheduledGeneration;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

