package com.loop.new_loop_api.performance.service;

import com.loop.new_loop_api.common.exception.ForbiddenException;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.performance.domain.MetricsRange;
import com.loop.new_loop_api.performance.domain.MetricsScope;
import com.loop.new_loop_api.performance.domain.RouteStatisticsCalculator.Arrival;
import com.loop.new_loop_api.performance.exception.InvalidMetricsRangeException;
import com.loop.new_loop_api.performance.repository.RouteStatisticsRepository;
import com.loop.new_loop_api.users.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteStatisticsServiceImplTest {

    private static final ZoneId    ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate TODAY     = LocalDate.of(2026, 11, 4); // Wednesday

    @Mock private RouteStatisticsRepository repository;
    @Mock private CurrentUserProvider       currentUserProvider;

    private RouteStatisticsServiceImpl service;

    private final UUID branchId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(TODAY.atTime(10, 0).atZone(ARGENTINA).toInstant(), ARGENTINA);
        service = new RouteStatisticsServiceImpl(repository, currentUserProvider, clock);
    }

    @Test
    void shouldDefaultToTheCurrentWeekForEveryBranchForAnAdmin() {
        actingAs(Role.ADMIN, null);
        var route = UUID.randomUUID();
        when(repository.findArrivals(any(), any())).thenReturn(List.of(new Arrival(branchId, "Norte", route,
                "Rto 1", TODAY, TODAY.atTime(14, 0), TODAY.atTime(15, 0))));

        var result = service.getRouteStatistics(null, null, null);

        var scope = ArgumentCaptor.forClass(MetricsScope.class);
        var range = ArgumentCaptor.forClass(MetricsRange.class);
        verify(repository).findArrivals(scope.capture(), range.capture());
        assertThat(scope.getValue().allBranches()).isTrue();
        assertThat(range.getValue().from()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(range.getValue().to()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(result.from()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(result.stay().avgSeconds()).isEqualTo(3600.0);
    }

    @Test
    void shouldLetAnAdminPickABranch() {
        actingAs(Role.ADMIN, null);

        service.getRouteStatistics(TODAY, TODAY, branchId);

        var scope = ArgumentCaptor.forClass(MetricsScope.class);
        verify(repository).findExits(scope.capture(), any());
        assertThat(scope.getValue()).isEqualTo(new MetricsScope(false, branchId));
    }

    @Test
    void shouldKeepASupervisorInTheirOwnBranchWhateverTheyAskFor() {
        actingAs(Role.SUPERVISOR, branchId);

        service.getRouteStatistics(TODAY, TODAY, UUID.randomUUID());

        var scope = ArgumentCaptor.forClass(MetricsScope.class);
        verify(repository).findRejections(scope.capture(), any());
        assertThat(scope.getValue()).isEqualTo(new MetricsScope(false, branchId));
    }

    @Test
    void shouldDenyASupervisorWithoutBranch() {
        actingAs(Role.SUPERVISOR, null);

        assertThatThrownBy(() -> service.getRouteStatistics(null, null, null)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void shouldDenyOtherRoles() {
        actingAs(Role.CONTROLADOR, branchId);

        assertThatThrownBy(() -> service.getRouteStatistics(null, null, null)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectARangeLongerThanNinetyTwoDays() {
        actingAs(Role.ADMIN, null);

        assertThatThrownBy(() -> service.getRouteStatistics(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1), null))
                .isInstanceOf(InvalidMetricsRangeException.class);
        verifyNoInteractions(repository);
    }

    private void actingAs(Role role, UUID branch) {
        when(currentUserProvider.current())
                .thenReturn(Optional.of(new AuthenticatedUser(UUID.randomUUID(), "user", "User", role, branch)));
    }
}
