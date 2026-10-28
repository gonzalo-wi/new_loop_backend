package com.loop.new_loop_api.performance.service;

import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.common.exception.ForbiddenException;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.performance.entity.OperationEventType;
import com.loop.new_loop_api.performance.exception.InvalidMetricsRangeException;
import com.loop.new_loop_api.performance.mapper.PerformanceMetricsMapper;
import com.loop.new_loop_api.performance.repository.MetricsQuery;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.ControlMetricsRow;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.DispenserMetricsRow;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.ReasonOwner;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.ReasonRow;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.users.entity.Role;
import com.loop.new_loop_api.users.entity.User;
import com.loop.new_loop_api.users.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceMetricsServiceImplTest {

    private static final ZoneId    ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate TODAY     = LocalDate.of(2026, 10, 28); // Wednesday

    @Mock private PerformanceMetricsRepository repository;
    @Mock private CurrentUserProvider          currentUserProvider;
    @Mock private UserRepository               userRepository;

    private PerformanceMetricsServiceImpl service;

    private final UUID branchId = UUID.randomUUID();
    private final UUID userA    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(TODAY.atTime(10, 0).atZone(ARGENTINA).toInstant(), ARGENTINA);
        service = new PerformanceMetricsServiceImpl(repository, new PerformanceMetricsMapper(), currentUserProvider,
                userRepository, clock);
    }

    @Test
    void shouldMergeControlAndDispenserFiguresOfTheSameUser() {
        actingAs(Role.SUPERVISOR, branchId);
        when(repository.findControlMetrics(any())).thenReturn(List.of(controlRow(userA, "ctrl1", null)));
        when(repository.findDispenserMetrics(any())).thenReturn(List.of(dispenserRow(userA, "ctrl1", null)));

        var summary = service.getSummary(null, null, null, null, null, null);

        assertThat(summary.from()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(summary.to()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(summary.users()).singleElement().satisfies(u -> {
            assertThat(u.userId()).isEqualTo(userA);
            assertThat(u.username()).isEqualTo("ctrl1");
            // 4 submitted, 1 faulty; 3 approvals, 2 on the first try; 450 s average response
            assertThat(u.controls().errorPercent()).isEqualTo(25.0);
            assertThat(u.controls().firstPassAcceptancePercent()).isEqualTo(66.7);
            assertThat(u.controls().avgDriverResponseMinutes()).isEqualTo(7.5);
            assertThat(u.dispensers().loads()).isEqualTo(3);
            assertThat(u.dispensers().avgLoadMinutes()).isEqualTo(12.0);
            assertThat(u.dispensers().medianGapMinutes()).isEqualTo(9.5);
        });
    }

    @Test
    void shouldKeepAveragesEmptyAndCountsZero_when_aUserHasOnlyDispenserActivity() {
        actingAs(Role.ADMIN, null);
        when(repository.findControlMetrics(any())).thenReturn(List.of());
        when(repository.findDispenserMetrics(any())).thenReturn(List.of(dispenserRow(userA, "ctrl1", null)));

        var user = service.getSummary(null, null, null, null, null, null).users().get(0);

        assertThat(user.controls().entriesSubmitted()).isZero();
        assertThat(user.controls().errorPercent()).isNull();
        assertThat(user.controls().firstPassAcceptancePercent()).isNull();
        assertThat(user.controls().avgDriverResponseMinutes()).isNull();
    }

    @Test
    void shouldLabelEventsWithoutUser() {
        actingAs(Role.ADMIN, null);
        when(repository.findControlMetrics(any())).thenReturn(List.of(controlRow(null, null, null)));

        assertThat(service.getSummary(null, null, null, null, null, null).users())
                .singleElement().satisfies(u -> assertThat(u.username()).isEqualTo(PerformanceMetricsMapper.NO_USER_LABEL));
    }

    @Test
    void shouldScopeASupervisorToTheirBranchAndPassTheFilters() {
        actingAs(Role.SUPERVISOR, branchId);

        service.getSummary(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), UUID.randomUUID(), userA,
                ControlType.ENTRY, DispenserMovementType.LOAD);

        var captor = ArgumentCaptor.forClass(MetricsQuery.class);
        verify(repository).findControlMetrics(captor.capture());
        var query = captor.getValue();
        assertThat(query.scope().branchId()).isEqualTo(branchId);
        assertThat(query.scope().allBranches()).isFalse();
        assertThat(query.responsibleUserId()).isEqualTo(userA);
        assertThat(query.controlType()).isEqualTo(ControlType.ENTRY);
        assertThat(query.movementType()).isEqualTo(DispenserMovementType.LOAD);
        assertThat(query.byDay()).isFalse();
        assertThat(query.range().lastEndedDay()).isEqualTo(TODAY.minusDays(1));
        verify(repository).findTopReasons(any(), eq(ReasonOwner.ACTOR));
        verify(repository).findCoverage(any());
    }

    @Test
    void shouldDenyAControllerWithoutQuerying() {
        actingAs(Role.CONTROLADOR, branchId);

        assertThatThrownBy(() -> service.getSummary(null, null, null, null, null, null))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectAnInvalidRangeWithoutQuerying() {
        actingAs(Role.ADMIN, null);

        assertThatThrownBy(() -> service.getSummary(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1),
                null, null, null, null)).isInstanceOf(InvalidMetricsRangeException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void shouldFillEveryDayOfTheRangeInTheUserTrend() {
        actingAs(Role.ADMIN, null);
        var monday = LocalDate.of(2026, 10, 26);
        when(repository.findControlMetrics(any())).thenReturn(List.of(controlRow(userA, "ctrl1", monday)));
        when(repository.findDispenserMetrics(any())).thenReturn(List.of(dispenserRow(userA, "ctrl1", monday.plusDays(2))));
        when(repository.findTopReasons(any(), eq(ReasonOwner.RESPONSIBLE))).thenReturn(List.of(
                new ReasonRow(OperationEventType.CONTROL_REJECTED, "Faltan bidones", userA, "ctrl1", 3)));

        var trend = service.getUserTrend(userA, null, null, null, null, null);

        assertThat(trend.username()).isEqualTo("ctrl1");
        assertThat(trend.days()).hasSize(7);
        assertThat(trend.days().get(0).controls().entriesSubmitted()).isEqualTo(3);
        assertThat(trend.days().get(1).controls().entriesSubmitted()).isZero();
        assertThat(trend.days().get(2).dispensers().loads()).isEqualTo(3);
        assertThat(trend.reasons()).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("REJECTION");
            assertThat(r.count()).isEqualTo(3);
        });
        var captor = ArgumentCaptor.forClass(MetricsQuery.class);
        verify(repository).findControlMetrics(captor.capture());
        assertThat(captor.getValue().byDay()).isTrue();
        assertThat(captor.getValue().responsibleUserId()).isEqualTo(userA);
    }

    @Test
    void shouldLookUpTheUsername_when_theUserHasNoActivityInTheRange() {
        actingAs(Role.ADMIN, null);
        when(userRepository.findById(userA)).thenReturn(Optional.of(User.builder().username("ctrl1").build()));

        assertThat(service.getUserTrend(userA, null, null, null, null, null).username()).isEqualTo("ctrl1");
    }

    @Test
    void shouldNotRevealTheUsernameOfAUserFromAnotherBranch_toASupervisor() {
        actingAs(Role.SUPERVISOR, branchId);
        var otherBranch = Branch.builder().id(UUID.randomUUID()).build();
        when(userRepository.findById(userA)).thenReturn(Optional.of(User.builder().username("ctrl1").branch(otherBranch).build()));

        assertThat(service.getUserTrend(userA, null, null, null, null, null).username()).isNull();
    }

    private void actingAs(Role role, UUID branch) {
        when(currentUserProvider.current())
                .thenReturn(Optional.of(new AuthenticatedUser(UUID.randomUUID(), "me", "Me", role, branch)));
    }

    private static ControlMetricsRow controlRow(UUID userId, String username, LocalDate day) {
        return new ControlMetricsRow(userId, username, day, 1, 3, 1, 1, 1, 3, 2, 450.0, 0, 1, 0);
    }

    private static DispenserMetricsRow dispenserRow(UUID userId, String username, LocalDate day) {
        return new DispenserMetricsRow(userId, username, day, 3, 1, 20, 0, 1, 720.0, 700.0, 0, 600.0, 570.0, 1, 0, 0);
    }
}
