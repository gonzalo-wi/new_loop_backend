package com.loop.new_loop_api.dispensers.exception;

import java.time.LocalDate;

public class InvalidStatsRangeException extends RuntimeException {

    private InvalidStatsRangeException(String message) {
        super(message);
    }

    public static InvalidStatsRangeException endBeforeStart(LocalDate from, LocalDate to) {
        return new InvalidStatsRangeException("Invalid range: 'to' (" + to + ") must not be before 'from' (" + from + ")");
    }

    public static InvalidStatsRangeException tooWide(int maxDays) {
        return new InvalidStatsRangeException("Invalid range: the range must not exceed " + maxDays + " days");
    }
}
