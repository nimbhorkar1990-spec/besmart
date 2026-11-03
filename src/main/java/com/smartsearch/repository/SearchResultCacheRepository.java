package com.smartsearch.repository;

import com.smartsearch.model.SearchResultCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SearchResultCacheRepository extends JpaRepository<SearchResultCache, Long> {
    Optional<SearchResultCache> findByQueryHashAndCategory(String queryHash, String category);
    void deleteByExpiresAtBefore(LocalDateTime now);
}

