package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "search_history", indexes = {
    @Index(name = "idx_history_user", columnList = "user_id"),
    @Index(name = "idx_history_searched_at", columnList = "searchedAt")
})
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 255)
    private String query;

    @Column(nullable = false, length = 50)
    private String category = "ALL";

    @Column(length = 64)
    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime searchedAt;

    public SearchHistory() {
    }

    public SearchHistory(User user, String query, String category, String ipAddress) {
        this.user = user;
        this.query = query;
        this.category = (category != null) ? category.toUpperCase() : "ALL";
        this.ipAddress = ipAddress;
    }

    @PrePersist
    protected void onCreate() {
        if (this.searchedAt == null) {
            this.searchedAt = LocalDateTime.now();
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

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getSearchedAt() {
        return searchedAt;
    }

    public void setSearchedAt(LocalDateTime searchedAt) {
        this.searchedAt = searchedAt;
    }
}

