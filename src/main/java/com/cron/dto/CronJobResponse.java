package com.cron.dto;

import com.cron.model.CronJob;

import java.time.Instant;

public record CronJobResponse(
        Long id,
        String name,
        String description,
        String cronExpression,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static CronJobResponse from(CronJob job) {
        return new CronJobResponse(
                job.getId(),
                job.getName(),
                job.getDescription(),
                job.getCronExpression(),
                job.isEnabled(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
