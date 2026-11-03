package com.smartsearch.repository;

import com.smartsearch.model.CrawlJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrawlJobRepository extends JpaRepository<CrawlJob, Long> {
    Page<CrawlJob> findAllByOrderByStartedAtDesc(Pageable pageable);
    List<CrawlJob> findTop10ByOrderByStartedAtDesc();
    List<CrawlJob> findTop20ByOrderByStartedAtDesc();
    List<CrawlJob> findByStatus(String status);
    long countByStatus(String status);
}
