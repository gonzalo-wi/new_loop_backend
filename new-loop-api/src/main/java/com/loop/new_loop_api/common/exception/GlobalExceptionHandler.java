package com.loop.new_loop_api.common.exception;

import com.loop.new_loop_api.appupdate.exception.InvalidApkUploadException;
import com.loop.new_loop_api.common.metrics.ErrorMetrics;
import com.loop.new_loop_api.common.response.ApiError;
import com.loop.new_loop_api.dispensers.exception.InvalidBaggedSerialsException;
import com.loop.new_loop_api.dispensers.exception.InvalidStatsRangeException;
import com.loop.new_loop_api.fleet.exception.FleetProviderException;
import com.loop.new_loop_api.integrations.aguas.exception.AguasReceiptUploadException;
import com.loop.new_loop_api.loadforecast.exception.InvalidRouteNumberException;
import com.loop.new_loop_api.performance.exception.InvalidMetricsRangeException;
import com.loop.new_loop_api.settlements.exception.InvalidReceiptUploadException;
import com.loop.new_loop_api.settlements.exception.ReceiptImageTooLargeException;
import com.loop.new_loop_api.settlements.exception.RouteWithoutNumberException;
import com.loop.new_loop_api.settlements.exception.UnsupportedReceiptImageException;
import com.loop.new_loop_api.users.exception.BranchRequiredForRoleException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.HandlerMapping;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String CONCURRENT_MODIFICATION_MESSAGE = "The record was modified by another user. Reload it and try again.";
    private static final String UNREADABLE_BODY_MESSAGE         = "Request body is malformed or contains an invalid value";

    private static final String RECEIPT_UPLOAD_PATH = "/settlements/me/receipts";
    private static final String UPLOAD_TOO_LARGE_MESSAGE = "El archivo supera el tamaño máximo permitido";

    private final ErrorMetrics errorMetrics;

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), ex, request);
    }

    /** Fallback for versioned entities whose service doesn't translate the conflict into a domain exception. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, CONCURRENT_MODIFICATION_MESSAGE, ex, request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return buildResponse(HttpStatus.BAD_REQUEST, message, ex, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", ex, request);
    }

    /** Invalid JSON or a value Jackson can't bind (e.g. an unknown role) is a client error, not a 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, UNREADABLE_BODY_MESSAGE, ex, request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid username or password", ex, request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabledUser(DisabledException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "User account is inactive", ex, request);
    }

    @ExceptionHandler(FleetProviderException.class)
    public ResponseEntity<ApiError> handleFleetProvider(FleetProviderException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(InvalidApkUploadException.class)
    public ResponseEntity<ApiError> handleInvalidApkUpload(InvalidApkUploadException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(BranchRequiredForRoleException.class)
    public ResponseEntity<ApiError> handleBranchRequired(BranchRequiredForRoleException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(InvalidStatsRangeException.class)
    public ResponseEntity<ApiError> handleInvalidStatsRange(InvalidStatsRangeException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(InvalidRouteNumberException.class)
    public ResponseEntity<ApiError> handleInvalidRouteNumber(InvalidRouteNumberException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(InvalidMetricsRangeException.class)
    public ResponseEntity<ApiError> handleInvalidMetricsRange(InvalidMetricsRangeException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(InvalidBaggedSerialsException.class)
    public ResponseEntity<ApiError> handleInvalidBaggedSerials(InvalidBaggedSerialsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(InvalidReceiptUploadException.class)
    public ResponseEntity<ApiError> handleInvalidReceiptUpload(InvalidReceiptUploadException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(ReceiptImageTooLargeException.class)
    public ResponseEntity<ApiError> handleReceiptImageTooLarge(ReceiptImageTooLargeException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(UnsupportedReceiptImageException.class)
    public ResponseEntity<ApiError> handleUnsupportedReceiptImage(UnsupportedReceiptImageException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(RouteWithoutNumberException.class)
    public ResponseEntity<ApiError> handleRouteWithoutNumber(RouteWithoutNumberException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(AguasReceiptUploadException.class)
    public ResponseEntity<ApiError> handleAguasReceiptUpload(AguasReceiptUploadException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex, request);
    }

    /**
     * Raised by the DispatcherServlet before a handler is chosen, so it has to be handled globally. The receipt
     * endpoint gets the receipt message; any other upload endpoint used to fall into the generic 500.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String message = RECEIPT_UPLOAD_PATH.equals(pathWithinApplication(request))
                ? ReceiptImageTooLargeException.MESSAGE
                : UPLOAD_TOO_LARGE_MESSAGE;
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, message, ex, request);
    }

    /**
     * A multipart Content-Type with a body that cannot be parsed. On the receipts path it is the client's
     * malformed upload (400); everywhere else it keeps the generic 500 it always had. Spring picks the closest
     * handler, so {@link MaxUploadSizeExceededException} (a subclass) still goes to its own 413 handler.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> handleMultipart(MultipartException ex, HttpServletRequest request) {
        if (RECEIPT_UPLOAD_PATH.equals(pathWithinApplication(request))) {
            return buildResponse(HttpStatus.BAD_REQUEST, InvalidReceiptUploadException.MESSAGE, ex, request);
        }
        return handleGeneric(ex, request);
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ApiError> handleInvalidSort(InvalidDataAccessApiUsageException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid sort parameter. Use format: field,asc or field,desc", ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", ex, request);
    }

    /** Request path without the context path and without trailing slashes, so "/x/" and "/ctx/x" match "/x". */
    private static String pathWithinApplication(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private ResponseEntity<ApiError> buildResponse(HttpStatus status, String message, Exception ex, HttpServletRequest request) {
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();
        errorMetrics.recordError(ex.getClass().getSimpleName(), resolveEndpoint(request));
        return ResponseEntity.status(status).body(error);
    }

    /** Route pattern (e.g. "/stock-controls/{id}"), not the resolved path, to keep the tag low-cardinality. */
    private String resolveEndpoint(HttpServletRequest request) {
        var pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : request.getRequestURI();
    }
}
