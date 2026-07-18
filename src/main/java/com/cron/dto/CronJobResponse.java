package com.cron.dto;

import com.cron.model.CronJob;
import com.cron.model.JobType;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record CronJobResponse(
        Long id,
        String name,
        String description,
        String cronExpression,
        JobType jobType,
        JsonNode payload,
        boolean enabled,
        Instant nextRunAt,
        Instant lastRunAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static CronJobResponse from(CronJob job) {
        return new CronJobResponse(
                job.getId(),
                job.getName(),
                job.getDescription(),
                job.getCronExpression(),
                job.getJobType(),
                job.getPayload(),
                job.isEnabled(),
                job.getNextRunAt(),
                job.getLastRunAt(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
