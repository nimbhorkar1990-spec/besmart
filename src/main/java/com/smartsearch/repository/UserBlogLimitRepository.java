package com.smartsearch.repository;

import com.smartsearch.model.User;
import com.smartsearch.model.UserBlogLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlogLimitRepository extends JpaRepository<UserBlogLimit, Long> {

    Optional<UserBlogLimit> findByUserCategoryAndIsCustomForUserFalse(String userCategory);

    Optional<UserBlogLimit> findByUser(User user);

    List<UserBlogLimit> findByIsCustomForUserFalse();

    List<UserBlogLimit> findByIsCustomForUserTrue();
}
