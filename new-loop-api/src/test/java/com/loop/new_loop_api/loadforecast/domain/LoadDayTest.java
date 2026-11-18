package com.loop.new_loop_api.loadforecast.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LoadDayTest {

    @ParameterizedTest
    @CsvSource({
            "2026-11-16, 2026-11-17",  // Monday   -> Tuesday
            "2026-11-20, 2026-11-21",  // Friday   -> Saturday
            "2026-11-21, 2026-11-23",  // Saturday -> Monday (no deliveries on Sunday)
            "2026-11-22, 2026-11-23",  // Sunday   -> Monday
    })
    void shouldBeTheNextDeliveryDay(LocalDate today, LocalDate expected) {
        assertThat(LoadDay.after(today)).isEqualTo(expected);
    }

    @Test
    void shouldUseTheFourPreviousSameWeekdaysAsReference() {
        assertThat(LoadDay.referenceDays(LocalDate.of(2026, 11, 17))).containsExactly(
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 10, 27),
                LocalDate.of(2026, 10, 20));
    }
}
