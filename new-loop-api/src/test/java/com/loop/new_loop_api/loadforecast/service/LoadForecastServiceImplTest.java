package com.loop.new_loop_api.loadforecast.service;

import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.common.exception.ForbiddenException;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.loadforecast.dto.LoadForecastItemResponse;
import com.loop.new_loop_api.loadforecast.exception.ForecastRouteNotFoundException;
import com.loop.new_loop_api.products.entity.Product;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.entity.StockControlItem;
import com.loop.new_loop_api.stockcontrols.repository.StockControlRepository;
import com.loop.new_loop_api.users.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoadForecastServiceImplTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    // Monday 2026-11-16 22:30 in Argentina is already Tuesday in UTC: the load day must still be Tuesday 17.
    private static final Clock MONDAY_NIGHT =
            Clock.fixed(ZonedDateTime.of(2026, 11, 16, 22, 30, 0, 0, ARGENTINA).toInstant(), ARGENTINA);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 17);
    private static final List<LocalDate> REFERENCE_DAYS = List.of(
            LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 3),
            LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 20));

    @Mock private RouteRepository        routeRepository;
    @Mock private StockControlRepository stockControlRepository;
    @Mock private CurrentUserProvider    currentUserProvider;

    private LoadForecastServiceImpl service;
    private Branch ciudadela;
    private Branch laPlata;

    @BeforeEach
    void setUp() {
        service   = new LoadForecastServiceImpl(routeRepository, stockControlRepository, currentUserProvider, MONDAY_NIGHT);
        ciudadela = Branch.builder().id(UUID.randomUUID()).name("Ciudadela").build();
        laPlata   = Branch.builder().id(UUID.randomUUID()).name("La Plata").build();
    }

    private static Route route(String code, Branch branch, boolean active) {
        return Route.builder().id(UUID.randomUUID()).code(code).branch(branch).active(active).build();
    }

    private void loggedAs(Role role, Branch branch) {
        when(currentUserProvider.current()).thenReturn(Optional.of(
                new AuthenticatedUser(UUID.randomUUID(), "u", "U", role, branch == null ? null : branch.getId())));
    }

    private static StockControl exit(LocalDate date, Product product, int quantity) {
        var control = StockControl.builder().id(UUID.randomUUID()).type(ControlType.EXIT).controlDate(date).build();
        control.getItems().add(StockControlItem.builder().product(product).totalQuantity(quantity)
                .fullQuantity(quantity).exchangeQuantity(0).stockControl(control).build());
        return control;
    }

    @Test
    void shouldForecastTheNextDeliveryDayFromTheRouteExitControls() {
        loggedAs(Role.CLARKISTA, ciudadela);
        var rto179 = route("Rto 179", ciudadela, true);
        var bidon  = Product.builder().id(UUID.randomUUID()).code("B20").name("Bidon 20 L").displayOrder(1).build();
        when(routeRepository.findByRouteNumber(179)).thenReturn(List.of(rto179, route("Rto 179", laPlata, true)));
        when(stockControlRepository.findReferenceControls(rto179.getId(), ControlType.EXIT, REFERENCE_DAYS, ControlStatus.CANCELLED))
                .thenReturn(List.of(exit(LocalDate.of(2026, 11, 10), bidon, 100), exit(LocalDate.of(2026, 11, 3), bidon, 121)));

        var forecast = service.forecast(179);

        assertThat(forecast.routeCode()).isEqualTo("Rto 179");
        assertThat(forecast.loadDate()).isEqualTo(TUESDAY);
        assertThat(forecast.basedOnDates()).containsExactly(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 10));
        assertThat(forecast.items()).containsExactly(new LoadForecastItemResponse("B20", "Bidon 20 L", 111));
    }

    @Test
    void shouldAnswerNoData_when_theRouteHasNoReferenceControl() {
        loggedAs(Role.CLARKISTA, ciudadela);
        var rto179 = route("Rto 179", ciudadela, true);
        when(routeRepository.findByRouteNumber(179)).thenReturn(List.of(rto179));
        when(stockControlRepository.findReferenceControls(eq(rto179.getId()), eq(ControlType.EXIT), any(), eq(ControlStatus.CANCELLED)))
                .thenReturn(List.of());

        var forecast = service.forecast(179);

        assertThat(forecast.loadDate()).isEqualTo(TUESDAY);
        assertThat(forecast.basedOnDates()).isEmpty();
        assertThat(forecast.items()).isEmpty();
    }

    @Test
    void shouldNotFindARouteOfAnotherBranch() {
        loggedAs(Role.CLARKISTA, ciudadela);
        when(routeRepository.findByRouteNumber(250)).thenReturn(List.of(route("Rto 250", laPlata, true)));

        assertThatThrownBy(() -> service.forecast(250)).isInstanceOf(ForecastRouteNotFoundException.class);
    }

    @Test
    void shouldNotFindAnInactiveRoute() {
        loggedAs(Role.CLARKISTA, ciudadela);
        when(routeRepository.findByRouteNumber(179)).thenReturn(List.of(route("Rto 179", ciudadela, false)));

        assertThatThrownBy(() -> service.forecast(179)).isInstanceOf(ForecastRouteNotFoundException.class);
    }

    @Test
    void shouldNotGuess_when_theNumberMatchesSeveralRoutesOfTheBranch() {
        loggedAs(Role.CLARKISTA, ciudadela);
        when(routeRepository.findByRouteNumber(179))
                .thenReturn(List.of(route("Rto 179", ciudadela, true), route("179 B", ciudadela, true)));

        assertThatThrownBy(() -> service.forecast(179)).isInstanceOf(ForecastRouteNotFoundException.class);
    }

    @Test
    void shouldLetAnAdminForecastARouteOfAnyBranch_when_theNumberIsUnique() {
        loggedAs(Role.ADMIN, null);
        var rto250 = route("Rto 250", laPlata, true);
        when(routeRepository.findByRouteNumber(250)).thenReturn(List.of(rto250));
        when(stockControlRepository.findReferenceControls(eq(rto250.getId()), eq(ControlType.EXIT), any(), eq(ControlStatus.CANCELLED)))
                .thenReturn(List.of());

        assertThat(service.forecast(250).routeCode()).isEqualTo("Rto 250");
    }

    @Test
    void shouldNotFindForAnAdmin_when_theNumberIsInSeveralBranches() {
        loggedAs(Role.ADMIN, null);
        when(routeRepository.findByRouteNumber(179))
                .thenReturn(List.of(route("Rto 179", ciudadela, true), route("Rto 179", laPlata, true)));

        assertThatThrownBy(() -> service.forecast(179)).isInstanceOf(ForecastRouteNotFoundException.class);
    }

    @Test
    void shouldDeny_when_theUserIsNeitherClarkistaNorAdmin() {
        loggedAs(Role.SUPERVISOR, ciudadela);

        assertThatThrownBy(() -> service.forecast(179)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(routeRepository, stockControlRepository);
    }
}
