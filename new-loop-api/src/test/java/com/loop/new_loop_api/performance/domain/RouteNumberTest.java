package com.loop.new_loop_api.performance.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RouteNumberTest {

    @ParameterizedTest
    @CsvSource({
            "179, 179",
            "Rto 179, 179",
            "RTO12, 12",
            "' 12 ', 12",
            "012, 12",
            "R-4 05, 405"
    })
    void shouldKeepOnlyTheDigitsAsANumber(String code, long expected) {
        assertThat(RouteNumber.of(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Rto", "  ", "12345678901234567890"})
    void shouldReturnNullWhenThereIsNoUsableNumber(String code) {
        assertThat(RouteNumber.of(code)).isNull();
    }
}
