package com.cron.exception;

public class InvalidCronExpressionException extends RuntimeException {

    public InvalidCronExpressionException(String cronExpression) {
        super("Invalid cron expression: '" + cronExpression + "'");
    }
}
