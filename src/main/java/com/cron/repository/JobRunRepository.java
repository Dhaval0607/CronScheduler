package com.cron.repository;

import com.cron.model.JobRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {

    List<JobRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<JobRun> findByJobIdOrderByStartedAtDesc(Long jobId, Pageable pageable);
}
