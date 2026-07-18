package com.cron.scheduler;

import com.cron.model.CronJob;
import com.cron.model.JobType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Indexes every {@link JobExecutor} bean by the type it handles.
 *
 * <p>Injecting the executors as a {@code List} rather than individually is what
 * keeps this open to new job types: adding an executor requires no change here
 * or in {@code SchedulerService}.
 */
@Component
public class JobExecutorRegistry {

    private final Map<JobType, JobExecutor> byType = new EnumMap<>(JobType.class);

    public JobExecutorRegistry(List<JobExecutor> executors) {
        for (JobExecutor executor : executors) {
            JobExecutor previous = byType.put(executor.type(), executor);
            if (previous != null) {
                // Fail at startup rather than letting one executor silently shadow the other.
                throw new IllegalStateException("Two executors claim job type %s: %s and %s"
                        .formatted(executor.type(),
                                previous.getClass().getSimpleName(),
                                executor.getClass().getSimpleName()));
            }
        }
    }

    public JobExecutor forType(JobType jobType) {
        JobExecutor executor = byType.get(jobType);
        if (executor == null) {
            throw new IllegalStateException("No executor registered for job type " + jobType);
        }
        return executor;
    }

    public JobExecutor forJob(CronJob job) {
        return forType(job.getJobType());
    }
}
