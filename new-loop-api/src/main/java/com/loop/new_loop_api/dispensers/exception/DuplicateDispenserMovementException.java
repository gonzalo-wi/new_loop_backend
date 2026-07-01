package com.loop.new_loop_api.dispensers.exception;

import com.loop.new_loop_api.common.exception.ConflictException;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;

import java.time.LocalDate;

public class DuplicateDispenserMovementException extends ConflictException {
    public DuplicateDispenserMovementException(DispenserMovementType type, String routeCode, LocalDate date) {
        super("A " + type + " dispenser movement already exists for route " + routeCode + " on " + date
                + ". Edit or cancel it instead of creating a duplicate.");
    }
}
