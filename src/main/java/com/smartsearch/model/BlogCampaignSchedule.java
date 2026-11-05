package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "blog_campaign_schedules")
public class BlogCampaignSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "command_id", nullable = false)
    private AssistantCommand command;

    @Column(nullable = false)
    private Integer dayNumber; // 1 to 10

    @Column(nullable = false)
    private LocalDate scheduledDate;

    @Column(nullable = false, length = 255)
    private String plannedTitle;

    @Column(length = 255)
    private String topicFocus;

    @Column(length = 150)
    private String targetIntent;

    @Column(length = 255)
    private String focusKeywords;

    @Column(nullable = false, length = 50)
    private String status = "SCHEDULED"; // "SCHEDULED", "GENERATED", "APPROVED", "PUBLISHED", "PAUSED", "CANCELLED"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blog_id")
    private BlogPost generatedBlog;

    private LocalDateTime generatedAt;

    public BlogCampaignSchedule() {
    }

    public BlogCampaignSchedule(AssistantCommand command, Integer dayNumber, LocalDate scheduledDate,
                                String plannedTitle, String topicFocus, String targetIntent,
                                String focusKeywords) {
        this.command = command;
        this.dayNumber = dayNumber;
        this.scheduledDate = scheduledDate;
        this.plannedTitle = plannedTitle;
        this.topicFocus = topicFocus;
        this.targetIntent = targetIntent;
        this.focusKeywords = focusKeywords;
        this.status = "SCHEDULED";
    }

    // --- Getters & Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AssistantCommand getCommand() {
        return command;
    }

    public void setCommand(AssistantCommand command) {
        this.command = command;
    }

    public Integer getDayNumber() {
        return dayNumber;
    }

    public void setDayNumber(Integer dayNumber) {
        this.dayNumber = dayNumber;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public String getPlannedTitle() {
        return plannedTitle;
    }

    public void setPlannedTitle(String plannedTitle) {
        this.plannedTitle = plannedTitle;
    }

    public String getTopicFocus() {
        return topicFocus;
    }

    public void setTopicFocus(String topicFocus) {
        this.topicFocus = topicFocus;
    }

    public String getTargetIntent() {
        return targetIntent;
    }

    public void setTargetIntent(String targetIntent) {
        this.targetIntent = targetIntent;
    }

    public String getFocusKeywords() {
        return focusKeywords;
    }

    public void setFocusKeywords(String focusKeywords) {
        this.focusKeywords = focusKeywords;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BlogPost getGeneratedBlog() {
        return generatedBlog;
    }

    public void setGeneratedBlog(BlogPost generatedBlog) {
        this.generatedBlog = generatedBlog;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
