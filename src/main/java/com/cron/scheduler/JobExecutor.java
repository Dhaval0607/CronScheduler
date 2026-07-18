package com.cron.scheduler;

import com.cron.model.CronJob;
import com.cron.model.JobType;
import tools.jackson.databind.JsonNode;

/**
 * Runs the work behind one kind of cron job.
 *
 * <p>Implementations are discovered by the {@link JobExecutorRegistry}, which
 * indexes them by {@link #type()}; nothing needs to be registered by hand.
 */
public interface JobExecutor {

    /** The job type this executor handles. Must be unique across all implementations. */
    JobType type();

    /**
     * Rejects a malformed payload at create/update time rather than at fire time,
     * so a misconfigured job fails the API call instead of failing silently at 3am.
     *
     * @throws com.cron.exception.InvalidJobPayloadException if the payload is unusable
     */
    void validate(JsonNode payload);

    void execute(CronJob job);
}
