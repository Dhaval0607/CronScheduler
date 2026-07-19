package com.cron.scheduler;

import com.cron.model.CronJob;
import com.cron.repository.CronJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class JobClaimerTest {

    private CronJobRepository repository;
    private JobClaimer claimer;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(CronJobRepository.class);
        claimer = new JobClaimer(repository, new NextRunCalculator());
    }

    private CronJob job(String cron) {
        CronJob job = new CronJob();
        job.setId(1L);
        job.setName("job");
        job.setCronExpression(cron);
        job.setEnabled(true);
        return job;
    }

    @Test
    void advancesScheduleAndStampsLastRun() {
        CronJob job = job("0 0 * * * *");
        when(repository.findDueJobsForUpdate(any(), anyInt())).thenReturn(List.of(job));
        Instant now = Instant.parse("2026-01-01T09:00:00Z");

        claimer.claimDueJobs(now, 10);

        assertThat(job.getLastRunAt()).isEqualTo(now);
        assertThat(job.getNextRunAt()).isAfter(now);
    }

    @Test
    void doesNotReplayEveryMissedInterval() {
        // A job well behind fires once and resumes from now; it is not queued up
        // once per interval it slept through. Minute granularity keeps this
        // assertion independent of the machine's timezone offset.
        CronJob job = job("0 * * * * *");
        when(repository.findDueJobsForUpdate(any(), anyInt())).thenReturn(List.of(job));
        Instant thirtyMinutesLate = Instant.parse("2026-01-01T09:30:45Z");

        claimer.claimDueJobs(thirtyMinutesLate, 10);

        assertThat(job.getNextRunAt()).isEqualTo(Instant.parse("2026-01-01T09:31:00Z"));
    }

    @Test
    void disablesJobsThatCanNeverFireAgain() {
        // Otherwise the row lingers enabled with a null nextRunAt, invisible to
        // the due query but still looking active in the API.
        CronJob job = job("0 0 0 30 2 *");
        when(repository.findDueJobsForUpdate(any(), anyInt())).thenReturn(List.of(job));

        claimer.claimDueJobs(Instant.parse("2026-01-01T00:00:00Z"), 10);

        assertThat(job.getNextRunAt()).isNull();
        assertThat(job.isEnabled()).isFalse();
    }

    @Test
    void returnsEmptyWhenNothingIsDue() {
        when(repository.findDueJobsForUpdate(any(), anyInt())).thenReturn(List.of());

        assertThat(claimer.claimDueJobs(Instant.now(), 10)).isEmpty();
    }
}
