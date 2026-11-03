package com.smartsearch.repository;

import com.smartsearch.model.SavedResult;
import com.smartsearch.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SavedResultRepository extends JpaRepository<SavedResult, Long> {
    Page<SavedResult> findByUserOrderBySavedAtDesc(User user, Pageable pageable);
    Page<SavedResult> findByUserAndCategoryOrderBySavedAtDesc(User user, String category, Pageable pageable);
    Optional<SavedResult> findByUserAndUrl(User user, String url);
    boolean existsByUserAndUrl(User user, String url);
    long countByUser(User user);
    void deleteByUserAndId(User user, Long id);
}

