package com.loop.new_loop_api.dispensers.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

import java.util.UUID;

public class MovementHasLaterReloadException extends ConflictException {
    public MovementHasLaterReloadException(UUID movementId) {
        super("Dispenser movement " + movementId + " has a later reload on the same route and date."
                + " Only the last load of the day can be cancelled or corrected.");
    }
}
