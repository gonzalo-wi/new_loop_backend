package com.loop.new_loop_api.dispensers.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

import java.util.UUID;

public class DispenserMovementNotFoundException extends NotFoundException {
    public DispenserMovementNotFoundException(UUID id) {
        super("Dispenser movement not found: " + id);
    }
}
