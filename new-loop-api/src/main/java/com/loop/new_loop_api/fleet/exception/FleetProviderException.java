package com.loop.new_loop_api.fleet.exception;

/** Thrown when the Powerfleet service is unreachable or returns an unexpected response. */
public class FleetProviderException extends RuntimeException {
    public FleetProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
