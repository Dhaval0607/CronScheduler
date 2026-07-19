package com.cron.scheduler;

import com.cron.model.CronJob;
import com.cron.model.JobRun;
import com.cron.model.JobRunStatus;
import com.cron.model.JobType;
import com.cron.repository.JobRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class JobRunRecorderTest {

    private JobRunRepository repository;
    private JobRunRecorder recorder;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(JobRunRepository.class);
        recorder = new JobRunRecorder(repository);
    }

    private CronJob job() {
        CronJob job = new CronJob();
        job.setId(7L);
        job.setName("nightly");
        job.setJobType(JobType.HTTP);
        return job;
    }

    private JobRun captureSaved() {
        ArgumentCaptor<JobRun> saved = ArgumentCaptor.forClass(JobRun.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void recordsSuccessWithDuration() {
        Instant start = Instant.parse("2026-01-01T09:00:00Z");
        Instant end = Instant.parse("2026-01-01T09:00:02.500Z");

        recorder.record(job(), start, end, null);

        JobRun run = captureSaved();
        assertThat(run.getStatus()).isEqualTo(JobRunStatus.SUCCESS);
        assertThat(run.getDurationMs()).isEqualTo(2500);
        assertThat(run.getErrorMessage()).isNull();
    }

    @Test
    void copiesJobIdentityForHistoryThatOutlivesTheJob() {
        // No FK to cron_jobs, so name and type must be denormalised here or the
        // history is unreadable once the job is deleted.
        recorder.record(job(), Instant.now(), Instant.now(), null);

        JobRun run = captureSaved();
        assertThat(run.getJobId()).isEqualTo(7L);
        assertThat(run.getJobName()).isEqualTo("nightly");
        assertThat(run.getJobType()).isEqualTo(JobType.HTTP);
    }

    @Test
    void recordsFailureWithReason() {
        recorder.record(job(), Instant.now(), Instant.now(),
                new IllegalStateException("connection refused"));

        JobRun run = captureSaved();
        assertThat(run.getStatus()).isEqualTo(JobRunStatus.FAILED);
        assertThat(run.getErrorMessage()).contains("IllegalStateException", "connection refused");
    }

    @Test
    void truncatesOversizedErrorMessages() {
        // Stack traces and HTML error pages are unbounded; the column is not.
        String huge = "x".repeat(10_000);

        recorder.record(job(), Instant.now(), Instant.now(), new RuntimeException(huge));

        assertThat(captureSaved().getErrorMessage()).hasSize(JobRun.MAX_ERROR_LENGTH);
    }

    @Test
    void handlesExceptionsWithNoMessage() {
        recorder.record(job(), Instant.now(), Instant.now(), new NullPointerException());

        assertThat(captureSaved().getErrorMessage()).isEqualTo("NullPointerException");
    }
}
