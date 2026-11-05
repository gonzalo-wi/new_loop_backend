package com.loop.new_loop_api.settlements.service;

import com.loop.new_loop_api.common.exception.ForbiddenException;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.performance.domain.RouteNumber;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import com.loop.new_loop_api.settlements.domain.PacLookup;
import com.loop.new_loop_api.settlements.domain.SettlementState;
import com.loop.new_loop_api.settlements.dto.DriverSettlementResponse;
import com.loop.new_loop_api.settlements.dto.DriverSettlementResponse.RouteSettlement;
import com.loop.new_loop_api.settlements.service.iService.DriverSettlementService;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.repository.StockControlRepository;
import com.loop.new_loop_api.users.entity.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Not transactional on purpose: each repository read runs in its own short read-only transaction, so no
 * database connection is held while PAC answers (up to its timeout).
 */
@Service
public class DriverSettlementServiceImpl implements DriverSettlementService {

    /** The whole request's budget for PAC (spec 007 NFR-1: under 6 s even when PAC hangs). */
    static final Duration PAC_DEADLINE = Duration.ofSeconds(5);

    private final CurrentUserProvider    currentUserProvider;
    private final RouteRepository        routeRepository;
    private final StockControlRepository stockControlRepository;
    private final PacSettlementGateway   pacSettlementGateway;
    private final Clock                  clock;
    private final Executor               pacExecutor;
    private final Duration               pacDeadline;

    @Autowired
    public DriverSettlementServiceImpl(CurrentUserProvider currentUserProvider, RouteRepository routeRepository,
                                       StockControlRepository stockControlRepository,
                                       PacSettlementGateway pacSettlementGateway, Clock clock,
                                       @Qualifier(SettlementConfig.PAC_EXECUTOR) Executor pacExecutor) {
        this(currentUserProvider, routeRepository, stockControlRepository, pacSettlementGateway, clock, pacExecutor,
                PAC_DEADLINE);
    }

    DriverSettlementServiceImpl(CurrentUserProvider currentUserProvider, RouteRepository routeRepository,
                                StockControlRepository stockControlRepository, PacSettlementGateway pacSettlementGateway,
                                Clock clock, Executor pacExecutor, Duration pacDeadline) {
        this.currentUserProvider    = currentUserProvider;
        this.routeRepository        = routeRepository;
        this.stockControlRepository = stockControlRepository;
        this.pacSettlementGateway   = pacSettlementGateway;
        this.clock                  = clock;
        this.pacExecutor            = pacExecutor;
        this.pacDeadline            = pacDeadline;
    }

    @Override
    public DriverSettlementResponse getMySettlement() {
        var user = currentUserProvider.current()
                .filter(u -> u.role() == Role.REPARTIDOR)
                .orElseThrow(() -> new ForbiddenException("Only drivers can see their settlement"));
        var today = LocalDate.now(clock);
        // Every PAC lookup starts now and shares one deadline, so several routes cost one timeout, not one each.
        var pending = routeRepository.findByDriverIdAndActiveTrueOrderByCodeAsc(user.id()).stream()
                .map(route -> start(route, today))
                .toList();
        return new DriverSettlementResponse(today, pending.stream().map(Pending::finish).toList());
    }

    private Pending start(Route route, LocalDate today) {
        boolean hasEntry = stockControlRepository.existsByTypeAndRouteIdAndControlDateAndStatusNot(
                ControlType.ENTRY, route.getId(), today, ControlStatus.CANCELLED);
        var number = RouteNumber.of(route.getCode());
        CompletableFuture<PacLookup> lookup = hasEntry && number != null
                ? CompletableFuture.supplyAsync(() -> pacSettlementGateway.lookup(number, today), pacExecutor)
                        .completeOnTimeout(new PacLookup.Unavailable("PAC did not answer in time"),
                                pacDeadline.toMillis(), TimeUnit.MILLISECONDS)
                : null;
        return new Pending(route, hasEntry, number, lookup);
    }

    private record Pending(Route route, boolean hasEntry, Long number, CompletableFuture<PacLookup> future) {

        RouteSettlement finish() {
            PacLookup lookup = future == null ? null : future.join();
            var state = SettlementState.resolve(hasEntry, number, lookup);
            var figures = lookup instanceof PacLookup.Found found ? found.figures() : null;
            return new RouteSettlement(route.getId(), route.getCode(), state, figures);
        }
    }
}
