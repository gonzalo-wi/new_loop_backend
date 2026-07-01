package com.loop.new_loop_api.dispensers.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

import java.util.UUID;

public class AguasDeleteFailedException extends ConflictException {
    public AguasDeleteFailedException(UUID id) {
        super("Could not delete dispenser movement " + id + " in Aguas. See integration logs for details.");
    }
}
