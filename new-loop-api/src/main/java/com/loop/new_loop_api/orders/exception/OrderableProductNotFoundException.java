package com.loop.new_loop_api.orders.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

import java.util.UUID;

public class OrderableProductNotFoundException extends NotFoundException {
    public OrderableProductNotFoundException(UUID id) {
        super("Orderable product not found: " + id);
    }
}
