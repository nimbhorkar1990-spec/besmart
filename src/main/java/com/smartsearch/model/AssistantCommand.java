package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "assistant_commands")
public class AssistantCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String commandText;

    // Content Requirements
    @Column(length = 255)
    private String title;

    @Column(length = 100)
    private String category = "Real Estate";

    @Column(length = 150)
    private String targetAudience = "Property Buyers & Investors";

    @Column(length = 50)
    private String language = "English";

    @Column(length = 150)
    private String location = "Pune, India";

    @Column(length = 255)
    private String focusKeywords;

    @Column(length = 500)
    private String otherTopics;

    // Media Permissions
    @Column(nullable = false)
    private Boolean allowImages = true;

    @Column(nullable = false)
    private Boolean allowVideos = true;

    @Column(nullable = false)
    private Boolean allowAiImages = true;

    @Column(nullable = false)
    private Boolean allowVideoEmbed = true;

    // SEO Requirements
    @Column(length = 255)
    private String blogTarget = "Organic Traffic & Top Search Visibility";

    @Column(length = 500)
    private String backlinkUrl;

    @Column(length = 255)
    private String websiteFocus;

    @Column(length = 255)
    private String websiteRankingFocus;

    @Column(length = 255)
    private String targetEngines = "Google, Bing, Yahoo, DuckDuckGo, Brave";

    @Column(length = 500)
    private String seoInstructions;

    // Multi-Blog / 10-Day Campaign Schedule
    @Column(nullable = false)
    private Boolean isRecurringSchedule = false;

    private Integer blogsPerDay = 5; // 5 or 10

    private Integer scheduleDurationDays = 10;

    @Column(nullable = false)
    private Boolean scheduleApproved = false;

    @Column(length = 50)
    private String scheduleStatus = "NONE"; // "NONE", "SCHEDULED", "IN_PROGRESS", "PAUSED", "COMPLETED", "CANCELLED"

    // Metrics & Monitoring
    @Column(nullable = false)
    private Integer blogsRequested = 1;

    @Column(nullable = false)
    private Integer blogsGenerated = 0;

    @Column(nullable = false)
    private Integer blogsApproved = 0;

    @Column(nullable = false)
    private Integer blogsPublished = 0;

    @Column(nullable = false, length = 50)
    private String status = "PENDING"; // "PENDING", "IN_PROGRESS", "COMPLETED", "FAILED", "PAUSED"

    @Lob
    @Column(columnDefinition = "TEXT")
    private String executionSummary;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    public AssistantCommand() {
        this.createdAt = LocalDateTime.now();
    }

    public int getPendingBlogs() {
        return Math.max(0, blogsGenerated - blogsApproved);
    }

    // --- Getters & Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCommandText() {
        return commandText;
    }

    public void setCommandText(String commandText) {
        this.commandText = commandText;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTargetAudience() {
        return targetAudience;
    }

    public void setTargetAudience(String targetAudience) {
        this.targetAudience = targetAudience;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getFocusKeywords() {
        return focusKeywords;
    }

    public void setFocusKeywords(String focusKeywords) {
        this.focusKeywords = focusKeywords;
    }

    public String getOtherTopics() {
        return otherTopics;
    }

    public void setOtherTopics(String otherTopics) {
        this.otherTopics = otherTopics;
    }

    public Boolean getAllowImages() {
        return allowImages;
    }

    public void setAllowImages(Boolean allowImages) {
        this.allowImages = allowImages;
    }

    public Boolean getAllowVideos() {
        return allowVideos;
    }

    public void setAllowVideos(Boolean allowVideos) {
        this.allowVideos = allowVideos;
    }

    public Boolean getAllowAiImages() {
        return allowAiImages;
    }

    public void setAllowAiImages(Boolean allowAiImages) {
        this.allowAiImages = allowAiImages;
    }

    public Boolean getAllowVideoEmbed() {
        return allowVideoEmbed;
    }

    public void setAllowVideoEmbed(Boolean allowVideoEmbed) {
        this.allowVideoEmbed = allowVideoEmbed;
    }

    public String getBlogTarget() {
        return blogTarget;
    }

    public void setBlogTarget(String blogTarget) {
        this.blogTarget = blogTarget;
    }

    public String getBacklinkUrl() {
        return backlinkUrl;
    }

    public void setBacklinkUrl(String backlinkUrl) {
        this.backlinkUrl = backlinkUrl;
    }

    public String getWebsiteFocus() {
        return websiteFocus;
    }

    public void setWebsiteFocus(String websiteFocus) {
        this.websiteFocus = websiteFocus;
    }

    public String getWebsiteRankingFocus() {
        return websiteRankingFocus;
    }

    public void setWebsiteRankingFocus(String websiteRankingFocus) {
        this.websiteRankingFocus = websiteRankingFocus;
    }

    public String getTargetEngines() {
        return targetEngines;
    }

    public void setTargetEngines(String targetEngines) {
        this.targetEngines = targetEngines;
    }

    public String getSeoInstructions() {
        return seoInstructions;
    }

    public void setSeoInstructions(String seoInstructions) {
        this.seoInstructions = seoInstructions;
    }

    public Boolean getIsRecurringSchedule() {
        return isRecurringSchedule;
    }

    public void setIsRecurringSchedule(Boolean isRecurringSchedule) {
        this.isRecurringSchedule = isRecurringSchedule;
    }

    public Integer getBlogsPerDay() {
        return blogsPerDay;
    }

    public void setBlogsPerDay(Integer blogsPerDay) {
        this.blogsPerDay = blogsPerDay;
    }

    public Integer getScheduleDurationDays() {
        return scheduleDurationDays;
    }

    public void setScheduleDurationDays(Integer scheduleDurationDays) {
        this.scheduleDurationDays = scheduleDurationDays;
    }

    public Boolean getScheduleApproved() {
        return scheduleApproved;
    }

    public void setScheduleApproved(Boolean scheduleApproved) {
        this.scheduleApproved = scheduleApproved;
    }

    public String getScheduleStatus() {
        return scheduleStatus;
    }

    public void setScheduleStatus(String scheduleStatus) {
        this.scheduleStatus = scheduleStatus;
    }

    public Integer getBlogsRequested() {
        return blogsRequested;
    }

    public void setBlogsRequested(Integer blogsRequested) {
        this.blogsRequested = blogsRequested;
    }

    public Integer getBlogsGenerated() {
        return blogsGenerated;
    }

    public void setBlogsGenerated(Integer blogsGenerated) {
        this.blogsGenerated = blogsGenerated;
    }

    public Integer getBlogsApproved() {
        return blogsApproved;
    }

    public void setBlogsApproved(Integer blogsApproved) {
        this.blogsApproved = blogsApproved;
    }

    public Integer getBlogsPublished() {
        return blogsPublished;
    }

    public void setBlogsPublished(Integer blogsPublished) {
        this.blogsPublished = blogsPublished;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExecutionSummary() {
        return executionSummary;
    }

    public void setExecutionSummary(String executionSummary) {
        this.executionSummary = executionSummary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
