package com.loop.new_loop_api.fleet.service.iService;

import com.loop.new_loop_api.fleet.dto.TruckLocationResponse;

public interface FleetLocationService {

    TruckLocationResponse getLocation(String licensePlate);
}
