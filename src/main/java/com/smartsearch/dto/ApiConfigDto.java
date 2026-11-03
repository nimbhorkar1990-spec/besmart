package com.smartsearch.dto;

public class ApiConfigDto {
    private String searchProvider = "HYBRID"; // LUCENE_ONLY, EXTERNAL_ONLY, HYBRID
    private boolean externalSearchEnabled = true;
    private String bingApiKey = "";
    private String bingEndpoint = "https://api.bing.microsoft.com/v7.0/search";
    private int defaultPageSize = 10;
    private String defaultSafeSearch = "MODERATE";
    private boolean voiceSearchEnabled = true;
    private boolean suggestEnabled = true;

    public ApiConfigDto() {
    }

    public String getSearchProvider() {
        return searchProvider;
    }

    public void setSearchProvider(String searchProvider) {
        this.searchProvider = searchProvider;
    }

    public boolean isExternalSearchEnabled() {
        return externalSearchEnabled;
    }

    public void setExternalSearchEnabled(boolean externalSearchEnabled) {
        this.externalSearchEnabled = externalSearchEnabled;
    }

    public String getBingApiKey() {
        return bingApiKey;
    }

    public void setBingApiKey(String bingApiKey) {
        this.bingApiKey = bingApiKey;
    }

    public String getBingEndpoint() {
        return bingEndpoint;
    }

    public void setBingEndpoint(String bingEndpoint) {
        this.bingEndpoint = bingEndpoint;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public String getDefaultSafeSearch() {
        return defaultSafeSearch;
    }

    public void setDefaultSafeSearch(String defaultSafeSearch) {
        this.defaultSafeSearch = defaultSafeSearch;
    }

    public boolean isVoiceSearchEnabled() {
        return voiceSearchEnabled;
    }

    public void setVoiceSearchEnabled(boolean voiceSearchEnabled) {
        this.voiceSearchEnabled = voiceSearchEnabled;
    }

    public boolean isSuggestEnabled() {
        return suggestEnabled;
    }

    public void setSuggestEnabled(boolean suggestEnabled) {
        this.suggestEnabled = suggestEnabled;
    }
}

