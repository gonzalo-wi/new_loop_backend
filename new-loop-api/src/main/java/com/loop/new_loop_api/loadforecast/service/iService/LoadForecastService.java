package com.loop.new_loop_api.loadforecast.service.iService;

import com.loop.new_loop_api.loadforecast.dto.LoadForecastResponse;

public interface LoadForecastService {

    /** What the route usually takes on the next delivery day, from its own past exit controls. */
    LoadForecastResponse forecast(long routeNumber);
}
