package com.loop.new_loop_api.integrations.common.metrics;

import com.loop.new_loop_api.common.metrics.MetricsTags;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Generic outbound-call counter for external integrations (Aguas, Odoo, ...), tagged by
 * integration name and result. Meant to be called from the same place each integration
 * service already records its {@link com.loop.new_loop_api.integrations.common.entity.IntegrationLog}
 * outcome, so no per-client duplication is needed.
 */
@Component
@RequiredArgsConstructor
public class IntegrationCallMetrics {

    private static final String CALLS_COUNTER = "loop_integration_calls_total";

    private final MeterRegistry meterRegistry;

    public void recordSuccess(IntegrationName integration) {
        record(integration, CallResult.SUCCESS);
    }

    public void recordError(IntegrationName integration) {
        record(integration, CallResult.ERROR);
    }

    private void record(IntegrationName integration, CallResult result) {
        Counter.builder(CALLS_COUNTER)
                .description("Total outbound calls to external integrations, by result")
                .tag(MetricsTags.INTEGRATION, integration.name())
                .tag(MetricsTags.RESULT, result.name())
                .register(meterRegistry)
                .increment();
    }

    private enum CallResult {
        SUCCESS,
        ERROR
    }
}
