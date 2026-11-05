package com.loop.new_loop_api.performance.service;

import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.performance.domain.MetricsRange;
import com.loop.new_loop_api.performance.domain.MetricsScope;
import com.loop.new_loop_api.performance.domain.RouteStatisticsCalculator;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse;
import com.loop.new_loop_api.performance.repository.RouteStatisticsRepository;
import com.loop.new_loop_api.performance.service.iService.RouteStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RouteStatisticsServiceImpl implements RouteStatisticsService {

    private final RouteStatisticsRepository repository;
    private final CurrentUserProvider       currentUserProvider;
    private final Clock                     clock;

    @Override
    @Transactional(readOnly = true)
    public RouteStatisticsResponse getRouteStatistics(LocalDate from, LocalDate to, UUID branchId) {
        // Same access and range rules as the performance page (spec 001).
        var scope = MetricsScope.resolve(currentUserProvider.current().orElse(null), branchId);
        var range = MetricsRange.of(from, to, clock);
        return RouteStatisticsCalculator.calculate(range.from(), range.to(),
                repository.findArrivals(scope, range),
                repository.findExits(scope, range),
                repository.findRejections(scope, range),
                repository.findTopReasons(scope, range));
    }
}
