package com.cron.service;

import com.cron.dto.CronJobRequest;
import com.cron.dto.CronJobResponse;
import com.cron.exception.CronJobNotFoundException;
import com.cron.exception.DuplicateCronJobNameException;
import com.cron.exception.InvalidCronExpressionException;
import com.cron.model.CronJob;
import com.cron.repository.CronJobRepository;
import com.cron.executor.JobExecutorRegistry;
import com.cron.scheduler.NextRunCalculator;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class CronJobService {

    private final CronJobRepository cronJobRepository;
    private final NextRunCalculator nextRunCalculator;
    private final JobExecutorRegistry executorRegistry;

    public CronJobService(CronJobRepository cronJobRepository,
                          NextRunCalculator nextRunCalculator,
                          JobExecutorRegistry executorRegistry) {
        this.cronJobRepository = cronJobRepository;
        this.nextRunCalculator = nextRunCalculator;
        this.executorRegistry = executorRegistry;
    }

    public CronJobResponse create(CronJobRequest request) {
        validateCronExpression(request.cronExpression());
        validatePayload(request);

        if (cronJobRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateCronJobNameException(request.name());
        }

        CronJob job = new CronJob();
        job.setName(request.name());
        job.setDescription(request.description());
        job.setCronExpression(request.cronExpression());
        job.setJobType(request.jobType());
        job.setPayload(request.payload());
        job.setEnabled(request.enabled() == null || request.enabled());
        scheduleNextRun(job);

        return CronJobResponse.from(cronJobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public List<CronJobResponse> findAll() {
        return cronJobRepository.findAll().stream()
                .map(CronJobResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CronJobResponse findById(Long id) {
        return CronJobResponse.from(getOrThrow(id));
    }

    public CronJobResponse update(Long id, CronJobRequest request) {
        validateCronExpression(request.cronExpression());
        validatePayload(request);

        CronJob job = getOrThrow(id);

        cronJobRepository.findByNameIgnoreCase(request.name())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateCronJobNameException(request.name());
                });

        job.setName(request.name());
        job.setDescription(request.description());
        job.setCronExpression(request.cronExpression());
        job.setJobType(request.jobType());
        job.setPayload(request.payload());
        job.setEnabled(request.enabled() == null || request.enabled());
        scheduleNextRun(job);
        return CronJobResponse.from(job);
    }

    public void delete(Long id) {
        CronJob job = getOrThrow(id);
        cronJobRepository.delete(job);
    }

    private CronJob getOrThrow(Long id) {
        return cronJobRepository.findById(id)
                .orElseThrow(() -> new CronJobNotFoundException(id));
    }

    /**
     * Recomputes the next fire time from the current expression. A disabled job
     * carries no next run, so it never shows up in the scheduler's due query.
     */
    private void scheduleNextRun(CronJob job) {
        job.setNextRunAt(job.isEnabled()
                ? nextRunCalculator.nextRunAfter(job.getCronExpression(), Instant.now())
                : null);
    }

    /**
     * Hands the payload to the executor that will eventually run it, so a bad
     * recipient or URL is rejected by the API rather than discovered at fire time.
     */
    private void validatePayload(CronJobRequest request) {
        executorRegistry.forType(request.jobType()).validate(request.payload());
    }

    private void validateCronExpression(String cronExpression) {
        if (!CronExpression.isValidExpression(cronExpression)) {
            throw new InvalidCronExpressionException(cronExpression);
        }
    }
}
