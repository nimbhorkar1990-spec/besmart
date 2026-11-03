package com.smartsearch.repository;

import com.smartsearch.model.CrawlQueueItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CrawlQueueItemRepository extends JpaRepository<CrawlQueueItem, Long> {
    Optional<CrawlQueueItem> findByUrl(String url);

    boolean existsByUrl(String url);

    List<CrawlQueueItem> findTop20ByStatusOrderByPriorityAscScheduledAtAsc(String status);

    Page<CrawlQueueItem> findAllByOrderByScheduledAtDesc(Pageable pageable);

    Page<CrawlQueueItem> findByStatusOrderByScheduledAtDesc(String status, Pageable pageable);

    long countByStatus(String status);

    long countByCrawlJobIdAndStatusIn(Long crawlJobId, List<String> statuses);

    @Modifying
    @Transactional
    void deleteByCrawlJobId(Long crawlJobId);
}
