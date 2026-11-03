package com.smartsearch.repository;

import com.smartsearch.model.SearchHistory;
import com.smartsearch.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
    List<SearchHistory> findTop10ByUserOrderBySearchedAtDesc(User user);
    Page<SearchHistory> findByUserOrderBySearchedAtDesc(User user, Pageable pageable);
    Page<SearchHistory> findByUserAndQueryContainingIgnoreCaseOrderBySearchedAtDesc(User user, String query, Pageable pageable);
    
    @Modifying
    @Query("DELETE FROM SearchHistory h WHERE h.user = :user")
    void deleteByUser(@Param("user") User user);

    @Query("SELECT h.query FROM SearchHistory h WHERE h.user = :user GROUP BY h.query ORDER BY MAX(h.searchedAt) DESC")
    List<String> findDistinctRecentQueriesByUser(@Param("user") User user, Pageable pageable);

    long countByUser(User user);
    long countBySearchedAtAfter(LocalDateTime date);
}

