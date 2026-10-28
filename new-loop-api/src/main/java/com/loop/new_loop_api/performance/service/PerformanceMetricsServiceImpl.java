package com.loop.new_loop_api.performance.service;

import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.performance.domain.MetricsRange;
import com.loop.new_loop_api.performance.domain.MetricsScope;
import com.loop.new_loop_api.performance.dto.PerformanceSummaryResponse;
import com.loop.new_loop_api.performance.dto.PerformanceSummaryResponse.UserPerformance;
import com.loop.new_loop_api.performance.dto.UserTrendResponse;
import com.loop.new_loop_api.performance.dto.UserTrendResponse.DailyPerformance;
import com.loop.new_loop_api.performance.mapper.PerformanceMetricsMapper;
import com.loop.new_loop_api.performance.repository.MetricsQuery;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.ControlMetricsRow;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.DispenserMetricsRow;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.ReasonOwner;
import com.loop.new_loop_api.performance.service.iService.PerformanceMetricsService;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.users.entity.User;
import com.loop.new_loop_api.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerformanceMetricsServiceImpl implements PerformanceMetricsService {

    private final PerformanceMetricsRepository repository;
    private final PerformanceMetricsMapper     mapper;
    private final CurrentUserProvider          currentUserProvider;
    private final UserRepository               userRepository;
    private final Clock                        clock;

    @Override
    @Transactional(readOnly = true)
    public PerformanceSummaryResponse getSummary(LocalDate from, LocalDate to, UUID branchId, UUID userId,
                                                 ControlType controlType, DispenserMovementType movementType) {
        var query    = buildQuery(from, to, branchId, userId, controlType, movementType, false);
        var controls = repository.findControlMetrics(query);
        var movements = repository.findDispenserMetrics(query);
        return new PerformanceSummaryResponse(
                query.range().from(), query.range().to(),
                mergeUsers(controls, movements),
                repository.findCoverage(query).stream().map(mapper::toCoverageDay).toList(),
                repository.findTopReasons(query, ReasonOwner.ACTOR).stream().map(mapper::toReasonCount).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserTrendResponse getUserTrend(UUID userId, LocalDate from, LocalDate to, UUID branchId,
                                          ControlType controlType, DispenserMovementType movementType) {
        var query    = buildQuery(from, to, branchId, userId, controlType, movementType, true);
        var controls = byDay(repository.findControlMetrics(query), ControlMetricsRow::day);
        var movements = byDay(repository.findDispenserMetrics(query), DispenserMetricsRow::day);
        var days = query.range().from().datesUntil(query.range().to().plusDays(1))
                .map(day -> new DailyPerformance(day,
                        mapper.toControlMetrics(controls.get(day)),
                        mapper.toDispenserMetrics(movements.get(day))))
                .toList();
        return new UserTrendResponse(userId, trendUsername(userId, query.scope(), controls, movements),
                query.range().from(), query.range().to(), days,
                repository.findTopReasons(query, ReasonOwner.RESPONSIBLE).stream().map(mapper::toReasonCount).toList());
    }

    private MetricsQuery buildQuery(LocalDate from, LocalDate to, UUID branchId, UUID userId,
                                    ControlType controlType, DispenserMovementType movementType, boolean byDay) {
        var scope = MetricsScope.resolve(currentUserProvider.current().orElse(null), branchId);
        var range = MetricsRange.of(from, to, clock);
        return new MetricsQuery(scope, range, userId, controlType, movementType, byDay);
    }

    /** One entry per user, in the order users first appear; null is the "Sin usuario" bucket. */
    private List<UserPerformance> mergeUsers(List<ControlMetricsRow> controls, List<DispenserMetricsRow> movements) {
        var usernames = new LinkedHashMap<UUID, String>();
        controls.forEach(row -> usernames.putIfAbsent(row.userId(), row.username()));
        movements.forEach(row -> usernames.putIfAbsent(row.userId(), row.username()));
        var controlsByUser  = byUser(controls, ControlMetricsRow::userId);
        var movementsByUser = byUser(movements, DispenserMetricsRow::userId);
        return usernames.entrySet().stream()
                .map(entry -> new UserPerformance(entry.getKey(),
                        mapper.usernameOrLabel(entry.getKey(), entry.getValue()),
                        mapper.toControlMetrics(controlsByUser.get(entry.getKey())),
                        mapper.toDispenserMetrics(movementsByUser.get(entry.getKey()))))
                .toList();
    }

    /** Without activity in range, the name comes from the user, but only if they are inside the requester's scope. */
    private String trendUsername(UUID userId, MetricsScope scope, Map<LocalDate, ControlMetricsRow> controls,
                                 Map<LocalDate, DispenserMetricsRow> movements) {
        return controls.values().stream().map(ControlMetricsRow::username)
                .filter(name -> name != null).findFirst()
                .or(() -> movements.values().stream().map(DispenserMetricsRow::username)
                        .filter(name -> name != null).findFirst())
                .orElseGet(() -> userRepository.findById(userId)
                        .filter(user -> scope.allBranches()
                                || (user.getBranch() != null && user.getBranch().getId().equals(scope.branchId())))
                        .map(User::getUsername)
                        .orElse(null));
    }

    // HashMap rather than Collectors.toMap: the "no user" key is null.
    private static <T> Map<UUID, T> byUser(List<T> rows, Function<T, UUID> key) {
        var map = new HashMap<UUID, T>();
        rows.forEach(row -> map.put(key.apply(row), row));
        return map;
    }

    private static <T> Map<LocalDate, T> byDay(List<T> rows, Function<T, LocalDate> key) {
        return rows.stream().collect(Collectors.toMap(key, Function.identity()));
    }
}
