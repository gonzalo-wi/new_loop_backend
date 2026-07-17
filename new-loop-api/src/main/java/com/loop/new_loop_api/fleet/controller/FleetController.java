package com.loop.new_loop_api.fleet.controller;

import com.loop.new_loop_api.common.response.ApiResponse;
import com.loop.new_loop_api.fleet.dto.TruckLocationResponse;
import com.loop.new_loop_api.fleet.service.iService.FleetLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fleet")
@RequiredArgsConstructor
public class FleetController {

    private final FleetLocationService fleetLocationService;

    @GetMapping("/location/{licensePlate}")
    public ResponseEntity<ApiResponse<TruckLocationResponse>> getLocation(@PathVariable String licensePlate) {
        var response = fleetLocationService.getLocation(licensePlate);
        return ResponseEntity.ok(ApiResponse.ok(response, "Truck location retrieved successfully"));
    }
}
