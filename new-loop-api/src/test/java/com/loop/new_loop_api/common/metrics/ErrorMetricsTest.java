package com.loop.new_loop_api.common.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private ErrorMetrics errorMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        errorMetrics = new ErrorMetrics(meterRegistry);
    }

    @Test
    void should_incrementLoopErrorsTotal_when_recordErrorCalled() {
        errorMetrics.recordError("StockControlNotFoundException", "/stock-controls/{id}");

        var counter = meterRegistry.find("loop_errors_total")
                .tag(MetricsTags.EXCEPTION, "StockControlNotFoundException")
                .tag(MetricsTags.ENDPOINT, "/stock-controls/{id}")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void should_accumulateCount_when_recordErrorCalledMultipleTimesWithSameTags() {
        errorMetrics.recordError("ValidationException", "/users");
        errorMetrics.recordError("ValidationException", "/users");
        errorMetrics.recordError("ValidationException", "/users");

        var counter = meterRegistry.find("loop_errors_total")
                .tag(MetricsTags.EXCEPTION, "ValidationException")
                .tag(MetricsTags.ENDPOINT, "/users")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    void should_keepSeparateCounters_when_tagsDiffer() {
        errorMetrics.recordError("NotFoundException", "/routes/{id}");
        errorMetrics.recordError("ConflictException", "/routes/{id}");

        var notFoundCounter = meterRegistry.find("loop_errors_total")
                .tag(MetricsTags.EXCEPTION, "NotFoundException")
                .counter();
        var conflictCounter = meterRegistry.find("loop_errors_total")
                .tag(MetricsTags.EXCEPTION, "ConflictException")
                .counter();

        assertThat(notFoundCounter.count()).isEqualTo(1.0);
        assertThat(conflictCounter.count()).isEqualTo(1.0);
    }
}
