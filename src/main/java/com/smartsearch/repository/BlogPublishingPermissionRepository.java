package com.smartsearch.repository;

import com.smartsearch.model.BlogPublishingPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlogPublishingPermissionRepository extends JpaRepository<BlogPublishingPermission, Long> {

    Optional<BlogPublishingPermission> findFirstByOrderByIdAsc();
}

