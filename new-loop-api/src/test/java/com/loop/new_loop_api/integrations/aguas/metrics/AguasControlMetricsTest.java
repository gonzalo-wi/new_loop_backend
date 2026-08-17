package com.loop.new_loop_api.integrations.aguas.metrics;

import com.loop.new_loop_api.common.metrics.MetricsTags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AguasControlMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private AguasControlMetrics aguasControlMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        aguasControlMetrics = new AguasControlMetrics(meterRegistry);
    }

    @Test
    void should_incrementSentCounter_taggedByOperation_when_recordSentCalled() {
        aguasControlMetrics.recordSent("IN");

        var counter = meterRegistry.find("loop_control_aguas_enviado_total")
                .tag(MetricsTags.OPERATION, "IN")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void should_incrementRejectedCounter_taggedByOperation_when_recordRejectedCalled() {
        aguasControlMetrics.recordRejected("OUT");

        var counter = meterRegistry.find("loop_control_aguas_rechazado_total")
                .tag(MetricsTags.OPERATION, "OUT")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void should_incrementCorrectedCounter_when_recordCorrectedCalled() {
        aguasControlMetrics.recordCorrected();
        aguasControlMetrics.recordCorrected();

        var counter = meterRegistry.find("loop_control_aguas_corregido_total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    void should_keepSentAndRejectedIndependent_when_bothOperationsAreRecorded() {
        aguasControlMetrics.recordSent("IN");
        aguasControlMetrics.recordRejected("IN");

        var sentCounter = meterRegistry.find("loop_control_aguas_enviado_total")
                .tag(MetricsTags.OPERATION, "IN").counter();
        var rejectedCounter = meterRegistry.find("loop_control_aguas_rechazado_total")
                .tag(MetricsTags.OPERATION, "IN").counter();

        assertThat(sentCounter.count()).isEqualTo(1.0);
        assertThat(rejectedCounter.count()).isEqualTo(1.0);
    }
}
