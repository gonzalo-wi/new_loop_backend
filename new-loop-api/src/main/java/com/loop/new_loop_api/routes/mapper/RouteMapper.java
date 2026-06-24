package com.loop.new_loop_api.routes.mapper;

import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.routes.dto.CreateRouteRequest;
import com.loop.new_loop_api.routes.dto.RouteResponse;
import com.loop.new_loop_api.routes.dto.UpdateRouteRequest;
import com.loop.new_loop_api.routes.entity.Route;
import org.springframework.stereotype.Component;

@Component
public class RouteMapper {

    public Route toEntity(CreateRouteRequest request, Branch branch) {
        return Route.builder()
                .code(request.getCode())
                .branch(branch)
                .driver(request.getDriver())
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
                .driver(route.getDriver())
                .truckPlate(route.getTruckPlate())
                .active(route.getActive())
                .observations(route.getObservations())
                .createdAt(route.getCreatedAt())
                .updatedAt(route.getUpdatedAt())
                .build();
    }

    public void updateEntity(UpdateRouteRequest request, Route route, Branch branch) {
        if (branch != null)                    route.setBranch(branch);
        if (request.getDriver() != null)       route.setDriver(request.getDriver());
        if (request.getTruckPlate() != null)   route.setTruckPlate(request.getTruckPlate());
        if (request.getObservations() != null) route.setObservations(request.getObservations());
    }
}
