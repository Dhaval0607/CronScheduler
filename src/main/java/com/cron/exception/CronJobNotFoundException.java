package com.cron.exception;

public class CronJobNotFoundException extends RuntimeException {

    public CronJobNotFoundException(Long id) {
        super("Cron job not found with id: " + id);
    }
}
