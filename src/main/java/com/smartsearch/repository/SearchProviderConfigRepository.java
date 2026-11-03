package com.smartsearch.repository;

import com.smartsearch.model.SearchProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SearchProviderConfigRepository extends JpaRepository<SearchProviderConfig, Long> {
    Optional<SearchProviderConfig> findByProviderId(String providerId);
    List<SearchProviderConfig> findByEnabledTrueOrderByPriorityAsc();
    List<SearchProviderConfig> findAllByOrderByPriorityAsc();
}

