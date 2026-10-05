package com.loop.new_loop_api.orders.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

import java.util.UUID;

public class OrderModifiedConcurrentlyException extends ConflictException {
    public OrderModifiedConcurrentlyException(UUID id) {
        super("Order " + id + " was just taken or updated by another user. Reload it and try again.");
    }
}
