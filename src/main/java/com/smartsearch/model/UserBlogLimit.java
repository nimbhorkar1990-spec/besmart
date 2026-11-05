package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_blog_limits")
public class UserBlogLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String userCategory; // "ADMIN", "AGENT", "PROPERTY_OWNER", "USER", "AI"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // null if category-wide default, non-null if custom for specific user

    @Column(nullable = false)
    private Integer maxBlogs = 10;

    @Column(nullable = false)
    private Integer periodDays = 30; // 30 days default

    @Column(length = 100)
    private String customPeriodName = "30 Days";

    @Column(nullable = false)
    private Boolean isCustomForUser = false;

    @Column(nullable = false)
    private Integer blogsCreatedInPeriod = 0;

    private LocalDateTime periodStartedAt;

    public UserBlogLimit() {
        this.periodStartedAt = LocalDateTime.now();
    }

    public UserBlogLimit(String userCategory, Integer maxBlogs, Integer periodDays) {
        this.userCategory = userCategory;
        this.maxBlogs = maxBlogs;
        this.periodDays = periodDays;
        this.customPeriodName = periodDays + " Days";
        this.isCustomForUser = false;
        this.blogsCreatedInPeriod = 0;
        this.periodStartedAt = LocalDateTime.now();
    }

    public boolean isPeriodExpired() {
        if (periodStartedAt == null) return true;
        return LocalDateTime.now().isAfter(periodStartedAt.plusDays(periodDays));
    }

    public void checkAndResetPeriod() {
        if (isPeriodExpired()) {
            this.blogsCreatedInPeriod = 0;
            this.periodStartedAt = LocalDateTime.now();
        }
    }

    public boolean canCreateBlog() {
        checkAndResetPeriod();
        return this.blogsCreatedInPeriod < this.maxBlogs;
    }

    public int getRemainingBlogs() {
        checkAndResetPeriod();
        return Math.max(0, this.maxBlogs - this.blogsCreatedInPeriod);
    }

    public void incrementCreated() {
        checkAndResetPeriod();
        this.blogsCreatedInPeriod++;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserCategory() {
        return userCategory;
    }

    public void setUserCategory(String userCategory) {
        this.userCategory = userCategory;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Integer getMaxBlogs() {
        return maxBlogs;
    }

    public void setMaxBlogs(Integer maxBlogs) {
        this.maxBlogs = maxBlogs;
    }

    public Integer getPeriodDays() {
        return periodDays;
    }

    public void setPeriodDays(Integer periodDays) {
        this.periodDays = periodDays;
    }

    public String getCustomPeriodName() {
        return customPeriodName;
    }

    public void setCustomPeriodName(String customPeriodName) {
        this.customPeriodName = customPeriodName;
    }

    public Boolean getIsCustomForUser() {
        return isCustomForUser;
    }

    public void setIsCustomForUser(Boolean customForUser) {
        isCustomForUser = customForUser;
    }

    public Integer getBlogsCreatedInPeriod() {
        return blogsCreatedInPeriod;
    }

    public void setBlogsCreatedInPeriod(Integer blogsCreatedInPeriod) {
        this.blogsCreatedInPeriod = blogsCreatedInPeriod;
    }

    public LocalDateTime getPeriodStartedAt() {
        return periodStartedAt;
    }

    public void setPeriodStartedAt(LocalDateTime periodStartedAt) {
        this.periodStartedAt = periodStartedAt;
    }
}
