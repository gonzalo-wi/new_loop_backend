package com.loop.new_loop_api.loadforecast.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

public class ForecastRouteNotFoundException extends NotFoundException {
    public ForecastRouteNotFoundException(long routeNumber) {
        super("No single active route found with number: " + routeNumber);
    }
}
