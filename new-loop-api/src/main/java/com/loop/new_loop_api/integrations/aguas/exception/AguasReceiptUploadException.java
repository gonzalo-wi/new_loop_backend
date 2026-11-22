package com.loop.new_loop_api.integrations.aguas.exception;

/** 502: the receipt photo did not reach Aguas (spec 009 FR-16). The detail is for logs, never for the client. */
public class AguasReceiptUploadException extends RuntimeException {
    public static final String MESSAGE = "No se pudo enviar la foto a Aguas. Probá de nuevo.";

    private final String detail;

    public AguasReceiptUploadException(Throwable cause) {
        this(cause.getClass().getSimpleName(), cause);
    }

    public AguasReceiptUploadException(String detail, Throwable cause) {
        super(MESSAGE, cause);
        this.detail = detail;
    }

    public AguasReceiptUploadException(String detail) {
        super(MESSAGE);
        this.detail = detail;
    }

    public String getDetail() {
        return detail;
    }
}
