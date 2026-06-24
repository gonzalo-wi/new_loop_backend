package com.loop.new_loop_api.routes.controller;

import com.loop.new_loop_api.common.response.ApiResponse;
import com.loop.new_loop_api.routes.dto.CreateRouteRequest;
import com.loop.new_loop_api.routes.dto.RouteResponse;
import com.loop.new_loop_api.routes.dto.UpdateRouteRequest;
import com.loop.new_loop_api.routes.service.iService.RouteService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
@Tag(name = "Routes")
public class RouteController {

    private final RouteService routeService;

    @PostMapping
    public ResponseEntity<ApiResponse<RouteResponse>> createRoute(@Valid @RequestBody CreateRouteRequest request) {
        var response = routeService.createRoute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Route created successfully"));
    }

    @Parameters({
            @Parameter(name = "page", example = "0"),
            @Parameter(name = "size", example = "20"),
            @Parameter(name = "sort", example = "code,asc")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<RouteResponse>>> getAllRoutes(
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(routeService.getAllRoutes(pageable), "Routes retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteResponse>> getRouteById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(routeService.getRouteById(id), "Route retrieved successfully"));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteResponse>> updateRoute(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRouteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(routeService.updateRoute(id, request), "Route updated successfully"));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateRoute(@PathVariable UUID id) {
        routeService.deactivateRoute(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Route deactivated successfully"));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activateRoute(@PathVariable UUID id) {
        routeService.activateRoute(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Route activated successfully"));
    }
}
