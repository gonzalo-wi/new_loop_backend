package com.loop.new_loop_api.products.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

import java.util.UUID;

public class ProductNotFoundException extends NotFoundException {
    public ProductNotFoundException(UUID id) {
        super("Product not found with id: " + id);
    }
}
