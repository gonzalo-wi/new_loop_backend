package com.loop.new_loop_api.routes.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

public class RouteCodeAlreadyExistsException extends ConflictException {
    public RouteCodeAlreadyExistsException(String code) {
        super("Route code already exists: " + code);
    }
}
