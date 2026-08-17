package com.loop.new_loop_api.integrations.common.metrics;

import com.loop.new_loop_api.common.metrics.MetricsTags;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationCallMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private IntegrationCallMetrics integrationCallMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        integrationCallMetrics = new IntegrationCallMetrics(meterRegistry);
    }

    @Test
    void should_incrementCallsCounter_taggedIntegrationAndSuccessResult_when_recordSuccessCalled() {
        integrationCallMetrics.recordSuccess(IntegrationName.AGUAS);

        var counter = meterRegistry.find("loop_integration_calls_total")
                .tag(MetricsTags.INTEGRATION, "AGUAS")
                .tag(MetricsTags.RESULT, "SUCCESS")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void should_incrementCallsCounter_taggedIntegrationAndErrorResult_when_recordErrorCalled() {
        integrationCallMetrics.recordError(IntegrationName.ODOO);

        var counter = meterRegistry.find("loop_integration_calls_total")
                .tag(MetricsTags.INTEGRATION, "ODOO")
                .tag(MetricsTags.RESULT, "ERROR")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void should_keepCountersIndependent_perIntegrationAndResult() {
        integrationCallMetrics.recordSuccess(IntegrationName.AGUAS);
        integrationCallMetrics.recordSuccess(IntegrationName.AGUAS);
        integrationCallMetrics.recordError(IntegrationName.AGUAS);
        integrationCallMetrics.recordSuccess(IntegrationName.JMOBILE);

        var aguasSuccess = meterRegistry.find("loop_integration_calls_total")
                .tag(MetricsTags.INTEGRATION, "AGUAS").tag(MetricsTags.RESULT, "SUCCESS").counter();
        var aguasError = meterRegistry.find("loop_integration_calls_total")
                .tag(MetricsTags.INTEGRATION, "AGUAS").tag(MetricsTags.RESULT, "ERROR").counter();
        var jmobileSuccess = meterRegistry.find("loop_integration_calls_total")
                .tag(MetricsTags.INTEGRATION, "JMOBILE").tag(MetricsTags.RESULT, "SUCCESS").counter();

        assertThat(aguasSuccess.count()).isEqualTo(2.0);
        assertThat(aguasError.count()).isEqualTo(1.0);
        assertThat(jmobileSuccess.count()).isEqualTo(1.0);
    }
}
