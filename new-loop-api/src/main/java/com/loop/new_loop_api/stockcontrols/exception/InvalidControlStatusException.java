package com.loop.new_loop_api.stockcontrols.exception;

import com.loop.new_loop_api.common.exception.ConflictException;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;

import java.util.UUID;

public class InvalidControlStatusException extends ConflictException {
    public InvalidControlStatusException(UUID id, ControlStatus expected, ControlStatus actual) {
        super("Stock control " + id + " must be in status " + expected + " but was " + actual);
    }
}
