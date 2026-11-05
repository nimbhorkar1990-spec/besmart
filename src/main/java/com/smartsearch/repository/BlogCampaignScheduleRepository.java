package com.smartsearch.repository;

import com.smartsearch.model.BlogCampaignSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BlogCampaignScheduleRepository extends JpaRepository<BlogCampaignSchedule, Long> {

    List<BlogCampaignSchedule> findByCommandIdOrderByDayNumberAsc(Long commandId);

    List<BlogCampaignSchedule> findByStatus(String status);

    List<BlogCampaignSchedule> findByScheduledDateAndStatus(LocalDate date, String status);

    List<BlogCampaignSchedule> findAllByOrderByScheduledDateAscDayNumberAsc();
}

