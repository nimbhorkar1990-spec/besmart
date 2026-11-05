package com.smartsearch.repository;

import com.smartsearch.model.BlogPost;
import com.smartsearch.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    Optional<BlogPost> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<BlogPost> findByStatus(String status);

    Page<BlogPost> findByStatusOrderByPublishedAtDesc(String status, Pageable pageable);

    Page<BlogPost> findByCategoryAndStatusOrderByPublishedAtDesc(String category, String status, Pageable pageable);

    List<BlogPost> findByStatusAndExpiresAtBefore(String status, LocalDateTime date);

    long countByStatus(String status);

    long countByIsAiGeneratedTrue();

    long countByAuthorAndCreatedAtAfter(User author, LocalDateTime after);

    long countByAuthorRoleAndCreatedAtAfter(String authorRole, LocalDateTime after);

    List<BlogPost> findTop5ByStatusOrderByViewsCountDesc(String status);

    List<BlogPost> findTop5ByStatusOrderByOrganicClicksDesc(String status);

    List<BlogPost> findAllByOrderByCreatedAtDesc();

    @Query("SELECT AVG(b.seoScore) FROM BlogPost b")
    Double findAverageSeoScore();

    @Query("SELECT SUM(b.organicClicks) FROM BlogPost b")
    Long sumOrganicClicks();

    @Query("SELECT SUM(b.organicImpressions) FROM BlogPost b")
    Long sumOrganicImpressions();

    @Query("SELECT SUM(b.viewsCount) FROM BlogPost b")
    Long sumViewsCount();
}

