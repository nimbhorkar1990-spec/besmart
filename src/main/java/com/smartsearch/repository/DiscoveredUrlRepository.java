package com.smartsearch.repository;

import com.smartsearch.model.DiscoveredUrl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscoveredUrlRepository extends JpaRepository<DiscoveredUrl, Long> {
    Optional<DiscoveredUrl> findByUrl(String url);
    boolean existsByUrl(String url);
    Page<DiscoveredUrl> findAllByOrderByDiscoveredAtDesc(Pageable pageable);
    Page<DiscoveredUrl> findByStatusOrderByDiscoveredAtDesc(String status, Pageable pageable);
    List<DiscoveredUrl> findTop50ByStatusOrderByDiscoveredAtAsc(String status);
    long countByStatus(String status);
}

