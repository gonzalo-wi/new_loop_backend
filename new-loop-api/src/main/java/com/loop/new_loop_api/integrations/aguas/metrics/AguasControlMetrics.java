package com.loop.new_loop_api.integrations.aguas.metrics;

import com.loop.new_loop_api.common.metrics.MetricsTags;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Business metrics for stock control submissions to Aguas (IN/OUT products),
 * tagged by operation (IN/OUT) where applicable.
 */
@Component
@RequiredArgsConstructor
public class AguasControlMetrics {

    private static final String SENT_COUNTER      = "loop_control_aguas_enviado_total";
    private static final String CORRECTED_COUNTER = "loop_control_aguas_corregido_total";
    private static final String REJECTED_COUNTER  = "loop_control_aguas_rechazado_total";

    private final MeterRegistry meterRegistry;

    public void recordSent(String operation) {
        Counter.builder(SENT_COUNTER)
                .description("Total stock controls successfully sent to Aguas")
                .tag(MetricsTags.OPERATION, operation)
                .register(meterRegistry)
                .increment();
    }

    public void recordRejected(String operation) {
        Counter.builder(REJECTED_COUNTER)
                .description("Total stock controls rejected/errored by Aguas")
                .tag(MetricsTags.OPERATION, operation)
                .register(meterRegistry)
                .increment();
    }

    public void recordCorrected() {
        Counter.builder(CORRECTED_COUNTER)
                .description("Total stock controls corrected by a SUPERVISOR and resent to Aguas")
                .register(meterRegistry)
                .increment();
    }
}
