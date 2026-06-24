package com.loop.new_loop_api.routes.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

import java.util.UUID;

public class UserIsNotDriverException extends ConflictException {
    public UserIsNotDriverException(UUID userId) {
        super("User " + userId + " does not have the REPARTIDOR role");
    }
}
