package com.smartsearch.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserRegistrationDto {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    @NotBlank(message = "Mobile number is mandatory")
    @Size(min = 7, max = 25, message = "Please enter a valid mobile number")
    private String mobileNumber;

    @Size(max = 25, message = "WhatsApp number cannot exceed 25 characters")
    private String whatsappNumber;

    // Account Type: "JOB_SEEKER" or "EMPLOYER"
    private String accountType = "JOB_SEEKER";

    // Employer Business Verification
    private String companyName;
    private String shopActCert;
    private String gstCert;
    private String reraCert;
    private String msmeCert;

    public UserRegistrationDto() {
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

    public java.util.List<String> getVerifiedCertificates() {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (shopActCert != null && !shopActCert.isBlank()) list.add("Shop Act");
        if (gstCert != null && !gstCert.isBlank()) list.add("GST");
        if (reraCert != null && !reraCert.isBlank()) list.add("RERA");
        if (msmeCert != null && !msmeCert.isBlank()) list.add("MSME");
        return list;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
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
}

