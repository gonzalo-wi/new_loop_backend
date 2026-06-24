package com.loop.new_loop_api.routes.mapper;

import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.routes.dto.CreateRouteRequest;
import com.loop.new_loop_api.routes.dto.RouteResponse;
import com.loop.new_loop_api.routes.dto.UpdateRouteRequest;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.users.entity.User;
import org.springframework.stereotype.Component;

@Component
public class RouteMapper {

    public Route toEntity(CreateRouteRequest request, Branch branch, User driver) {
        return Route.builder()
                .code(request.getCode())
                .branch(branch)
                .driver(driver)
                .truckPlate(request.getTruckPlate())
                .observations(request.getObservations())
                .active(true)
                .build();
    }

    public RouteResponse toResponse(Route route) {
        return RouteResponse.builder()
                .id(route.getId())
                .code(route.getCode())
                .branchId(route.getBranch().getId())
                .branchName(route.getBranch().getName())
                .branchCode(route.getBranch().getCode())
                .driverId(route.getDriver() != null ? route.getDriver().getId() : null)
                .driverName(route.getDriver() != null ? route.getDriver().getName() : null)
                .truckPlate(route.getTruckPlate())
                .active(route.getActive())
                .observations(route.getObservations())
                .createdAt(route.getCreatedAt())
                .updatedAt(route.getUpdatedAt())
                .build();
    }

    public void updateEntity(UpdateRouteRequest request, Route route, Branch branch, User driver) {
        if (branch  != null)                   route.setBranch(branch);
        if (driver  != null)                   route.setDriver(driver);
        if (request.getTruckPlate()  != null)  route.setTruckPlate(request.getTruckPlate());
        if (request.getObservations() != null) route.setObservations(request.getObservations());
    }
}
