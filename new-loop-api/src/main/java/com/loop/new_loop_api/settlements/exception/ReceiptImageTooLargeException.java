package com.loop.new_loop_api.settlements.exception;

/** 413: image above the size limit (spec 009 FR-11). */
public class ReceiptImageTooLargeException extends RuntimeException {
    public static final String MESSAGE = "La imagen supera los 10 MB";

    public ReceiptImageTooLargeException() {
        super(MESSAGE);
    }
}
