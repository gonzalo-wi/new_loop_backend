package com.loop.new_loop_api.common.exception;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import com.loop.new_loop_api.integrations.aguas.exception.AguasReceiptUploadException;
import com.loop.new_loop_api.settlements.exception.InvalidReceiptUploadException;
import com.loop.new_loop_api.settlements.exception.ReceiptImageTooLargeException;
import com.loop.new_loop_api.settlements.exception.RouteWithoutNumberException;
import com.loop.new_loop_api.settlements.exception.UnsupportedReceiptImageException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.loop.new_loop_api.common.metrics.ErrorMetrics;
import com.loop.new_loop_api.common.metrics.MetricsTags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
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

    @Test
    void should_return409_when_optimisticLockFails() {
        var request = new MockHttpServletRequest("POST", "/orders/1/start");
        var ex = new ObjectOptimisticLockingFailureException("Order", "1");

        var response = handler.handleOptimisticLock(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage())
                .isEqualTo("The record was modified by another user. Reload it and try again.");
    }

    @Test
    void should_return400_when_requestBodyIsUnreadable() {
        var request = new MockHttpServletRequest("POST", "/users");
        var ex = new HttpMessageNotReadableException("Cannot deserialize Role from \"PICKER\"",
                new MockHttpInputMessage(new byte[0]));

        var response = handler.handleUnreadableBody(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage())
                .isEqualTo("Request body is malformed or contains an invalid value");
    }

    // ---- spec 009: driver receipt photos (Spanish messages, standard ApiError) ----

    private static MockHttpServletRequest receiptRequest() {
        return new MockHttpServletRequest("POST", "/settlements/me/receipts");
    }

    private void assertApiError(org.springframework.http.ResponseEntity<com.loop.new_loop_api.common.response.ApiError> response,
                                HttpStatus status, String message, String path) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(status.value());
        assertThat(body.getError()).isEqualTo(status.getReasonPhrase());
        assertThat(body.getMessage()).isEqualTo(message);
        assertThat(body.getPath()).isEqualTo(path);
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    void should_return400WithSpanishMessage_when_receiptUploadIsInvalid() {
        var response = handler.handleInvalidReceiptUpload(new InvalidReceiptUploadException(), receiptRequest());
        assertApiError(response, HttpStatus.BAD_REQUEST, "Falta el reparto o la imagen, o el reparto no es válido",
                "/settlements/me/receipts");
    }

    @Test
    void should_return413WithSpanishMessage_when_receiptImageIsTooLarge() {
        var response = handler.handleReceiptImageTooLarge(new ReceiptImageTooLargeException(), receiptRequest());
        assertApiError(response, HttpStatus.PAYLOAD_TOO_LARGE, "La imagen supera los 10 MB", "/settlements/me/receipts");
    }

    @Test
    void should_return415WithSpanishMessage_when_receiptImageIsUnsupported() {
        var response = handler.handleUnsupportedReceiptImage(new UnsupportedReceiptImageException(), receiptRequest());
        assertApiError(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Solo se aceptan imágenes JPEG o PNG",
                "/settlements/me/receipts");
    }

    @Test
    void should_return422WithSpanishMessage_when_routeHasNoNumber() {
        var response = handler.handleRouteWithoutNumber(new RouteWithoutNumberException(), receiptRequest());
        assertApiError(response, HttpStatus.UNPROCESSABLE_ENTITY, "El reparto no tiene número",
                "/settlements/me/receipts");
    }

    @Test
    void should_return502WithSpanishMessage_when_aguasReceiptUploadFails() {
        var cause = new RuntimeException("connection reset");
        var response = handler.handleAguasReceiptUpload(new AguasReceiptUploadException(cause), receiptRequest());
        assertApiError(response, HttpStatus.BAD_GATEWAY, "No se pudo enviar la foto a Aguas. Probá de nuevo.",
                "/settlements/me/receipts");
    }

    @Test
    void should_keepCauseOutOfTheMessage_when_aguasReceiptUploadFails() {
        var ex = new AguasReceiptUploadException("status 500", new RuntimeException("secret detail"));
        assertThat(ex.getMessage()).isEqualTo("No se pudo enviar la foto a Aguas. Probá de nuevo.");
        assertThat(ex.getCause()).hasMessage("secret detail");
        assertThat(ex.getDetail()).isEqualTo("status 500");
    }

    @Test
    void should_return413WithReceiptMessage_when_bodyExceedsLimitOnReceiptEndpoint() {
        var response = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(268435456L), receiptRequest());
        assertApiError(response, HttpStatus.PAYLOAD_TOO_LARGE, "La imagen supera los 10 MB", "/settlements/me/receipts");
    }

    @Test
    void should_return413WithGenericSpanishMessage_when_bodyExceedsLimitElsewhere() {
        var request = new MockHttpServletRequest("POST", "/app/upload");
        var response = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(268435456L), request);
        assertApiError(response, HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el tamaño máximo permitido",
                "/app/upload");
    }

    @Test
    void should_recordErrorMetric_when_receiptExceptionIsHandled() {
        handler.handleRouteWithoutNumber(new RouteWithoutNumberException(), receiptRequest());
        var counter = meterRegistry.find("loop_errors_total")
                .tag(MetricsTags.EXCEPTION, "RouteWithoutNumberException").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void should_return413WithReceiptMessage_when_receiptPathHasTrailingSlash() {
        var request = new MockHttpServletRequest("POST", "/settlements/me/receipts/");
        var response = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(268435456L), request);
        assertThat(response.getBody().getMessage()).isEqualTo("La imagen supera los 10 MB");
    }

    @Test
    void should_return413WithReceiptMessage_when_appRunsUnderAContextPath() {
        var request = new MockHttpServletRequest("POST", "/loop/settlements/me/receipts");
        request.setContextPath("/loop");
        var response = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(268435456L), request);
        assertThat(response.getBody().getMessage()).isEqualTo("La imagen supera los 10 MB");
    }

    @Test
    void should_return400WithReceiptMessage_when_multipartBodyIsCorruptOnReceiptEndpoint() {
        var response = handler.handleMultipart(new org.springframework.web.multipart.MultipartException("corrupt"),
                receiptRequest());
        assertApiError(response, HttpStatus.BAD_REQUEST, "Falta el reparto o la imagen, o el reparto no es válido",
                "/settlements/me/receipts");
    }

    @Test
    void should_return400_when_corruptMultipartOnReceiptPathWithTrailingSlash() {
        var request = new MockHttpServletRequest("POST", "/settlements/me/receipts/");
        var response = handler.handleMultipart(new org.springframework.web.multipart.MultipartException("corrupt"), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_keepGeneric500_when_corruptMultipartOnAnotherPath() {
        var request = new MockHttpServletRequest("POST", "/app/upload");
        var response = handler.handleMultipart(new org.springframework.web.multipart.MultipartException("corrupt"), request);
        assertApiError(response, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "/app/upload");
    }

    @Test
    void should_stillResolveMaxUploadSizeToTheTooLargeHandler_notTheMultipartOne() {
        var resolver = new org.springframework.web.method.annotation.ExceptionHandlerMethodResolver(
                GlobalExceptionHandler.class);
        var method = resolver.resolveMethodByThrowable(new MaxUploadSizeExceededException(1L));
        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("handleMaxUploadSize");
    }
}
