package com.loop.new_loop_api.stockcontrols.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

public class InactiveProductException extends ConflictException {
    public InactiveProductException(String code) {
        super("Product is inactive and cannot be used in a control: " + code);
    }
}
