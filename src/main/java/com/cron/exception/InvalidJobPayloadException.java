package com.cron.exception;

import com.cron.model.JobType;

public class InvalidJobPayloadException extends RuntimeException {

    public InvalidJobPayloadException(JobType jobType, String reason) {
        super("Invalid payload for job type " + jobType + ": " + reason);
    }
}
