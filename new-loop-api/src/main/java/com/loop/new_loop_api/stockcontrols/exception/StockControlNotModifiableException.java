package com.loop.new_loop_api.stockcontrols.exception;

import com.loop.new_loop_api.common.exception.ConflictException;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;

import java.util.UUID;

public class StockControlNotModifiableException extends ConflictException {
    public StockControlNotModifiableException(UUID id, ControlStatus status) {
        super("Stock control " + id + " cannot be modified in status: " + status);
    }
}
