package com.loop.new_loop_api.performance.repository;

import com.loop.new_loop_api.performance.domain.MetricsRange;
import com.loop.new_loop_api.performance.domain.MetricsScope;
import com.loop.new_loop_api.performance.domain.RouteStatisticsCalculator.Arrival;
import com.loop.new_loop_api.performance.domain.RouteStatisticsCalculator.Reason;
import com.loop.new_loop_api.performance.domain.RouteStatisticsCalculator.Rejections;
import com.loop.new_loop_api.performance.entity.OperationEvent;
import com.loop.new_loop_api.performance.entity.OperationEventSubject;
import com.loop.new_loop_api.performance.entity.OperationEventType;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every test works on its own fresh branch and routes: the test database is shared across runs and
 * operation events can never be deleted.
 */
class RouteStatisticsRepositoryTest extends PostgresIntegrationTest {

    private static final LocalDate SAT = LocalDate.of(2026, 10, 17);
    private static final LocalDate MON = LocalDate.of(2026, 10, 19);
    private static final LocalDate TUE = MON.plusDays(1);

    @Autowired private RouteStatisticsRepository repository;
    @Autowired private OperationEventRepository  events;
    @Autowired private JdbcTemplate              jdbc;

    private UUID branchId;

    @BeforeEach
    void setUp() {
        branchId = branch();
    }

    @Test
    void shouldPickTheLiveExitCreatedOnTheArrivalDay() {
        var r1 = route("R1");
        control(branchId, ControlType.ENTRY, r1, MON, "PENDING_DRIVER_APPROVAL", MON.atTime(14, 0));
        control(branchId, ControlType.EXIT, r1, TUE, "CANCELLED", MON.atTime(15, 0));
        control(branchId, ControlType.EXIT, r1, TUE, "SENT_TO_AGUAS", MON.atTime(16, 30));
        var r2 = route("R2"); // exit made the next morning: no stay
        control(branchId, ControlType.ENTRY, r2, MON, "ACCEPTED_BY_DRIVER", MON.atTime(13, 0));
        control(branchId, ControlType.EXIT, r2, TUE, "SENT_TO_AGUAS", TUE.atTime(7, 0));
        var r3 = route("R3"); // Saturday arrival, exit for Monday made on Saturday
        control(branchId, ControlType.ENTRY, r3, SAT, "SENT_TO_AGUAS", SAT.atTime(12, 0));
        control(branchId, ControlType.EXIT, r3, MON, "SENT_TO_AGUAS", SAT.atTime(14, 0));
        control(branchId, ControlType.ENTRY, route("R4"), MON, "CANCELLED", MON.atTime(14, 0));
        control(branch(), ControlType.ENTRY, route("R5"), MON, "SENT_TO_AGUAS", MON.atTime(14, 0));

        var arrivals = repository.findArrivals(scope(), new MetricsRange(SAT, TUE, TUE));

        assertThat(arrivals).hasSize(3);
        assertThat(arrival(arrivals, r1).arrivedAt()).isEqualTo(MON.atTime(14, 0));
        assertThat(arrival(arrivals, r1).exitAt()).isEqualTo(MON.atTime(16, 30));
        assertThat(arrival(arrivals, r1).routeCode()).startsWith("R1-");
        assertThat(arrival(arrivals, r1).branchName()).isEqualTo("Branch " + branchId);
        assertThat(arrival(arrivals, r2).exitAt()).isNull();
        assertThat(arrival(arrivals, r3).exitAt()).isEqualTo(SAT.atTime(14, 0));
    }

    @Test
    void shouldReturnEveryLiveExitWithTheDayItWasCreated() {
        var r1 = route("R1");
        control(branchId, ControlType.EXIT, r1, TUE, "SENT_TO_AGUAS", MON.atTime(17, 0));
        var r2 = route("R2");
        control(branchId, ControlType.EXIT, r2, TUE, "SENT_TO_AGUAS", TUE.atTime(6, 30));
        control(branchId, ControlType.EXIT, route("R3"), TUE, "CANCELLED", TUE.atTime(6, 30));
        control(branchId, ControlType.EXIT, route("R4"), TUE.plusDays(1), "SENT_TO_AGUAS", TUE.atTime(17, 0));

        var exits = repository.findExits(scope(), new MetricsRange(MON, TUE, TUE));

        assertThat(exits).hasSize(2);
        assertThat(exits).filteredOn(e -> e.routeId().equals(r1)).singleElement()
                .satisfies(e -> assertThat(e.createdDate()).isEqualTo(MON));
        assertThat(exits).filteredOn(e -> e.routeId().equals(r2)).singleElement().satisfies(e -> {
            assertThat(e.createdDate()).isEqualTo(TUE);
            assertThat(e.controlDate()).isEqualTo(TUE);
        });
    }

    @Test
    void shouldGroupRejectionsByTheStoredRouteCodeWithTopThreeReasons() {
        var r1 = route("R1");
        var c1 = UUID.randomUUID();
        rejection(c1, r1, "Rto 1", MON, "Faltan bidones");
        rejection(c1, r1, "Rto 1", MON, " faltan bidones");
        rejection(c1, r1, "Rto 1", TUE, "Sobran sodas");
        var c2 = UUID.randomUUID();
        rejection(c2, r1, "Rto 1", TUE, "Mal contado");
        rejection(c2, r1, "Rto 1", TUE, "Otra cosa");
        rejection(c2, r1, "Rto 1", TUE, null);
        rejection(UUID.randomUUID(), r1, "Rto 1 viejo", MON, "Antes del cambio");
        rejection(UUID.randomUUID(), r1, "Rto 1", MON.minusDays(5), "Fuera del rango");
        var cancelled = control(branchId, ControlType.ENTRY, route("R9"), MON, "CANCELLED", MON.atTime(14, 0));
        rejection(cancelled, r1, "Rto 1", MON, "De un control cancelado");

        var range      = new MetricsRange(MON, TUE, TUE);
        var rejections = repository.findRejections(scope(), range);
        var reasons    = repository.findTopReasons(scope(), range);

        assertThat(rejections).hasSize(2);
        var current = rejectionsOf(rejections, "Rto 1");
        assertThat(current.rejections()).isEqualTo(6);
        assertThat(current.rejectedControls()).isEqualTo(2);
        assertThat(current.routeId()).isEqualTo(r1);
        assertThat(current.branchName()).isEqualTo("Branch " + branchId);
        assertThat(rejectionsOf(rejections, "Rto 1 viejo").rejections()).isEqualTo(1);
        assertThat(reasons).filteredOn(r -> r.routeCode().equals("Rto 1")).hasSize(3)
                .first().satisfies(r -> {
                    assertThat(r.reason()).isEqualToIgnoringCase("faltan bidones");
                    assertThat(r.count()).isEqualTo(2);
                });
        assertThat(reasons).filteredOn(r -> r.routeCode().equals("Rto 1 viejo")).extracting(Reason::reason)
                .containsExactly("Antes del cambio");
    }

    @Test
    void shouldFallBackToTheCurrentRouteCodeAndKeepUnknownRoutesApart() {
        var r1 = route("R1");
        rejection(UUID.randomUUID(), r1, null, MON, "Sin snapshot");
        rejection(UUID.randomUUID(), null, null, MON, "Sin reparto");

        var range      = new MetricsRange(MON, MON, MON);
        var rejections = repository.findRejections(scope(), range);
        var reasons    = repository.findTopReasons(scope(), range);

        assertThat(rejections).hasSize(2);
        assertThat(rejections).filteredOn(r -> r1.equals(r.routeId())).singleElement()
                .satisfies(r -> assertThat(r.routeCode()).startsWith("R1-"));
        assertThat(rejections).filteredOn(r -> r.routeId() == null).singleElement()
                .satisfies(r -> assertThat(r.routeCode()).isNull());
        assertThat(reasons).filteredOn(r -> r.routeId() == null).extracting(Reason::reason)
                .containsExactly("Sin reparto");
    }

    @Test
    void shouldReturnEveryBranchForTheAllBranchesScope() {
        var other = branch();
        control(branchId, ControlType.ENTRY, route("R1"), MON, "SENT_TO_AGUAS", MON.atTime(14, 0));
        control(other, ControlType.ENTRY, route("R2"), MON, "SENT_TO_AGUAS", MON.atTime(15, 0));

        var arrivals = repository.findArrivals(new MetricsScope(true, null), new MetricsRange(MON, MON, MON));

        assertThat(arrivals).extracting(Arrival::branchId).contains(branchId, other);
    }

    // ---------- helpers ----------

    private MetricsScope scope() {
        return new MetricsScope(false, branchId);
    }

    private UUID branch() {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO branches (id, name, code, active) VALUES (?, ?, ?, true)",
                id, "Branch " + id, "B-" + id.toString().substring(0, 8));
        return id;
    }

    private UUID route(String prefix) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO routes (id, code, branch_id, active) VALUES (?, ?, ?, true)",
                id, prefix + "-" + id, branchId);
        return id;
    }

    private UUID control(UUID branch, ControlType type, UUID routeId, LocalDate date, String status,
                         LocalDateTime createdAt) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO stock_controls (id, type, status, branch_id, route_id, control_date, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                id, type.name(), status, branch, routeId, date, createdAt, createdAt);
        return id;
    }

    private void rejection(UUID controlId, UUID routeId, String routeCode, LocalDate day, String reason) {
        events.saveAndFlush(OperationEvent.builder()
                .eventType(OperationEventType.CONTROL_REJECTED)
                .occurredAt(day.atTime(15, 0).plusNanos(System.nanoTime() % 1_000_000))
                .eventDate(day)
                .subjectType(OperationEventSubject.STOCK_CONTROL)
                .subjectId(controlId)
                .controlType(ControlType.ENTRY)
                .branchId(branchId)
                .routeId(routeId)
                .routeCode(routeCode)
                .reason(reason)
                .build());
    }

    private static Arrival arrival(List<Arrival> arrivals, UUID routeId) {
        return arrivals.stream().filter(a -> a.routeId().equals(routeId)).findFirst().orElseThrow();
    }

    private static Rejections rejectionsOf(List<Rejections> rows, String code) {
        return rows.stream().filter(r -> r.routeCode().equals(code)).findFirst().orElseThrow();
    }
}
