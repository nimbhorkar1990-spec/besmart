package com.smartsearch.repository;

import com.smartsearch.model.BlogEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlogEditHistoryRepository extends JpaRepository<BlogEditHistory, Long> {

    List<BlogEditHistory> findByBlogIdOrderByEditedAtDesc(Long blogId);

    List<BlogEditHistory> findAllByOrderByEditedAtDesc();
}

