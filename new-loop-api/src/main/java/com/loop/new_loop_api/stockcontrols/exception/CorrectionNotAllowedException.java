package com.loop.new_loop_api.stockcontrols.exception;

import com.loop.new_loop_api.common.exception.ForbiddenException;

public class CorrectionNotAllowedException extends ForbiddenException {
    public CorrectionNotAllowedException() {
        super("Only users with role SUPERVISOR can correct a stock control");
    }
}
