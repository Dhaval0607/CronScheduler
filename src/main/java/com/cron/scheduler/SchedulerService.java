package com.cron.scheduler;

import com.cron.model.CronJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Polls the repository for jobs that are due and dispatches them to the
 * execution pool. Polling is single-threaded by design (fixedDelay is
 * non-reentrant, so claims never overlap); execution is not.
 */
@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final JobClaimer jobClaimer;
    private final JobExecutorRegistry executorRegistry;
    private final ThreadPoolTaskExecutor jobExecutionPool;
    private final int batchSize;

    public SchedulerService(JobClaimer jobClaimer,
                            JobExecutorRegistry executorRegistry,
                            ThreadPoolTaskExecutor jobExecutionPool,
                            @Value("${scheduler.batch-size:100}") int batchSize) {
        this.jobClaimer = jobClaimer;
        this.executorRegistry = executorRegistry;
        this.jobExecutionPool = jobExecutionPool;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${scheduler.poll-interval-ms:5000}",
            initialDelayString = "${scheduler.initial-delay-ms:5000}")
    public void pollDueJobs() {
        Instant now = Instant.now();

        List<CronJob> due;
        try {
            due = jobClaimer.claimDueJobs(now, batchSize);
        } catch (RuntimeException e) {
            // Never let a failed poll kill the scheduled task.
            log.error("Failed to claim due jobs", e);
            return;
        }

        if (due.isEmpty()) {
            return;
        }

        log.debug("Claimed {} due job(s)", due.size());
        for (CronJob job : due) {
            // Hand off to the execution pool; polling stays serial so claims
            // never overlap, but the jobs themselves run in parallel.
            jobExecutionPool.execute(() -> runSafely(job));
        }
    }

    private void runSafely(CronJob job) {
        try {
            executorRegistry.forJob(job).execute(job);
        } catch (RuntimeException e) {
            // Must catch here: an exception escaping a pool task is discarded
            // silently. The schedule has already been advanced, so one bad job
            // neither stalls the batch nor gets retried in a tight loop.
            // This also absorbs a missing executor for an unknown job type.
            log.error("Job id={} name={} type={} failed",
                    job.getId(), job.getName(), job.getJobType(), e);
        }
    }
}
