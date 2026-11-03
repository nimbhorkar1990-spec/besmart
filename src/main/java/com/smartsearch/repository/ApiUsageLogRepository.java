package com.smartsearch.repository;

import com.smartsearch.model.ApiUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {
    Page<ApiUsageLog> findAllByOrderByTimestampDesc(Pageable pageable);
    List<ApiUsageLog> findTop20ByOrderByTimestampDesc();
    long countByTimestampAfter(LocalDateTime date);
    long countByProviderId(String providerId);
}

