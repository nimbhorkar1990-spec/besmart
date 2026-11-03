package com.smartsearch.dto;

import java.util.List;

public class JobListingDto {
    private Long id;
    private String title;
    private String company;
    private String location;
    private String jobType;
    private String category;
    private String salaryRange;
    private String experienceLevel;
    private String postedAgo;
    private String description;
    private List<String> tags;
    private String companyLogo;
    private boolean featured;
    private int applicantCount;

    // Business Certificates (Minimum 2 required: Shop Act, GST, RERA, MSME)
    private String shopActCert;
    private String gstCert;
    private String reraCert;
    private String msmeCert;

    public JobListingDto() {
    }

    public JobListingDto(Long id, String title, String company, String location, String jobType,
                         String category, String salaryRange, String experienceLevel,
                         String postedAgo, String description, List<String> tags,
                         String companyLogo, boolean featured, int applicantCount) {
        this.id = id;
        this.title = title;
        this.company = company;
        this.location = location;
        this.jobType = jobType;
        this.category = category;
        this.salaryRange = salaryRange;
        this.experienceLevel = experienceLevel;
        this.postedAgo = postedAgo;
        this.description = description;
        this.tags = tags;
        this.companyLogo = companyLogo;
        this.featured = featured;
        this.applicantCount = applicantCount;
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSalaryRange() {
        return salaryRange;
    }

    public void setSalaryRange(String salaryRange) {
        this.salaryRange = salaryRange;
    }

    public String getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(String experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public String getPostedAgo() {
        return postedAgo;
    }

    public void setPostedAgo(String postedAgo) {
        this.postedAgo = postedAgo;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getCompanyLogo() {
        return companyLogo;
    }

    public void setCompanyLogo(String companyLogo) {
        this.companyLogo = companyLogo;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public int getApplicantCount() {
        return applicantCount;
    }

    public void setApplicantCount(int applicantCount) {
        this.applicantCount = applicantCount;
    }
}

