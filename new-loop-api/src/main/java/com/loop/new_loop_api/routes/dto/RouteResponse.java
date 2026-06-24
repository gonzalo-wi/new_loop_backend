package com.loop.new_loop_api.routes.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class RouteResponse {

    private UUID          id;
    private String        code;
    private UUID          branchId;
    private String        branchName;
    private String        branchCode;
    private UUID          driverId;
    private String        driverName;
    private String        truckPlate;
    private Boolean       active;
    private String        observations;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
