package com.smartsearch.repository;

import com.smartsearch.model.CrawlErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CrawlErrorLogRepository extends JpaRepository<CrawlErrorLog, Long> {
    Page<CrawlErrorLog> findAllByOrderByOccurredAtDesc(Pageable pageable);
    List<CrawlErrorLog> findTop20ByOrderByOccurredAtDesc();
    List<CrawlErrorLog> findTop50ByOrderByOccurredAtDesc();
    List<CrawlErrorLog> findTop100ByOrderByOccurredAtDesc();

    long countByErrorType(String errorType);

    List<CrawlErrorLog> findByErrorTypeOrderByOccurredAtDesc(String errorType);

    List<CrawlErrorLog> findByDomainContainingIgnoreCaseOrderByOccurredAtDesc(String domain);

    List<CrawlErrorLog> findByDomainContainingIgnoreCaseOrUrlContainingIgnoreCaseOrderByOccurredAtDesc(String domain, String url);

    List<CrawlErrorLog> findByCrawlJobIdOrderByOccurredAtDesc(Long crawlJobId);

    List<CrawlErrorLog> findByErrorTypeAndDomainContainingIgnoreCaseOrderByOccurredAtDesc(String errorType, String domain);

    @Query("SELECT DISTINCT c.errorType FROM CrawlErrorLog c ORDER BY c.errorType ASC")
    List<String> findDistinctErrorTypes();

    @Modifying
    @Transactional
    void deleteByErrorType(String errorType);
}
