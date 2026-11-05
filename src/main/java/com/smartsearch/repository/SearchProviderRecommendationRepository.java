package com.smartsearch.repository;

import com.smartsearch.model.SearchProviderRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SearchProviderRecommendationRepository extends JpaRepository<SearchProviderRecommendation, Long> {
    Optional<SearchProviderRecommendation> findByProviderId(String providerId);
    List<SearchProviderRecommendation> findAllByOrderByQualityRatingDesc();
    List<SearchProviderRecommendation> findByIsAddedFalseOrderByQualityRatingDesc();
}
