package com.smartsearch.repository;

import com.smartsearch.model.IndexedPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndexedPageRepository extends JpaRepository<IndexedPage, Long> {
    Optional<IndexedPage> findByUrl(String url);
    boolean existsByUrl(String url);
    Optional<IndexedPage> findByContentHash(String contentHash);
    Optional<IndexedPage> findByCanonicalUrl(String canonicalUrl);
    Page<IndexedPage> findByCategoryIgnoreCase(String category, Pageable pageable);
    Page<IndexedPage> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String title, String description, Pageable pageable);
    long countByCategoryIgnoreCase(String category);
    long countByStatus(String status);
    List<IndexedPage> findTop50ByOrderByIndexedAtDesc();
}

