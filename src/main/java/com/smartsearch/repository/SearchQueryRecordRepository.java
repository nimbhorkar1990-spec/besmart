package com.smartsearch.repository;

import com.smartsearch.model.SearchQueryRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SearchQueryRecordRepository extends JpaRepository<SearchQueryRecord, Long> {
    Page<SearchQueryRecord> findAllByOrderBySearchedAtDesc(Pageable pageable);
    List<SearchQueryRecord> findTop10ByOrderBySearchedAtDesc();
    long countBySearchedAtAfter(LocalDateTime date);
    long countByExternalTriggeredTrue();
}

