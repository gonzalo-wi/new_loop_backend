package com.loop.new_loop_api.settlements.exception;

/** 415: not multipart (FR-12), or declared type / real format not allowed (FR-13). */
public class UnsupportedReceiptImageException extends RuntimeException {
    public static final String MESSAGE = "Solo se aceptan imágenes JPEG o PNG";
    public static final String NOT_MULTIPART_MESSAGE = "La foto debe enviarse como multipart/form-data";

    public UnsupportedReceiptImageException() {
        super(MESSAGE);
    }

    private UnsupportedReceiptImageException(String message) {
        super(message);
    }

    public static UnsupportedReceiptImageException notMultipart() {
        return new UnsupportedReceiptImageException(NOT_MULTIPART_MESSAGE);
    }
}
