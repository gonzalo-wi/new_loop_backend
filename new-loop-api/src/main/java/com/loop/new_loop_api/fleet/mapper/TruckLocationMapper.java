package com.loop.new_loop_api.fleet.mapper;

import com.loop.new_loop_api.fleet.dto.TruckLocationResponse;
import com.loop.new_loop_api.integrations.powerFleet.dto.PowerfleetVehicle;
import org.springframework.stereotype.Component;

@Component
public class TruckLocationMapper {

    public TruckLocationResponse toResponse(PowerfleetVehicle vehicle) {
        return TruckLocationResponse.builder()
                .licensePlate(vehicle.getLicensePlate())
                .lat(vehicle.getLat())
                .lng(vehicle.getLng())
                .address(vehicle.getAddress())
                .speed(vehicle.getSpeed())
                .engineOn(vehicle.getEngineOn() != null && vehicle.getEngineOn() == 1)
                .stateIcon(vehicle.getStateIcon())
                .driver(vehicle.getDriver())
                .gpsDateTime(vehicle.getGpsDateTime())
                .direction(vehicle.getDirection())
                .build();
    }
}
