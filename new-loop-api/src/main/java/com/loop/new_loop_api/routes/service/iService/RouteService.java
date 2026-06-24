package com.loop.new_loop_api.routes.service.iService;

import com.loop.new_loop_api.routes.dto.CreateRouteRequest;
import com.loop.new_loop_api.routes.dto.RouteResponse;
import com.loop.new_loop_api.routes.dto.UpdateRouteRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RouteService {

    RouteResponse createRoute(CreateRouteRequest request);
    Page<RouteResponse> getAllRoutes(Pageable pageable);
    RouteResponse getRouteById(UUID id);
    RouteResponse updateRoute(UUID id, UpdateRouteRequest request);
    void deactivateRoute(UUID id);
    void activateRoute(UUID id);
}
