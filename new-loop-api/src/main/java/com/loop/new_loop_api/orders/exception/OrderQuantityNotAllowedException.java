package com.loop.new_loop_api.orders.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

public class OrderQuantityNotAllowedException extends ConflictException {
    public OrderQuantityNotAllowedException(String productCode, String reason) {
        super("Product " + productCode + ": " + reason);
    }
}
