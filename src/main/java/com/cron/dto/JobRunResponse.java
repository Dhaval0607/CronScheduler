package com.cron.dto;

import com.cron.model.JobRun;
import com.cron.model.JobRunStatus;
import com.cron.model.JobType;

import java.time.Instant;

public record JobRunResponse(
        Long id,
        Long jobId,
        String jobName,
        JobType jobType,
        JobRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        String errorMessage
) {
    public static JobRunResponse from(JobRun run) {
        return new JobRunResponse(
                run.getId(),
                run.getJobId(),
                run.getJobName(),
                run.getJobType(),
                run.getStatus(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                run.getErrorMessage()
        );
    }
}
