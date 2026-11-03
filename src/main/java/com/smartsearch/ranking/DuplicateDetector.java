package com.smartsearch.ranking;

import com.smartsearch.extractor.ExtractedPageData;
import com.smartsearch.model.DuplicateRecord;
import com.smartsearch.model.IndexedPage;
import com.smartsearch.repository.DuplicateRecordRepository;
import com.smartsearch.repository.IndexedPageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DuplicateDetector {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateDetector.class);

    private final IndexedPageRepository indexedPageRepository;
    private final DuplicateRecordRepository duplicateRecordRepository;

    public DuplicateDetector(IndexedPageRepository indexedPageRepository,
                             DuplicateRecordRepository duplicateRecordRepository) {
        this.indexedPageRepository = indexedPageRepository;
        this.duplicateRecordRepository = duplicateRecordRepository;
    }

    /**
     * Checks if the extracted page is a duplicate via Canonical URL or SHA-256 fingerprint.
     * Returns true if it is a duplicate and should NOT be indexed as a new page.
     */
    public boolean isDuplicateAndRecord(ExtractedPageData data) {
        String currentUrl = data.getUrl().trim();
        String canonicalUrl = data.getCanonicalUrl() != null ? data.getCanonicalUrl().trim() : currentUrl;
        String contentHash = data.getContentHash();

        // 1. Canonical URL check
        if (!canonicalUrl.equalsIgnoreCase(currentUrl)) {
            Optional<IndexedPage> canonicalMatch = indexedPageRepository.findByUrl(canonicalUrl);
            if (canonicalMatch.isPresent()) {
                recordDuplicate(currentUrl, canonicalUrl, contentHash, "CANONICAL");
                logger.info("Duplicate page detected (Canonical): {} points to {}", currentUrl, canonicalUrl);
                return true;
            }
        }

        // 2. Content Fingerprint (SHA-256) check
        if (contentHash != null && !contentHash.isBlank()) {
            Optional<IndexedPage> existingHashMatch = indexedPageRepository.findByContentHash(contentHash);
            if (existingHashMatch.isPresent() && !existingHashMatch.get().getUrl().equalsIgnoreCase(currentUrl)) {
                String originalUrl = existingHashMatch.get().getUrl();
                recordDuplicate(currentUrl, originalUrl, contentHash, "CONTENT_FINGERPRINT");
                logger.info("Duplicate page detected (Fingerprint): {} matches content of {}", currentUrl, originalUrl);
                return true;
            }
        }

        return false;
    }

    private void recordDuplicate(String dupUrl, String origUrl, String hash, String type) {
        try {
            if (duplicateRecordRepository.findByDuplicateUrl(dupUrl).isEmpty()) {
                DuplicateRecord record = new DuplicateRecord(dupUrl, origUrl, hash, type);
                duplicateRecordRepository.save(record);
            }
        } catch (Exception e) {
            logger.debug("Failed to save duplicate record: {}", e.getMessage());
        }
    }
}

