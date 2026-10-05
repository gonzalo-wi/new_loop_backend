package com.loop.new_loop_api.integrations.common.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

import java.util.UUID;

public class NothingToRetryException extends ConflictException {
    public NothingToRetryException(UUID logId) {
        super("Integration log " + logId + " was not retried: it was already sent, its record no longer needs it, "
                + "or there is nothing left to send.");
    }
}
