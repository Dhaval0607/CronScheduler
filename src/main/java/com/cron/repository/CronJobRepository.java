package com.cron.repository;

import com.cron.model.CronJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CronJobRepository extends JpaRepository<CronJob, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<CronJob> findByNameIgnoreCase(String name);
}
