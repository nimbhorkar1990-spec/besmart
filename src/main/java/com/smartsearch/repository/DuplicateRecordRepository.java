package com.smartsearch.repository;

import com.smartsearch.model.DuplicateRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DuplicateRecordRepository extends JpaRepository<DuplicateRecord, Long> {
    Optional<DuplicateRecord> findByDuplicateUrl(String duplicateUrl);
    boolean existsByContentHash(String contentHash);
    Page<DuplicateRecord> findAllByOrderByDetectedAtDesc(Pageable pageable);
}

