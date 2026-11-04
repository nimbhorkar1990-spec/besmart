package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.ROLE_USER;

    @Column(length = 255)
    private String avatarUrl;

    @Column(length = 30)
    private String mobileNumber;

    @Column(length = 30)
    private String whatsappNumber;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 30)
    private String accountType = "JOB_SEEKER";

    @Column(length = 150)
    private String companyName;

    @Column(length = 100)
    private String shopActCert;

    @Column(length = 100)
    private String gstCert;

    @Column(length = 100)
    private String reraCert;

    @Column(length = 100)
    private String msmeCert;

    @Column(nullable = false)
    private boolean businessVerified = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public User() {
    }

    public User(String username, String email, String password, String fullName, Role role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.enabled = true;
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getShopActCert() {
        return shopActCert;
    }

    public void setShopActCert(String shopActCert) {
        this.shopActCert = shopActCert;
    }

    public String getGstCert() {
        return gstCert;
    }

    public void setGstCert(String gstCert) {
        this.gstCert = gstCert;
    }

    public String getReraCert() {
        return reraCert;
    }

    public void setReraCert(String reraCert) {
        this.reraCert = reraCert;
    }

    public String getMsmeCert() {
        return msmeCert;
    }

    public void setMsmeCert(String msmeCert) {
        this.msmeCert = msmeCert;
    }

    public boolean isBusinessVerified() {
        return businessVerified;
    }

    public void setBusinessVerified(boolean businessVerified) {
        this.businessVerified = businessVerified;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getWhatsappNumber() {
        return whatsappNumber;
    }

    public void setWhatsappNumber(String whatsappNumber) {
        this.whatsappNumber = whatsappNumber;
    }

    public int getCertificateCount() {
        int count = 0;
        if (shopActCert != null && !shopActCert.isBlank()) count++;
        if (gstCert != null && !gstCert.isBlank()) count++;
        if (reraCert != null && !reraCert.isBlank()) count++;
        if (msmeCert != null && !msmeCert.isBlank()) count++;
        return count;
    }

    public boolean hasMinimumCertificates() {
        return getCertificateCount() >= 2;
    }

    public boolean isEmployer() {
        return "EMPLOYER".equalsIgnoreCase(accountType) || role == Role.ROLE_ADMIN;
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

