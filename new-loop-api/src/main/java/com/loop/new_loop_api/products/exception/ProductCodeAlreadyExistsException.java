package com.loop.new_loop_api.products.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

public class ProductCodeAlreadyExistsException extends ConflictException {
    public ProductCodeAlreadyExistsException(String code) {
        super("Product code already exists: " + code);
    }
}
