package com.smartsearch.provider;

import com.smartsearch.dto.SearchRequestDto;
import com.smartsearch.dto.SearchResultItemDto;

import java.util.List;

public interface SearchProvider {

    /**
     * Unique identifier for this provider (e.g., "GOOGLE", "BING", "BRAVE", "WIKIPEDIA").
     */
    String getProviderId();

    /**
     * Human-readable display label.
     */
    String getDisplayName();

    /**
     * Whether this provider is enabled by admin.
     */
    boolean isEnabled();

    /**
     * Whether all required credentials (e.g., API keys, search engine IDs) are present.
     */
    boolean isConfigured();

    /**
     * Priority order for ranking (1 = highest).
     */
    int getPriority();

    /**
     * Executes the external search against this provider's official API.
     */
    List<SearchResultItemDto> search(SearchRequestDto request);
}

