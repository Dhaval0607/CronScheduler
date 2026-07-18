package com.cron.model;

/**
 * The kind of work a job performs. Each value is handled by exactly one
 * {@code JobExecutor}, resolved at fire time by the executor registry.
 */
public enum JobType {

    EMAIL,
    HTTP
}
