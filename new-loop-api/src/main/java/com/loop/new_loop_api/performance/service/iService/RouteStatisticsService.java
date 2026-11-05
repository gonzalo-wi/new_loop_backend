package com.loop.new_loop_api.performance.service.iService;

import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface RouteStatisticsService {

    RouteStatisticsResponse getRouteStatistics(LocalDate from, LocalDate to, UUID branchId);
}
