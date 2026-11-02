package com.loop.new_loop_api.integrations.pac.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.integrations.pac.dto.PacArrivalResponse;
import com.loop.new_loop_api.integrations.pac.mapper.PacArrivalMapper;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.repository.StockControlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PacArrivalServiceImplTest {

    private static final LocalDate TODAY_IN_ARGENTINA = LocalDate.parse("2026-11-02");

    private final StockControlRepository repository = mock(StockControlRepository.class);
    // 01:30 UTC on the 3rd is still 22:30 of the 2nd in Argentina.
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T01:30:00Z"), ZoneOffset.UTC);

    private PacArrivalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PacArrivalServiceImpl(clock, repository, new PacArrivalMapper());
    }

    private static StockControl control(String routeCode, String branchName, String createdAt, ControlStatus status) {
        return StockControl.builder()
                .type(ControlType.ENTRY)
                .status(status)
                .route(Route.builder().id(UUID.randomUUID()).code(routeCode).build())
                .branch(Branch.builder().id(UUID.randomUUID()).name(branchName).build())
                .createdAt(LocalDateTime.parse(createdAt))
                .build();
    }

    private void repositoryReturns(StockControl... controls) {
        when(repository.findControlsForDate(any(), any(), any(), any())).thenReturn(List.of(controls));
    }

    @Test
    void shouldQueryTodayInArgentina_when_noDateIsGiven() {
        repositoryReturns();

        service.getArrivals(null);

        verify(repository).findControlsForDate(ControlType.ENTRY, TODAY_IN_ARGENTINA, ControlStatus.CANCELLED, null);
    }

    @Test
    void shouldQueryTheGivenDate_when_oneIsGiven() {
        repositoryReturns();

        service.getArrivals(LocalDate.parse("2026-12-24"));

        verify(repository).findControlsForDate(eq(ControlType.ENTRY), eq(LocalDate.parse("2026-12-24")),
                eq(ControlStatus.CANCELLED), isNull());
    }

    @Test
    void shouldReturnAnEmptyList_when_nobodyArrived() {
        repositoryReturns();

        assertThat(service.getArrivals(TODAY_IN_ARGENTINA)).isEmpty();
    }

    @Test
    void shouldMapTheArrivalTimeToArgentinaOffset() {
        repositoryReturns(control("Rto 179", "Centro", "2026-11-02T15:20:00.123", ControlStatus.SENT_TO_AGUAS));

        var arrivals = service.getArrivals(TODAY_IN_ARGENTINA);

        assertThat(arrivals).singleElement().satisfies(arrival -> {
            assertThat(arrival.routeNumber()).isEqualTo(179L);
            assertThat(arrival.arrivedAt()).hasToString("2026-11-02T15:20-03:00");
        });
    }

    @Test
    void shouldLeaveOutRoutesWithoutDigits() {
        repositoryReturns(
                control("Sin número", "Centro", "2026-11-02T14:00:00", ControlStatus.CONTROLLED),
                control("Rto 12", "Centro", "2026-11-02T15:00:00", ControlStatus.CONTROLLED));

        var arrivals = service.getArrivals(TODAY_IN_ARGENTINA);

        assertThat(arrivals).extracting(PacArrivalResponse::routeNumber).containsExactly(12L);
    }

    @Test
    void shouldKeepOneEntryPerControl_when_routeNumbersRepeat() {
        repositoryReturns(
                control("179", "Centro", "2026-11-02T14:00:00", ControlStatus.CONTROLLED),
                control("179", "Norte", "2026-11-02T14:10:00", ControlStatus.CONTROLLED),
                control("R-179", "Centro", "2026-11-02T14:20:00", ControlStatus.CONTROLLED));

        var arrivals = service.getArrivals(TODAY_IN_ARGENTINA);

        assertThat(arrivals).hasSize(3);
        assertThat(arrivals).extracting(PacArrivalResponse::routeNumber).containsOnly(179L);
    }

    @Test
    void shouldKeepRejectedAndErroredControls_becauseTheTruckCameBackAnyway() {
        repositoryReturns(
                control("Rto 1", "Centro", "2026-11-02T14:00:00", ControlStatus.REJECTED_BY_DRIVER),
                control("Rto 2", "Centro", "2026-11-02T14:10:00", ControlStatus.AGUAS_ERROR));

        assertThat(service.getArrivals(TODAY_IN_ARGENTINA)).hasSize(2);
    }

    @Test
    void shouldOrderByArrivalTime_thenRouteCodeThenBranchName() {
        repositoryReturns(
                control("Rto 5", "Norte", "2026-11-02T16:00:00", ControlStatus.CONTROLLED),
                control("Rto 3", "Sur", "2026-11-02T15:00:00", ControlStatus.CONTROLLED),
                control("Rto 3", "Norte", "2026-11-02T15:00:00", ControlStatus.CONTROLLED),
                control("Rto 1", "Sur", "2026-11-02T15:00:30", ControlStatus.CONTROLLED),
                control("Rto 2", "Sur", "2026-11-02T15:00:00", ControlStatus.CONTROLLED));

        var arrivals = service.getArrivals(TODAY_IN_ARGENTINA);

        assertThat(arrivals).extracting(a -> a.routeCode() + "/" + a.branchName())
                .containsExactly("Rto 2/Sur", "Rto 3/Norte", "Rto 3/Sur", "Rto 1/Sur", "Rto 5/Norte");
    }

    @Test
    void shouldPropagateTheFailure_when_theRepositoryFails() {
        var failure = new DataAccessResourceFailureException("db down");
        when(repository.findControlsForDate(any(), any(), any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> service.getArrivals(TODAY_IN_ARGENTINA)).isSameAs(failure);
    }

    @Test
    void shouldNotDependOnTheAuditLog() {
        for (Field field : PacArrivalServiceImpl.class.getDeclaredFields()) {
            assertThat(field.getType()).isNotEqualTo(AuditService.class);
        }
    }
}
