package com.loop.new_loop_api.settlements.controller;

import com.loop.new_loop_api.settlements.dto.ReceiptUpload;
import com.loop.new_loop_api.settlements.exception.InvalidReceiptUploadException;
import com.loop.new_loop_api.settlements.exception.UnsupportedReceiptImageException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;

/**
 * Reads the receipt upload by hand instead of with {@code @RequestPart}: Spring's own binding errors have English
 * messages, their own order, and silently take the first of repeated parts (spec 009 FR-2, FR-10, FR-12).
 * Every other part and parameter, {@code fecha} included, is ignored (FR-3).
 */
final class ReceiptUploadRequestReader {

    static final String ROUTE_ID_PART = "routeId";
    static final String IMAGE_PART = "image";

    private ReceiptUploadRequestReader() {
    }

    static ReceiptUpload read(HttpServletRequest request) {
        // Unwrap: security filters can wrap the request, hiding the multipart one underneath.
        MultipartHttpServletRequest multipart = WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
        if (multipart == null) {
            throw UnsupportedReceiptImageException.notMultipart();
        }
        UUID routeId = readRouteId(multipart);
        MultipartFile image = readImage(multipart);
        return new ReceiptUpload(routeId, image.getContentType(), image.getSize(), image);
    }

    private static UUID readRouteId(MultipartHttpServletRequest request) {
        String[] values = request.getParameterValues(ROUTE_ID_PART);
        if (values == null || values.length != 1) throw new InvalidReceiptUploadException();
        try {
            return UUID.fromString(values[0].trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidReceiptUploadException();
        }
    }

    /** A present zero-byte file is not "missing": the service rejects it as an unsupported image. */
    private static MultipartFile readImage(MultipartHttpServletRequest request) {
        List<MultipartFile> files = request.getFiles(IMAGE_PART);
        if (files.size() != 1) throw new InvalidReceiptUploadException();
        return files.get(0);
    }
}
