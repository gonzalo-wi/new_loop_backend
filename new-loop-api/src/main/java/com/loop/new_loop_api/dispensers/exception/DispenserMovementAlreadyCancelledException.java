package com.loop.new_loop_api.dispensers.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

import java.util.UUID;

public class DispenserMovementAlreadyCancelledException extends ConflictException {
    public DispenserMovementAlreadyCancelledException(UUID id) {
        super("Dispenser movement " + id + " is already cancelled");
    }
}
