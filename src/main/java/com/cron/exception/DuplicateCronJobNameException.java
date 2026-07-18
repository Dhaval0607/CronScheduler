package com.cron.exception;

public class DuplicateCronJobNameException extends RuntimeException {

    public DuplicateCronJobNameException(String name) {
        super("A cron job with name '" + name + "' already exists");
    }
}
