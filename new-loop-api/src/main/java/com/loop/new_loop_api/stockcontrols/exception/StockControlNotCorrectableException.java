package com.loop.new_loop_api.stockcontrols.exception;

import com.loop.new_loop_api.common.exception.ConflictException;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;

import java.util.UUID;

public class StockControlNotCorrectableException extends ConflictException {

    public StockControlNotCorrectableException(UUID id, ControlStatus status) {
        super("Stock control " + id + " cannot be corrected in status: " + status);
    }

    public StockControlNotCorrectableException(UUID id, ControlType type) {
        super("Stock control " + id + " cannot be corrected: only " + ControlType.ENTRY + " controls are correctable, but was " + type);
    }
}
