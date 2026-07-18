package com.cron.dto;

import jakarta.validation.constraints.NotBlank;

public record CronJobRequest(

        @NotBlank(message = "name must not be blank")
        String name,

        String description,

        @NotBlank(message = "cronExpression must not be blank")
        String cronExpression,

        Boolean enabled
) {
}
