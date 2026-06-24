package com.loop.new_loop_api.routes.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

import java.util.UUID;

public class RouteNotFoundException extends NotFoundException {
    public RouteNotFoundException(UUID id) {
        super("Route not found with id: " + id);
    }
}
