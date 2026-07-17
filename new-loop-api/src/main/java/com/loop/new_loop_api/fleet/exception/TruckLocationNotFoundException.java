package com.loop.new_loop_api.fleet.exception;

import com.loop.new_loop_api.common.exception.NotFoundException;

public class TruckLocationNotFoundException extends NotFoundException {
    public TruckLocationNotFoundException(String licensePlate) {
        super("No location found for truck with license plate: " + licensePlate);
    }
}
