package com.loop.new_loop_api.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Counts errors handled by {@link com.loop.new_loop_api.common.exception.GlobalExceptionHandler},
 * tagged by exception type and endpoint route pattern (both low-cardinality).
 */
@Component
@RequiredArgsConstructor
public class ErrorMetrics {

    private static final String ERRORS_COUNTER = "loop_errors_total";

    private final MeterRegistry meterRegistry;

    public void recordError(String exceptionType, String endpoint) {
        Counter.builder(ERRORS_COUNTER)
                .description("Total errors handled by the global exception handler")
                .tag(MetricsTags.EXCEPTION, exceptionType)
                .tag(MetricsTags.ENDPOINT, endpoint)
                .register(meterRegistry)
                .increment();
    }
}
