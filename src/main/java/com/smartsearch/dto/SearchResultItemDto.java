package com.smartsearch.dto;

public class SearchResultItemDto {
    private String id;
    private String title;
    private String url;
    private String domain;
    private String snippet;
    private String category;
    private String imageUrl;
    private String publishedDate;
    private float score;
    private String source; // "LUCENE", "WEB_API", "CRAWLER", "GOOGLE", "BING", "BRAVE", "WIKIPEDIA"
    private String sourceBadge = "SmartSearch Index";
    private String sourceBadgeClass = "bg-success-subtle text-success border border-success-subtle";
    private String canonicalUrl;
    private String crawledAt;
    private boolean saved;
    private java.util.List<String> verifiedProviders = new java.util.ArrayList<>();
    
    // Rich category specific attributes
    private String duration;      // For Videos (e.g., "14:20")
    private String channel;       // For Videos
    private String price;         // For Shopping (e.g., "$49.99")
    private String rating;        // For Shopping (e.g., "4.8")
    private String merchant;      // For Shopping
    private String location;      // For Maps
    private String coordinates;   // For Maps

    public SearchResultItemDto() {
    }

    public SearchResultItemDto(String title, String url, String snippet, String category, String domain) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.category = category;
        this.domain = domain;
        this.source = "LUCENE";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
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

    public String getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(String publishedDate) {
        this.publishedDate = publishedDate;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isSaved() {
        return saved;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }

    public String getSourceBadge() {
        return sourceBadge;
    }

    public void setSourceBadge(String sourceBadge) {
        this.sourceBadge = sourceBadge;
    }

    public String getSourceBadgeClass() {
        return sourceBadgeClass;
    }

    public void setSourceBadgeClass(String sourceBadgeClass) {
        this.sourceBadgeClass = sourceBadgeClass;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public void setCanonicalUrl(String canonicalUrl) {
        this.canonicalUrl = canonicalUrl;
    }

    public String getCrawledAt() {
        return crawledAt;
    }

    public void setCrawledAt(String crawledAt) {
        this.crawledAt = crawledAt;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(String coordinates) {
        this.coordinates = coordinates;
    }

    public java.util.List<String> getVerifiedProviders() {
        if (verifiedProviders == null) {
            verifiedProviders = new java.util.ArrayList<>();
        }
        return verifiedProviders;
    }

    public void setVerifiedProviders(java.util.List<String> verifiedProviders) {
        this.verifiedProviders = verifiedProviders;
    }

    public void addVerifiedProvider(String provider) {
        if (provider != null && !provider.isBlank()) {
            if (this.verifiedProviders == null) {
                this.verifiedProviders = new java.util.ArrayList<>();
            }
            if (!this.verifiedProviders.contains(provider)) {
                this.verifiedProviders.add(provider);
            }
        }
    }
}

