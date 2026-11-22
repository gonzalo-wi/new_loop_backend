package com.loop.new_loop_api.settlements.exception;

/** 400: routeId or image missing, repeated or malformed (spec 009 FR-10). */
public class InvalidReceiptUploadException extends RuntimeException {
    public static final String MESSAGE = "Falta el reparto o la imagen, o el reparto no es válido";

    public InvalidReceiptUploadException() {
        super(MESSAGE);
    }
}
