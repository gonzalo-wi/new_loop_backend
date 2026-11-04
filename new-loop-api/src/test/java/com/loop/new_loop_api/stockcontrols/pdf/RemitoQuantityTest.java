package com.loop.new_loop_api.stockcontrols.pdf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RemitoQuantityTest {

    @Test
    void shouldShowUnitsWhenProductHasNoPack() {
        assertThat(RemitoQuantity.format(12, null)).isEqualTo("12");
    }

    @Test
    void shouldShowUnitsWhenPackIsOne() {
        assertThat(RemitoQuantity.format(12, 1)).isEqualTo("12");
    }

    @Test
    void shouldShowBulksWhenQuantityIsExactMultipleOfPack() {
        assertThat(RemitoQuantity.format(12, 6)).isEqualTo("2");
    }

    @Test
    void shouldShowUnitsWithSuffixWhenQuantityHasLeftoverUnits() {
        assertThat(RemitoQuantity.format(15, 6)).isEqualTo("15 u");
    }

    @Test
    void shouldShowUnitsWithSuffixWhenQuantityIsLessThanOneBulk() {
        assertThat(RemitoQuantity.format(4, 6)).isEqualTo("4 u");
    }
}
