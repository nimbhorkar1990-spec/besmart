package com.smartsearch.repository;

import com.smartsearch.model.SearchAnalytics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SearchAnalyticsRepository extends JpaRepository<SearchAnalytics, Long> {
    Optional<SearchAnalytics> findByQueryIgnoreCase(String query);
    List<SearchAnalytics> findTop10ByOrderBySearchCountDesc();
    Page<SearchAnalytics> findAllByOrderBySearchCountDesc(Pageable pageable);
    Page<SearchAnalytics> findByQueryContainingIgnoreCaseOrderBySearchCountDesc(String query, Pageable pageable);

    @Query("SELECT s.query FROM SearchAnalytics s WHERE LOWER(s.query) LIKE LOWER(CONCAT(:prefix, '%')) ORDER BY s.searchCount DESC")
    List<String> findSuggestionsByPrefix(String prefix, Pageable pageable);

    @Query("SELECT SUM(s.searchCount) FROM SearchAnalytics s")
    Long sumTotalSearches();
}

