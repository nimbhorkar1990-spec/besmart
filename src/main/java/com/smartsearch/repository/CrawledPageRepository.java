package com.smartsearch.repository;

import com.smartsearch.model.CrawledPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CrawledPageRepository extends JpaRepository<CrawledPage, Long> {
    Optional<CrawledPage> findByUrl(String url);
    boolean existsByUrl(String url);
    Optional<CrawledPage> findByContentHash(String contentHash);
    Page<CrawledPage> findAllByOrderByCrawledAtDesc(Pageable pageable);
    long countByDomain(String domain);
}

