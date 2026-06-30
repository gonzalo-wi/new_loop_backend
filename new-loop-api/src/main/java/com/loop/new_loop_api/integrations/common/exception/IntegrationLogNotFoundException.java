package com.loop.new_loop_api.integrations.common.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

import java.util.UUID;

public class IntegrationLogNotFoundException extends NotFoundException {
    public IntegrationLogNotFoundException(UUID id) {
        super("Integration log not found: " + id);
    }
}
