package com.loop.new_loop_api.common.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SessionExpiryTest {

    // Argentina is UTC-3 all year, so 00:00 there is 03:00Z.

    @Test
    void shouldExpireAtTonightsMidnight_when_loggingInDuringTheMorning() {
        assertThat(SessionExpiry.nextMidnight(Instant.parse("2026-10-30T11:00:00Z")))
                .isEqualTo(Instant.parse("2026-10-31T03:00:00Z"));
    }

    @Test
    void shouldExpireInAMinute_when_loggingInAt2359() {
        assertThat(SessionExpiry.nextMidnight(Instant.parse("2026-10-31T02:59:00Z")))
                .isEqualTo(Instant.parse("2026-10-31T03:00:00Z"));
    }

    @Test
    void shouldLastTheWholeDay_when_loggingInExactlyAtMidnight() {
        assertThat(SessionExpiry.nextMidnight(Instant.parse("2026-10-31T03:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
    }

    @Test
    void shouldUseArgentinaDate_when_utcIsAlreadyTomorrow() {
        // 22:00 in Argentina is 01:00Z of the next day: the session still ends at Argentina's midnight.
        assertThat(SessionExpiry.nextMidnight(Instant.parse("2026-10-31T01:00:00Z")))
                .isEqualTo(Instant.parse("2026-10-31T03:00:00Z"));
    }
}
