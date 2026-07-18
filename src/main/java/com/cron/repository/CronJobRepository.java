package com.cron.repository;

import com.cron.model.CronJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CronJobRepository extends JpaRepository<CronJob, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<CronJob> findByNameIgnoreCase(String name);

    /**
     * Claims the jobs whose next run has come due. FOR UPDATE SKIP LOCKED means
     * concurrent scheduler instances each take a disjoint slice of the due rows
     * instead of blocking on each other or double-firing a job.
     * Must be called inside a transaction; the locks hold until it commits.
     */
    @Query(value = """
            select * from cron_jobs
            where enabled = true
              and next_run_at is not null
              and next_run_at <= :now
            order by next_run_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<CronJob> findDueJobsForUpdate(@Param("now") Instant now, @Param("limit") int limit);
}
