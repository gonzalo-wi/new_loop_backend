package com.loop.new_loop_api.loadforecast.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/** Trucks are loaded for the next delivery day, the same date an exit control created today gets. */
public final class LoadDay {

    static final int REFERENCE_WEEKS = 4;

    private LoadDay() {
    }

    /** Tomorrow, or Monday when tomorrow is Sunday (no deliveries on Sundays). */
    public static LocalDate after(LocalDate today) {
        var tomorrow = today.plusDays(1);
        return tomorrow.getDayOfWeek() == DayOfWeek.SUNDAY ? tomorrow.plusDays(1) : tomorrow;
    }

    /** The same weekday in each of the previous weeks, most recent first. */
    public static List<LocalDate> referenceDays(LocalDate loadDay) {
        return IntStream.rangeClosed(1, REFERENCE_WEEKS)
                .mapToObj(loadDay::minusWeeks)
                .toList();
    }
}
