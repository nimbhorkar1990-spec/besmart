package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_activity_logs")
public class AdminActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String adminUsername;

    @Column(nullable = false, length = 100)
    private String action; // e.g. "PROVIDER_ACTIVATED", "PROVIDER_DEACTIVATED", "PROVIDER_ADDED", "PROVIDER_UPDATED", "CONNECTION_TESTED"

    @Column(length = 50)
    private String targetProviderId;

    @Column(length = 1000)
    private String details;

    @Column(length = 50)
    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public AdminActivityLog() {
        this.timestamp = LocalDateTime.now();
    }

    public AdminActivityLog(String adminUsername, String action, String targetProviderId, String details, String ipAddress) {
        this.adminUsername = adminUsername;
        this.action = action;
        this.targetProviderId = targetProviderId;
        this.details = details;
        this.ipAddress = ipAddress;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetProviderId() {
        return targetProviderId;
    }

    public void setTargetProviderId(String targetProviderId) {
        this.targetProviderId = targetProviderId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}

