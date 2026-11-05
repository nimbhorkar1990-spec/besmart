package com.smartsearch.model;

import jakarta.persistence.*;

@Entity
@Table(name = "search_provider_recommendations")
public class SearchProviderRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String providerId; // e.g. "MOJEEK", "YANDEX", "QWANT", "BAIDU", "KAGI", "STARTPAGE", "ECOSIA"

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String websiteUrl;

    @Column(length = 500)
    private String apiEndpoint;

    @Column(length = 500)
    private String docUrl;

    @Column(length = 50)
    private String pricingModel; // "FREE", "FREEMIUM", "PAID"

    @Column(length = 255)
    private String globalCoverage;

    @Column(length = 255)
    private String supportedLanguages;

    @Column(length = 1000)
    private String description;

    private Double qualityRating; // e.g. 4.8

    private Boolean officialApiAvailable = true;

    @Column(length = 500)
    private String recommendedReason;

    private Boolean isAdded = false;

    public SearchProviderRecommendation() {
    }

    public SearchProviderRecommendation(String providerId, String name, String websiteUrl, String apiEndpoint,
                                        String docUrl, String pricingModel, String globalCoverage,
                                        String supportedLanguages, String description, Double qualityRating,
                                        Boolean officialApiAvailable, String recommendedReason) {
        this.providerId = providerId;
        this.name = name;
        this.websiteUrl = websiteUrl;
        this.apiEndpoint = apiEndpoint;
        this.docUrl = docUrl;
        this.pricingModel = pricingModel;
        this.globalCoverage = globalCoverage;
        this.supportedLanguages = supportedLanguages;
        this.description = description;
        this.qualityRating = qualityRating;
        this.officialApiAvailable = officialApiAvailable;
        this.recommendedReason = recommendedReason;
        this.isAdded = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    public String getDocUrl() {
        return docUrl;
    }

    public void setDocUrl(String docUrl) {
        this.docUrl = docUrl;
    }

    public String getPricingModel() {
        return pricingModel;
    }

    public void setPricingModel(String pricingModel) {
        this.pricingModel = pricingModel;
    }

    public String getGlobalCoverage() {
        return globalCoverage;
    }

    public void setGlobalCoverage(String globalCoverage) {
        this.globalCoverage = globalCoverage;
    }

    public String getSupportedLanguages() {
        return supportedLanguages;
    }

    public void setSupportedLanguages(String supportedLanguages) {
        this.supportedLanguages = supportedLanguages;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getQualityRating() {
        return qualityRating;
    }

    public void setQualityRating(Double qualityRating) {
        this.qualityRating = qualityRating;
    }

    public Boolean getOfficialApiAvailable() {
        return officialApiAvailable;
    }

    public void setOfficialApiAvailable(Boolean officialApiAvailable) {
        this.officialApiAvailable = officialApiAvailable;
    }

    public String getRecommendedReason() {
        return recommendedReason;
    }

    public void setRecommendedReason(String recommendedReason) {
        this.recommendedReason = recommendedReason;
    }

    public Boolean getIsAdded() {
        return isAdded;
    }

    public void setIsAdded(Boolean isAdded) {
        this.isAdded = isAdded;
    }
}

