package com.loop.new_loop_api.settlements.exception;

/** 422: the route has no route number (spec 009 FR-15). */
public class RouteWithoutNumberException extends RuntimeException {
    public static final String MESSAGE = "El reparto no tiene número";

    public RouteWithoutNumberException() {
        super(MESSAGE);
    }
}
