package com.cron.executor;

import com.cron.model.CronJob;
import com.cron.model.JobType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobExecutorRegistryTest {

    /** Minimal stand-in; the registry only cares about type(). */
    private record StubExecutor(JobType type) implements JobExecutor {
        @Override
        public void validate(JsonNode payload) {
        }

        @Override
        public void execute(CronJob job) {
        }
    }

    @Test
    void resolvesExecutorByType() {
        JobExecutor email = new StubExecutor(JobType.EMAIL);
        JobExecutor http = new StubExecutor(JobType.HTTP);

        JobExecutorRegistry registry = new JobExecutorRegistry(List.of(email, http));

        assertThat(registry.forType(JobType.EMAIL)).isSameAs(email);
        assertThat(registry.forType(JobType.HTTP)).isSameAs(http);
    }

    @Test
    void resolvesFromTheJobsOwnType() {
        JobExecutor email = new StubExecutor(JobType.EMAIL);
        JobExecutorRegistry registry = new JobExecutorRegistry(List.of(email));

        CronJob job = new CronJob();
        job.setJobType(JobType.EMAIL);

        assertThat(registry.forJob(job)).isSameAs(email);
    }

    @Test
    void failsFastWhenTwoExecutorsClaimTheSameType() {
        // Startup failure is deliberate: silently letting one shadow the other
        // would route jobs to whichever bean happened to be constructed last.
        List<JobExecutor> duplicates = List.of(
                new StubExecutor(JobType.EMAIL),
                new StubExecutor(JobType.EMAIL));

        assertThatThrownBy(() -> new JobExecutorRegistry(duplicates))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Two executors claim job type EMAIL");
    }

    @Test
    void throwsForATypeWithNoExecutor() {
        JobExecutorRegistry registry = new JobExecutorRegistry(List.of(new StubExecutor(JobType.EMAIL)));

        assertThatThrownBy(() -> registry.forType(JobType.HTTP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No executor registered");
    }
}
