package com.cron.service;

import com.cron.dto.CronJobRequest;
import com.cron.dto.CronJobResponse;
import com.cron.exception.CronJobNotFoundException;
import com.cron.exception.DuplicateCronJobNameException;
import com.cron.exception.InvalidCronExpressionException;
import com.cron.model.CronJob;
import com.cron.repository.CronJobRepository;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CronJobService {

    private final CronJobRepository cronJobRepository;

    public CronJobService(CronJobRepository cronJobRepository) {
        this.cronJobRepository = cronJobRepository;
    }

    public CronJobResponse create(CronJobRequest request) {
        validateCronExpression(request.cronExpression());

        if (cronJobRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateCronJobNameException(request.name());
        }

        CronJob job = new CronJob();
        job.setName(request.name());
        job.setDescription(request.description());
        job.setCronExpression(request.cronExpression());
        job.setEnabled(request.enabled() == null || request.enabled());

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

        CronJob job = getOrThrow(id);

        cronJobRepository.findByNameIgnoreCase(request.name())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateCronJobNameException(request.name());
                });

        job.setName(request.name());
        job.setDescription(request.description());
        job.setCronExpression(request.cronExpression());
        job.setEnabled(request.enabled() == null || request.enabled());
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

    private void validateCronExpression(String cronExpression) {
        if (!CronExpression.isValidExpression(cronExpression)) {
            throw new InvalidCronExpressionException(cronExpression);
        }
    }
}
