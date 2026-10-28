package com.loop.new_loop_api.performance.exception;

import java.time.LocalDate;

public class InvalidMetricsRangeException extends RuntimeException {

    private InvalidMetricsRangeException(String message) {
        super(message);
    }

    public static InvalidMetricsRangeException endBeforeStart(LocalDate from, LocalDate to) {
        return new InvalidMetricsRangeException("Invalid range: 'to' (" + to + ") must not be before 'from' (" + from + ")");
    }

    public static InvalidMetricsRangeException tooWide(int maxDays) {
        return new InvalidMetricsRangeException("Invalid range: the range must not exceed " + maxDays + " days");
    }
}
