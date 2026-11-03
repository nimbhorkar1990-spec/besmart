package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "saved_results", indexes = {
    @Index(name = "idx_saved_user", columnList = "user_id")
})
public class SavedResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 1500)
    private String url;

    @Column(length = 2000)
    private String snippet;

    @Column(length = 50)
    private String category = "ALL";

    @Column(length = 1500)
    private String imageUrl;

    @Column(nullable = false)
    private LocalDateTime savedAt;

    public SavedResult() {
    }

    public SavedResult(User user, String title, String url, String snippet, String category, String imageUrl) {
        this.user = user;
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.category = (category != null) ? category.toUpperCase() : "ALL";
        this.imageUrl = imageUrl;
    }

    @PrePersist
    protected void onCreate() {
        if (this.savedAt == null) {
            this.savedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(LocalDateTime savedAt) {
        this.savedAt = savedAt;
    }
}

