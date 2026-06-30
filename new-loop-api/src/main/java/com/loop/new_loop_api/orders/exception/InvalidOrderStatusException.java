package com.loop.new_loop_api.orders.exception;

import com.loop.new_loop_api.common.exception.ConflictException;
import com.loop.new_loop_api.orders.entity.OrderStatus;

import java.util.UUID;

public class InvalidOrderStatusException extends ConflictException {
    public InvalidOrderStatusException(UUID id, OrderStatus expected, OrderStatus actual) {
        super("Order " + id + " must be in status " + expected + " but was " + actual);
    }
}
