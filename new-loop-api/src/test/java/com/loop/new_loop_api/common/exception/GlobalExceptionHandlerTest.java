package com.loop.new_loop_api.common.exception;

import com.loop.new_loop_api.common.metrics.ErrorMetrics;
import com.loop.new_loop_api.common.metrics.MetricsTags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private SimpleMeterRegistry meterRegistry;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        handler = new GlobalExceptionHandler(new ErrorMetrics(meterRegistry));
    }

    @Test
    void should_incrementLoopErrorsTotal_when_notFoundExceptionIsHandled() {
        var request = new MockHttpServletRequest("GET", "/stock-controls/123");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/stock-controls/{id}");
        var ex = new NotFoundException("Stock control not found");

        var response = handler.handleNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Stock control not found");

        var counter = meterRegistry.find("loop_errors_total")
                .tag(MetricsTags.EXCEPTION, "NotFoundException")
                .tag(MetricsTags.ENDPOINT, "/stock-controls/{id}")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void should_incrementLoopErrorsTotal_when_genericExceptionIsHandled() {
        var request = new MockHttpServletRequest("GET", "/dispenser-movements");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/dispenser-movements");
        var ex = new RuntimeException("boom");

        var response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        var counter = meterRegistry.find("loop_errors_total")
                .tag(MetricsTags.EXCEPTION, "RuntimeException")
                .tag(MetricsTags.ENDPOINT, "/dispenser-movements")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void should_useRawRequestURI_when_noBestMatchingPatternAttributeIsPresent() {
        var request = new MockHttpServletRequest("GET", "/unmatched/path");
        var ex = new ConflictException("Duplicate control");

        handler.handleConflict(ex, request);

        var counter = meterRegistry.find("loop_errors_total")
                .tag(MetricsTags.EXCEPTION, "ConflictException")
                .tag(MetricsTags.ENDPOINT, "/unmatched/path")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
