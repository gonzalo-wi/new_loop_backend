package com.loop.new_loop_api.stockcontrols.exception;

import com.loop.new_loop_api.common.exception.ForbiddenException;

public class ControlEditNotAllowedException extends ForbiddenException {
    public ControlEditNotAllowedException() {
        super("Only users with role CONTROLADOR, SUPERVISOR or ADMIN can edit a stock control");
    }
}
