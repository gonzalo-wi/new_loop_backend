package com.loop.new_loop_api.performance.domain;

import com.loop.new_loop_api.performance.exception.InvalidMetricsRangeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricsRangeTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    @ParameterizedTest
    @CsvSource({
            // today (Wednesday)   → Monday, Sunday
            "2026-10-28, 2026-10-26, 2026-11-01",
            // today is a Monday   → that same week
            "2026-10-26, 2026-10-26, 2026-11-01",
            // today is a Sunday   → the week that ends today
            "2026-11-01, 2026-10-26, 2026-11-01"
    })
    void shouldDefaultToTheCurrentWeekMondayToSunday(LocalDate today, LocalDate monday, LocalDate sunday) {
        var range = MetricsRange.of(null, null, clockAt(today));

        assertThat(range.from()).isEqualTo(monday);
        assertThat(range.to()).isEqualTo(sunday);
    }

    @Test
    void shouldKeepTheRequestedDatesAndEndCoverageYesterday() {
        var range = MetricsRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), clockAt(LocalDate.of(2026, 10, 28)));

        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(range.lastEndedDay()).isEqualTo(LocalDate.of(2026, 10, 27));
    }

    @Test
    void shouldRejectARangeThatEndsBeforeItStarts() {
        var clock = clockAt(LocalDate.of(2026, 10, 28));

        assertThatThrownBy(() -> MetricsRange.of(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 9), clock))
                .isInstanceOf(InvalidMetricsRangeException.class);
    }

    @Test
    void shouldAccept92DaysAndReject93() {
        var clock = clockAt(LocalDate.of(2026, 10, 28));
        var from  = LocalDate.of(2026, 1, 1);

        assertThat(MetricsRange.of(from, from.plusDays(91), clock).to()).isEqualTo(from.plusDays(91));
        assertThatThrownBy(() -> MetricsRange.of(from, from.plusDays(92), clock))
                .isInstanceOf(InvalidMetricsRangeException.class);
    }

    @Test
    void shouldFillTheMissingEndFromTheCurrentWeek() {
        var range = MetricsRange.of(LocalDate.of(2026, 10, 20), null, clockAt(LocalDate.of(2026, 10, 28)));

        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    private static Clock clockAt(LocalDate day) {
        // 23:30 local: the UTC date is already the next day, the range must still follow Argentina.
        return Clock.fixed(day.atTime(23, 30).atZone(ARGENTINA).toInstant(), ARGENTINA);
    }
}
