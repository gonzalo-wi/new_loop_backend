package com.loop.new_loop_api.loadforecast.exception;

public class InvalidRouteNumberException extends RuntimeException {
    public InvalidRouteNumberException() {
        super("routeNumber is required and must be a positive integer");
    }
}
