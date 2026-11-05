package com.smartsearch.repository;

import com.smartsearch.model.AiSeoRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiSeoRecommendationRepository extends JpaRepository<AiSeoRecommendation, Long> {

    List<AiSeoRecommendation> findByApprovalStatusOrderByCreatedAtDesc(String approvalStatus);

    List<AiSeoRecommendation> findByBlogIdOrderByCreatedAtDesc(Long blogId);

    List<AiSeoRecommendation> findAllByOrderByCreatedAtDesc();

    long countByApprovalStatus(String approvalStatus);
}
