package com.loop.new_loop_api.routes.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UpdateRouteRequest {

    @Size(max = 50, message = "Code must not exceed 50 characters")
    private String code;

    private UUID branchId;

    private UUID driverId;

    @Size(max = 20, message = "Truck plate must not exceed 20 characters")
    private String truckPlate;

    @Size(max = 500, message = "Observations must not exceed 500 characters")
    private String observations;
}
