package com.smartsearch.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public class IndexPageDto {

    @NotBlank(message = "URL is required")
    @URL(message = "Must be a valid URL (http or https)")
    private String url;

    private String title;
    private String description;
    private String category = "ALL";
    private String keywords;
    private boolean crawlDeep = false;

    public IndexPageDto() {
    }

    public IndexPageDto(String url, String title, String description, String category) {
        this.url = url;
        this.title = title;
        this.description = description;
        this.category = category;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public boolean isCrawlDeep() {
        return crawlDeep;
    }

    public void setCrawlDeep(boolean crawlDeep) {
        this.crawlDeep = crawlDeep;
    }
}

