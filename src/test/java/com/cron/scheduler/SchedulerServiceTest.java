package com.cron.scheduler;

import com.cron.executor.JobExecutor;
import com.cron.executor.JobExecutorRegistry;
import com.cron.model.CronJob;
import com.cron.model.JobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulerServiceTest {

    private JobClaimer claimer;
    private JobExecutorRegistry registry;
    private JobExecutor executor;
    private JobRunRecorder recorder;
    private SchedulerService scheduler;

    @BeforeEach
    void setUp() {
        claimer = Mockito.mock(JobClaimer.class);
        registry = Mockito.mock(JobExecutorRegistry.class);
        executor = Mockito.mock(JobExecutor.class);
        recorder = Mockito.mock(JobRunRecorder.class);

        // Run dispatched tasks inline so assertions do not race the pool.
        ThreadPoolTaskExecutor pool = Mockito.mock(ThreadPoolTaskExecutor.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(pool).execute(any(Runnable.class));

        scheduler = new SchedulerService(claimer, registry, pool, recorder, 100);
    }

    private CronJob job() {
        CronJob job = new CronJob();
        job.setId(1L);
        job.setName("job");
        job.setJobType(JobType.EMAIL);
        return job;
    }

    @Test
    void executesEachClaimedJob() {
        CronJob job = job();
        when(claimer.claimDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(registry.forJob(job)).thenReturn(executor);

        scheduler.pollDueJobs();

        verify(executor).execute(job);
    }

    @Test
    void recordsSuccessfulRuns() {
        CronJob job = job();
        when(claimer.claimDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(registry.forJob(job)).thenReturn(executor);

        scheduler.pollDueJobs();

        verify(recorder).record(eq(job), any(Instant.class), any(Instant.class), isNull());
    }

    @Test
    void recordsFailureWithoutPropagating() {
        CronJob job = job();
        RuntimeException boom = new RuntimeException("endpoint down");
        when(claimer.claimDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(registry.forJob(job)).thenReturn(executor);
        doThrow(boom).when(executor).execute(job);

        assertThatCode(scheduler::pollDueJobs).doesNotThrowAnyException();

        verify(recorder).record(eq(job), any(Instant.class), any(Instant.class), eq(boom));
    }

    @Test
    void oneFailingJobDoesNotStopTheRestOfTheBatch() {
        CronJob failing = job();
        CronJob healthy = job();
        healthy.setId(2L);
        when(claimer.claimDueJobs(any(), anyInt())).thenReturn(List.of(failing, healthy));
        when(registry.forJob(any())).thenReturn(executor);
        doThrow(new RuntimeException("boom")).when(executor).execute(failing);

        scheduler.pollDueJobs();

        verify(executor).execute(healthy);
    }

    @Test
    void aFailedClaimDoesNotKillTheScheduledTask() {
        // If this propagated, Spring would stop rescheduling and the scheduler
        // would go permanently silent.
        when(claimer.claimDueJobs(any(), anyInt())).thenThrow(new RuntimeException("db down"));

        assertThatCode(scheduler::pollDueJobs).doesNotThrowAnyException();
        verify(executor, never()).execute(any());
    }

    @Test
    void aFailedHistoryWriteDoesNotFailTheJob() {
        CronJob job = job();
        when(claimer.claimDueJobs(any(), anyInt())).thenReturn(List.of(job));
        when(registry.forJob(job)).thenReturn(executor);
        doThrow(new RuntimeException("history table gone"))
                .when(recorder).record(any(), any(), any(), any());

        assertThatCode(scheduler::pollDueJobs).doesNotThrowAnyException();
        verify(executor).execute(job);
    }

    @Test
    void doesNothingWhenNoJobsAreDue() {
        when(claimer.claimDueJobs(any(), anyInt())).thenReturn(List.of());

        scheduler.pollDueJobs();

        verify(recorder, never()).record(any(), any(), any(), any());
    }

    @Test
    void claimsUsingTheConfiguredBatchSize() {
        when(claimer.claimDueJobs(any(), anyInt())).thenReturn(List.of());

        scheduler.pollDueJobs();

        ArgumentCaptor<Integer> batch = ArgumentCaptor.forClass(Integer.class);
        verify(claimer).claimDueJobs(any(), batch.capture());
        assertThat(batch.getValue()).isEqualTo(100);
    }
}
