package com.smartsearch.service;

import com.smartsearch.dto.ApiConfigDto;
import com.smartsearch.model.SystemSetting;
import com.smartsearch.repository.SystemSettingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemSettingService {

    private final SystemSettingRepository settingRepository;
    private final Map<String, String> cachedSettings = new HashMap<>();

    public SystemSettingService(SystemSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @PostConstruct
    public void init() {
        refreshCache();
        // Initialize defaults if missing
        initDefaultSetting("SEARCH_PROVIDER", "HYBRID", "Search provider mode: LUCENE_ONLY, EXTERNAL_ONLY, HYBRID");
        initDefaultSetting("EXTERNAL_SEARCH_ENABLED", "true", "Enable live external web search fallback");
        initDefaultSetting("BING_API_KEY", "", "Bing Web Search API Key (optional)");
        initDefaultSetting("BING_ENDPOINT", "https://api.bing.microsoft.com/v7.0/search", "Bing Web Search API Endpoint");
        initDefaultSetting("DEFAULT_PAGE_SIZE", "10", "Default search result items per page");
        initDefaultSetting("DEFAULT_SAFE_SEARCH", "MODERATE", "Default safe search filtering: OFF, MODERATE, STRICT");
        initDefaultSetting("VOICE_SEARCH_ENABLED", "true", "Enable client-side voice search UI");
        initDefaultSetting("SUGGEST_ENABLED", "true", "Enable autocomplete suggestions");
        refreshCache();
    }

    private void initDefaultSetting(String key, String defaultValue, String description) {
        if (!settingRepository.existsBySettingKey(key)) {
            settingRepository.save(new SystemSetting(key, defaultValue, description));
        }
    }

    public synchronized void refreshCache() {
        cachedSettings.clear();
        List<SystemSetting> all = settingRepository.findAll();
        for (SystemSetting s : all) {
            cachedSettings.put(s.getSettingKey(), s.getSettingValue());
        }
    }

    public String getSetting(String key, String defaultValue) {
        return cachedSettings.getOrDefault(key, defaultValue);
    }

    public boolean getBooleanSetting(String key, boolean defaultValue) {
        String val = cachedSettings.get(key);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val);
    }

    public int getIntSetting(String key, int defaultValue) {
        String val = cachedSettings.get(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Transactional
    public void updateSetting(String key, String value) {
        updateSetting(key, value, "");
    }

    @Transactional
    public void updateSetting(String key, String value, String description) {
        SystemSetting setting = settingRepository.findBySettingKey(key)
                .orElseGet(() -> new SystemSetting(key, value, description));
        setting.setSettingValue(value);
        if (description != null && !description.isBlank()) {
            setting.setDescription(description);
        }
        settingRepository.save(setting);
        cachedSettings.put(key, value);
    }

    public ApiConfigDto getApiConfig() {
        ApiConfigDto dto = new ApiConfigDto();
        dto.setSearchProvider(getSetting("SEARCH_PROVIDER", "HYBRID"));
        dto.setExternalSearchEnabled(getBooleanSetting("EXTERNAL_SEARCH_ENABLED", true));
        dto.setBingApiKey(getSetting("BING_API_KEY", ""));
        dto.setBingEndpoint(getSetting("BING_ENDPOINT", "https://api.bing.microsoft.com/v7.0/search"));
        dto.setDefaultPageSize(getIntSetting("DEFAULT_PAGE_SIZE", 10));
        dto.setDefaultSafeSearch(getSetting("DEFAULT_SAFE_SEARCH", "MODERATE"));
        dto.setVoiceSearchEnabled(getBooleanSetting("VOICE_SEARCH_ENABLED", true));
        dto.setSuggestEnabled(getBooleanSetting("SUGGEST_ENABLED", true));
        return dto;
    }

    @Transactional
    public void updateApiConfig(ApiConfigDto dto) {
        updateSetting("SEARCH_PROVIDER", dto.getSearchProvider());
        updateSetting("EXTERNAL_SEARCH_ENABLED", String.valueOf(dto.isExternalSearchEnabled()));
        updateSetting("BING_API_KEY", dto.getBingApiKey() != null ? dto.getBingApiKey().trim() : "");
        updateSetting("BING_ENDPOINT", dto.getBingEndpoint());
        updateSetting("DEFAULT_PAGE_SIZE", String.valueOf(dto.getDefaultPageSize()));
        updateSetting("DEFAULT_SAFE_SEARCH", dto.getDefaultSafeSearch());
        updateSetting("VOICE_SEARCH_ENABLED", String.valueOf(dto.isVoiceSearchEnabled()));
        updateSetting("SUGGEST_ENABLED", String.valueOf(dto.isSuggestEnabled()));
        refreshCache();
    }
}

