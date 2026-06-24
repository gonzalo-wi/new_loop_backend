package com.loop.new_loop_api.routes.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.branches.exception.BranchNotFoundException;
import com.loop.new_loop_api.branches.repository.BranchRepository;
import com.loop.new_loop_api.routes.dto.CreateRouteRequest;
import com.loop.new_loop_api.routes.dto.RouteResponse;
import com.loop.new_loop_api.routes.dto.UpdateRouteRequest;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.exception.RouteCodeAlreadyExistsException;
import com.loop.new_loop_api.routes.exception.RouteNotFoundException;
import com.loop.new_loop_api.routes.mapper.RouteMapper;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import com.loop.new_loop_api.routes.service.iService.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RouteServiceImpl implements RouteService {

    private final RouteRepository  routeRepository;
    private final RouteMapper      routeMapper;
    private final BranchRepository branchRepository;
    private final AuditService     auditService;

    @Override
    @Transactional
    public RouteResponse createRoute(CreateRouteRequest request) {
        if (routeRepository.existsByCode(request.getCode())) {
            throw new RouteCodeAlreadyExistsException(request.getCode());
        }
        var route    = routeMapper.toEntity(request, findBranchById(request.getBranchId()));
        var response = routeMapper.toResponse(routeRepository.save(route));
        auditService.register("CREATE_ROUTE", "Route", response.getId(), null, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RouteResponse> getAllRoutes(Pageable pageable) {
        return routeRepository.findAll(pageable)
                .map(routeMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public RouteResponse getRouteById(UUID id) {
        return routeMapper.toResponse(findRouteById(id));
    }

    @Override
    @Transactional
    public RouteResponse updateRoute(UUID id, UpdateRouteRequest request) {
        var route    = findRouteById(id);
        var oldValue = routeMapper.toResponse(route);
        var branch   = request.getBranchId() != null ? findBranchById(request.getBranchId()) : null;
        routeMapper.updateEntity(request, route, branch);
        var response = routeMapper.toResponse(routeRepository.save(route));
        auditService.register("UPDATE_ROUTE", "Route", id, oldValue, response);
        return response;
    }

    @Override
    @Transactional
    public void deactivateRoute(UUID id) {
        var route = findRouteById(id);
        route.setActive(false);
        routeRepository.save(route);
        auditService.register("DEACTIVATE_ROUTE", "Route", id, null, null);
    }

    @Override
    @Transactional
    public void activateRoute(UUID id) {
        var route = findRouteById(id);
        route.setActive(true);
        routeRepository.save(route);
        auditService.register("ACTIVATE_ROUTE", "Route", id, null, null);
    }

    private Route findRouteById(UUID id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new RouteNotFoundException(id));
    }

    private Branch findBranchById(UUID id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException(id));
    }
}
