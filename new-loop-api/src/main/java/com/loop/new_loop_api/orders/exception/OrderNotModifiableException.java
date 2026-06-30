package com.loop.new_loop_api.orders.exception;

import com.loop.new_loop_api.common.exception.ConflictException;
import com.loop.new_loop_api.orders.entity.OrderStatus;

import java.util.UUID;

public class OrderNotModifiableException extends ConflictException {
    public OrderNotModifiableException(UUID id, OrderStatus status) {
        super("Order " + id + " cannot be modified in status: " + status);
    }
}
