package com.loop.new_loop_api.loadforecast.controller;

import com.loop.new_loop_api.common.response.ApiResponse;
import com.loop.new_loop_api.loadforecast.dto.LoadForecastResponse;
import com.loop.new_loop_api.loadforecast.exception.InvalidRouteNumberException;
import com.loop.new_loop_api.loadforecast.service.iService.LoadForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/load-forecast")
@RequiredArgsConstructor
public class LoadForecastController {

    private final LoadForecastService loadForecastService;

    /** Optional on purpose: a missing number answers 400 through our own error instead of a generic 500. */
    @GetMapping
    public ResponseEntity<ApiResponse<LoadForecastResponse>> getForecast(
            @RequestParam(required = false) Long routeNumber) {
        if (routeNumber == null || routeNumber <= 0) {
            throw new InvalidRouteNumberException();
        }
        return ResponseEntity.ok(ApiResponse.ok(loadForecastService.forecast(routeNumber),
                "Load forecast retrieved successfully"));
    }
}
