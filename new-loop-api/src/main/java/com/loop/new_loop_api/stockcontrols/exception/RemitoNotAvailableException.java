package com.loop.new_loop_api.stockcontrols.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

import java.util.UUID;

public class RemitoNotAvailableException extends ConflictException {
    public RemitoNotAvailableException(UUID id, String reason) {
        super("Remito not available for stock control " + id + ": " + reason);
    }
}
