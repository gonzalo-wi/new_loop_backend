package com.loop.new_loop_api.settlements.service;

import com.loop.new_loop_api.common.exception.ForbiddenException;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import com.loop.new_loop_api.settlements.domain.PacLookup;
import com.loop.new_loop_api.settlements.domain.SettlementState;
import com.loop.new_loop_api.settlements.dto.SettlementFigures;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.repository.StockControlRepository;
import com.loop.new_loop_api.users.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverSettlementServiceImplTest {

    private static final ZoneId    ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    // 22:30 in Argentina is already the next day in UTC: today must come from the clock's zone.
    private static final LocalDate TODAY     = LocalDate.of(2026, 11, 5);

    @Mock private CurrentUserProvider    currentUserProvider;
    @Mock private RouteRepository        routeRepository;
    @Mock private StockControlRepository stockControlRepository;
    @Mock private PacSettlementGateway   gateway;

    private DriverSettlementServiceImpl service;
    private final UUID driverId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(TODAY.atTime(22, 30).atZone(ARGENTINA).toInstant(), ARGENTINA);
        service = new DriverSettlementServiceImpl(currentUserProvider, routeRepository, stockControlRepository, gateway, clock,
                Executors.newVirtualThreadPerTaskExecutor(), Duration.ofMillis(300));
    }

    @Test
    void shouldShowPacFiguresForADriverRouteThatCameBack() {
        actingAs(Role.REPARTIDOR);
        var route = route("Rto 179");
        when(routeRepository.findByDriverIdAndActiveTrueOrderByCodeAsc(driverId)).thenReturn(List.of(route));
        cameBack(route, true);
        var figures = new SettlementFigures(null, null, null, null, null, null, null, null, null, null, "PENDING", null, null, null, null, false);
        when(gateway.lookup(179L, TODAY)).thenReturn(new PacLookup.Found(figures));

        var result = service.getMySettlement();

        assertThat(result.date()).isEqualTo(TODAY);
        assertThat(result.routes()).singleElement().satisfies(r -> {
            assertThat(r.routeId()).isEqualTo(route.getId());
            assertThat(r.routeCode()).isEqualTo("Rto 179");
            assertThat(r.state()).isEqualTo(SettlementState.AVAILABLE);
            assertThat(r.figures()).isEqualTo(figures);
        });
    }

    @Test
    void shouldWaitForTheEntryControlWithoutAskingPac() {
        actingAs(Role.REPARTIDOR);
        var route = route("Rto 179");
        when(routeRepository.findByDriverIdAndActiveTrueOrderByCodeAsc(driverId)).thenReturn(List.of(route));
        cameBack(route, false);

        var result = service.getMySettlement();

        assertThat(result.routes()).singleElement().satisfies(r -> {
            assertThat(r.state()).isEqualTo(SettlementState.WAITING_ENTRY);
            assertThat(r.figures()).isNull();
        });
        verifyNoInteractions(gateway);
    }

    @Test
    void shouldGiveEachRouteItsOwnState() {
        actingAs(Role.REPARTIDOR);
        var noNumber = route("Reparto especial");
        var notLoaded = route("Rto 12");
        var down = route("Rto 13");
        when(routeRepository.findByDriverIdAndActiveTrueOrderByCodeAsc(driverId)).thenReturn(List.of(noNumber, notLoaded, down));
        cameBack(noNumber, true);
        cameBack(notLoaded, true);
        cameBack(down, true);
        when(gateway.lookup(12L, TODAY)).thenReturn(new PacLookup.NotLoaded());
        when(gateway.lookup(13L, TODAY)).thenReturn(new PacLookup.Unavailable("timeout"));

        var states = service.getMySettlement().routes().stream().map(r -> r.state()).toList();

        assertThat(states).containsExactly(SettlementState.NO_ROUTE_NUMBER, SettlementState.NOT_LOADED,
                SettlementState.UNAVAILABLE);
    }

    @Test
    void shouldAnswerWithinOneDeadlineWhenPacHangsForEveryRoute() {
        actingAs(Role.REPARTIDOR);
        var a = route("Rto 1");
        var b = route("Rto 2");
        var c = route("Rto 3");
        when(routeRepository.findByDriverIdAndActiveTrueOrderByCodeAsc(driverId)).thenReturn(List.of(a, b, c));
        cameBack(a, true);
        cameBack(b, true);
        cameBack(c, true);
        when(gateway.lookup(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(TODAY)))
                .thenAnswer(invocation -> {
                    Thread.sleep(5_000);
                    return new PacLookup.NotLoaded();
                });

        long started = System.nanoTime();
        var states = service.getMySettlement().routes().stream().map(r -> r.state()).toList();
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        // Three hung routes cost one 300 ms deadline, not three.
        assertThat(states).containsOnly(SettlementState.UNAVAILABLE).hasSize(3);
        assertThat(elapsedMs).isLessThan(2_000);
    }

    @Test
    void shouldAnswerWithNoRoutesForADriverWithoutAssignment() {
        actingAs(Role.REPARTIDOR);
        when(routeRepository.findByDriverIdAndActiveTrueOrderByCodeAsc(driverId)).thenReturn(List.of());

        assertThat(service.getMySettlement().routes()).isEmpty();
    }

    @Test
    void shouldDenyEveryoneButDrivers() {
        actingAs(Role.SUPERVISOR);

        assertThatThrownBy(() -> service.getMySettlement()).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(routeRepository, gateway);
    }

    @Test
    void shouldDenyAnAnonymousCall() {
        when(currentUserProvider.current()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMySettlement()).isInstanceOf(ForbiddenException.class);
    }

    private void actingAs(Role role) {
        when(currentUserProvider.current())
                .thenReturn(Optional.of(new AuthenticatedUser(driverId, "chofer", "Chofer", role, null)));
    }

    private static Route route(String code) {
        var route = new Route();
        route.setId(UUID.randomUUID());
        route.setCode(code);
        return route;
    }

    private void cameBack(Route route, boolean value) {
        when(stockControlRepository.existsByTypeAndRouteIdAndControlDateAndStatusNot(
                ControlType.ENTRY, route.getId(), TODAY, ControlStatus.CANCELLED)).thenReturn(value);
    }
}
