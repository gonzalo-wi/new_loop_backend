package com.loop.new_loop_api.orders.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

import java.util.UUID;

public class OrderNotFoundException extends NotFoundException {
    public OrderNotFoundException(UUID id) {
        super("Order not found: " + id);
    }
}
