package com.cron.scheduler;

import com.cron.model.CronJob;
import com.cron.repository.CronJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Takes ownership of due jobs by advancing their schedule, in a transaction kept
 * as short as possible so the row locks are released before any job actually runs.
 */
@Component
public class JobClaimer {

    private static final Logger log = LoggerFactory.getLogger(JobClaimer.class);

    private final CronJobRepository cronJobRepository;
    private final NextRunCalculator nextRunCalculator;

    public JobClaimer(CronJobRepository cronJobRepository, NextRunCalculator nextRunCalculator) {
        this.cronJobRepository = cronJobRepository;
        this.nextRunCalculator = nextRunCalculator;
    }

    /**
     * Locks the due jobs, moves each one's {@code nextRunAt} past {@code now}, and
     * returns them for execution. A job that fell several intervals behind is not
     * replayed once per missed interval — it fires once and resumes its schedule.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CronJob> claimDueJobs(Instant now, int batchSize) {
        List<CronJob> due = cronJobRepository.findDueJobsForUpdate(now, batchSize);

        for (CronJob job : due) {
            job.setLastRunAt(now);
            job.setNextRunAt(nextRunCalculator.nextRunAfter(job.getCronExpression(), now));
            if (job.getNextRunAt() == null) {
                log.info("Job id={} name={} has no further fire time; disabling", job.getId(), job.getName());
                job.setEnabled(false);
            }
        }

        return due;
    }
}
