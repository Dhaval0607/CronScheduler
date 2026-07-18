package com.cron.scheduler;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Resolves the next fire time of a cron expression relative to a point in time.
 */
@Component
public class NextRunCalculator {

    private final ZoneId zone = ZoneId.systemDefault();

    /**
     * @return the first fire time strictly after {@code from}, or {@code null} if the
     * expression will never fire again.
     */
    public Instant nextRunAfter(String cronExpression, Instant from) {
        ZonedDateTime next = CronExpression.parse(cronExpression)
                .next(ZonedDateTime.ofInstant(from, zone));
        return next == null ? null : next.toInstant();
    }
}
