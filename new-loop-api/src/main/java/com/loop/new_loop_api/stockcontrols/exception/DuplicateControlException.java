package com.loop.new_loop_api.stockcontrols.exception;

import com.loop.new_loop_api.common.exception.ConflictException;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;

import java.time.LocalDate;
import java.util.UUID;

public class DuplicateControlException extends ConflictException {
    public DuplicateControlException(ControlType type, UUID routeId, LocalDate controlDate) {
        super("A " + type + " control already exists for route " + routeId + " on " + controlDate);
    }
}
