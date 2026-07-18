package com.cron.scheduler;

import com.cron.model.CronJob;
import com.cron.model.JobRun;
import com.cron.model.JobRunStatus;
import com.cron.repository.JobRunRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Writes one row per execution.
 *
 * <p>Separate bean so the {@code @Transactional} proxy actually applies: job
 * execution happens on a pool thread with no ambient transaction, and a
 * self-invoked call from {@code SchedulerService} would bypass the proxy.
 */
@Component
public class JobRunRecorder {

    private final JobRunRepository jobRunRepository;

    public JobRunRecorder(JobRunRepository jobRunRepository) {
        this.jobRunRepository = jobRunRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(CronJob job, Instant startedAt, Instant finishedAt, Throwable failure) {
        JobRun run = new JobRun();
        run.setJobId(job.getId());
        run.setJobName(job.getName());
        run.setJobType(job.getJobType());
        run.setStartedAt(startedAt);
        run.setFinishedAt(finishedAt);
        run.setDurationMs(Duration.between(startedAt, finishedAt).toMillis());
        run.setStatus(failure == null ? JobRunStatus.SUCCESS : JobRunStatus.FAILED);
        run.setErrorMessage(failure == null ? null : describe(failure));

        jobRunRepository.save(run);
    }

    private String describe(Throwable failure) {
        String message = failure.getClass().getSimpleName()
                + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
        return message.length() <= JobRun.MAX_ERROR_LENGTH
                ? message
                : message.substring(0, JobRun.MAX_ERROR_LENGTH);
    }
}
