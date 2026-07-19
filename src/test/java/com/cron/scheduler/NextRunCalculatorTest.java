package com.cron.scheduler;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NextRunCalculatorTest {

    private final NextRunCalculator calculator = new NextRunCalculator();

    /**
     * Cron expressions resolve in the system zone, so fixtures are built as
     * local wall-clock time. Parsing these as UTC would make the test pass or
     * fail depending on the machine's offset.
     */
    private Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZoneId.systemDefault()).toInstant();
    }

    @Test
    void returnsNextMatchingInstant() {
        Instant from = at("2026-01-01T08:59:30");

        Instant next = calculator.nextRunAfter("0 0 * * * *", from);

        assertThat(next).isEqualTo(at("2026-01-01T09:00:00"));
    }

    @Test
    void isStrictlyAfterTheGivenInstant() {
        // A job claimed exactly on its own boundary must advance, not return the
        // same instant, or it would be re-claimed forever.
        Instant onTheBoundary = at("2026-01-01T09:00:00");

        Instant next = calculator.nextRunAfter("0 0 * * * *", onTheBoundary);

        assertThat(next).isEqualTo(at("2026-01-01T10:00:00"));
    }

    @Test
    void returnsNullWhenExpressionCanNeverFireAgain() {
        // Feb 30 does not exist; Spring resolves this to "never".
        Instant next = calculator.nextRunAfter("0 0 0 30 2 *", at("2026-01-01T00:00:00"));

        assertThat(next).isNull();
    }

    @Test
    void rejectsFiveFieldCrontabSyntax() {
        // Spring expects 6 fields (leading seconds); a standard crontab string
        // is a common mistake worth failing loudly on.
        assertThatThrownBy(() -> calculator.nextRunAfter("0 9 * * *", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
