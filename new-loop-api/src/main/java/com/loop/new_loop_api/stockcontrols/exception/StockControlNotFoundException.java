package com.loop.new_loop_api.stockcontrols.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

import java.util.UUID;

public class StockControlNotFoundException extends NotFoundException {
    public StockControlNotFoundException(UUID id) {
        super("Stock control not found with id: " + id);
    }
}
