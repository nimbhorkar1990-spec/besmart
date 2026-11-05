package com.smartsearch.repository;

import com.smartsearch.model.AssistantCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssistantCommandRepository extends JpaRepository<AssistantCommand, Long> {

    List<AssistantCommand> findAllByOrderByCreatedAtDesc();

    List<AssistantCommand> findByStatusOrderByCreatedAtDesc(String status);
}

