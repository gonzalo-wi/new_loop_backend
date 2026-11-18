package com.loop.new_loop_api.loadforecast.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoadForecastCalculatorTest {

    private static final UUID BIDON  = UUID.randomUUID();
    private static final UUID SIFON  = UUID.randomUUID();
    private static final UUID VASOS  = UUID.randomUUID();

    private static LoadedProduct bidon(int qty) { return new LoadedProduct(BIDON, "B20", "Bidón 20 L", 1, qty); }
    private static LoadedProduct sifon(int qty) { return new LoadedProduct(SIFON, "SIF", "Sifón", 2, qty); }
    private static LoadedProduct vasos(int qty) { return new LoadedProduct(VASOS, "VAS", "Vasos", null, qty); }

    private static ReferenceLoad load(String date, LoadedProduct... products) {
        return new ReferenceLoad(LocalDate.parse(date), List.of(products));
    }

    @Test
    void shouldReturnNothing_when_thereAreNoReferenceLoads() {
        assertThat(LoadForecastCalculator.forecast(List.of())).isEmpty();
    }

    @Test
    void shouldAverageOverTheLoadsFound_countingAMissingProductAsZero() {
        var items = LoadForecastCalculator.forecast(List.of(
                load("2026-11-10", bidon(100), sifon(10)),
                load("2026-11-03", bidon(120))));

        assertThat(items).containsExactly(
                new ForecastItem("B20", "Bidón 20 L", 110),
                new ForecastItem("SIF", "Sifón", 5));
    }

    @Test
    void shouldRoundHalfUp() {
        var items = LoadForecastCalculator.forecast(List.of(
                load("2026-11-10", bidon(3), sifon(1)),
                load("2026-11-03", bidon(2), sifon(0))));

        assertThat(items).containsExactly(
                new ForecastItem("B20", "Bidón 20 L", 3),   // 2.5 -> 3
                new ForecastItem("SIF", "Sifón", 1));       // 0.5 -> 1
    }

    @Test
    void shouldDropProductsWhoseForecastRoundsToZero() {
        var items = LoadForecastCalculator.forecast(List.of(
                load("2026-11-10", bidon(10), sifon(1)),
                load("2026-11-03", bidon(10), sifon(0)),
                load("2026-10-27", bidon(10), sifon(0))));   // 0.33 -> 0

        assertThat(items).containsExactly(new ForecastItem("B20", "Bidón 20 L", 10));
    }

    @Test
    void shouldOrderByDisplayOrderWithNullsLastThenByName() {
        var items = LoadForecastCalculator.forecast(List.of(
                load("2026-11-10", vasos(5), sifon(5), bidon(5))));

        assertThat(items).extracting(ForecastItem::productCode).containsExactly("B20", "SIF", "VAS");
    }
}
