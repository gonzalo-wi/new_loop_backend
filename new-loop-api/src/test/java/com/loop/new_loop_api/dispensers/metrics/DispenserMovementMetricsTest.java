package com.loop.new_loop_api.dispensers.metrics;

import com.loop.new_loop_api.common.metrics.MetricsTags;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DispenserMovementMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private DispenserMovementMetrics dispenserMovementMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        dispenserMovementMetrics = new DispenserMovementMetrics(meterRegistry);
    }

    @Test
    void should_incrementSentCounter_taggedByMovementType_when_recordSentCalled() {
        dispenserMovementMetrics.recordSent(DispenserMovementType.LOAD);

        var counter = meterRegistry.find("loop_dispenser_movement_aguas_enviado_total")
                .tag(MetricsTags.MOVEMENT_TYPE, "LOAD")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void should_incrementRejectedCounter_taggedByMovementType_when_recordRejectedCalled() {
        dispenserMovementMetrics.recordRejected(DispenserMovementType.UNLOAD);

        var counter = meterRegistry.find("loop_dispenser_movement_aguas_rechazado_total")
                .tag(MetricsTags.MOVEMENT_TYPE, "UNLOAD")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void should_incrementUnregisteredCounterByGivenCount_when_recordUnregisteredSerialsExcludedCalled() {
        dispenserMovementMetrics.recordUnregisteredSerialsExcluded(3);

        var counter = meterRegistry.find("loop_dispenser_no_registrado_total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    void should_notRegisterCounter_when_recordUnregisteredSerialsExcludedCalledWithZero() {
        dispenserMovementMetrics.recordUnregisteredSerialsExcluded(0);

        var counter = meterRegistry.find("loop_dispenser_no_registrado_total").counter();
        assertThat(counter).isNull();
    }

    @Test
    void should_notRegisterCounter_when_recordUnregisteredSerialsExcludedCalledWithNegativeCount() {
        dispenserMovementMetrics.recordUnregisteredSerialsExcluded(-1);

        var counter = meterRegistry.find("loop_dispenser_no_registrado_total").counter();
        assertThat(counter).isNull();
    }
}
