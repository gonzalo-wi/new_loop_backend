package com.loop.new_loop_api.performance.domain;

import com.loop.new_loop_api.performance.exception.InvalidMetricsRangeException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * The dates a metrics request covers. Missing ends default to the current week (Monday to Sunday,
 * in the clock's zone). Coverage only counts control dates up to {@code lastEndedDay}: trucks may
 * still be on the road today.
 */
public record MetricsRange(LocalDate from, LocalDate to, LocalDate lastEndedDay) {

    // Same limit as the dispenser statistics, so both pages behave alike.
    public static final int MAX_RANGE_DAYS = 92;

    public static MetricsRange of(LocalDate from, LocalDate to, Clock clock) {
        var today = LocalDate.now(clock);
        var start = from != null ? from : today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var end   = to != null ? to : today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        if (end.isBefore(start)) throw InvalidMetricsRangeException.endBeforeStart(start, end);
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) throw InvalidMetricsRangeException.tooWide(MAX_RANGE_DAYS);
        return new MetricsRange(start, end, today.minusDays(1));
    }
}
