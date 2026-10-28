package com.loop.new_loop_api.performance.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LoadDurationTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, -600, 3600, 7200})
    void shouldDiscardMissingNegativeOrImplausibleDurations(Integer seconds) {
        assertThat(LoadDuration.accept(seconds)).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 600, 1800, 3599})
    void shouldKeepDurationsUnderOneHour(int seconds) {
        assertThat(LoadDuration.accept(seconds)).isEqualTo(seconds);
    }
}
