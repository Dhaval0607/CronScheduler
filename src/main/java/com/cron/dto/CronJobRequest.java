package com.cron.dto;

import com.cron.model.JobType;
import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CronJobRequest(

        @NotBlank(message = "name must not be blank")
        String name,

        String description,

        @NotBlank(message = "cronExpression must not be blank")
        String cronExpression,

        @NotNull(message = "jobType must not be null")
        JobType jobType,

        /** Type-specific config; validated by the executor for {@link #jobType}. */
        JsonNode payload,

        Boolean enabled
) {
}
