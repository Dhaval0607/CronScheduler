package com.cron.service;

import com.cron.dto.JobRunResponse;
import com.cron.repository.JobRunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class JobRunService {

    /** History grows without bound, so every query is capped. */
    static final int MAX_LIMIT = 500;
    static final int DEFAULT_LIMIT = 50;

    private final JobRunRepository jobRunRepository;

    public JobRunService(JobRunRepository jobRunRepository) {
        this.jobRunRepository = jobRunRepository;
    }

    public List<JobRunResponse> findRecent(Integer limit) {
        return jobRunRepository.findAllByOrderByStartedAtDesc(page(limit)).stream()
                .map(JobRunResponse::from)
                .toList();
    }

    public List<JobRunResponse> findRecentForJob(Long jobId, Integer limit) {
        return jobRunRepository.findByJobIdOrderByStartedAtDesc(jobId, page(limit)).stream()
                .map(JobRunResponse::from)
                .toList();
    }

    private Pageable page(Integer limit) {
        int effective = limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return PageRequest.ofSize(effective);
    }
}
