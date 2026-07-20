package com.loop.new_loop_api.stockcontrols.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

import java.time.LocalDate;
import java.util.UUID;

public class ExitControlNotFoundException extends NotFoundException {
    public ExitControlNotFoundException(UUID routeId, LocalDate date) {
        super("No EXIT control found for route " + routeId + " on " + date);
    }
}
