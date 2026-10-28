package com.loop.new_loop_api.performance.repository;

import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.performance.domain.MetricsRange;
import com.loop.new_loop_api.performance.domain.MetricsScope;
import com.loop.new_loop_api.performance.entity.OperationEvent;
import com.loop.new_loop_api.performance.entity.OperationEventSubject;
import com.loop.new_loop_api.performance.entity.OperationEventType;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.ControlMetricsRow;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.DispenserMetricsRow;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.ReasonOwner;
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
 * Every test works on its own fresh branch and users: the test database is shared across runs and
 * operation events can never be deleted.
 */
class PerformanceMetricsRepositoryTest extends PostgresIntegrationTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 19);
    private static final LocalDate TUE = MON.plusDays(1);
    private static final LocalDate WED = MON.plusDays(2);

    @Autowired private PerformanceMetricsRepository repository;
    @Autowired private OperationEventRepository     events;
    @Autowired private JdbcTemplate                 jdbc;

    private UUID branchId;
    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUp() {
        branchId = UUID.randomUUID();
        jdbc.update("INSERT INTO branches (id, name, code, active) VALUES (?, ?, ?, true)",
                branchId, "Branch " + branchId, "B-" + branchId.toString().substring(0, 8));
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
    }

    // ---------- stock controls ----------

    @Test
    void shouldComputeControlMetricsPerUser() {
        var c1 = UUID.randomUUID(); // rejected twice, resubmitted twice, approved, corrected → faulty
        event(c1, OperationEventType.CONTROL_SUBMITTED, ControlType.ENTRY, userA, MON, b -> b);
        event(c1, OperationEventType.CONTROL_REJECTED, ControlType.ENTRY, userA, MON, b -> b.rejectionRound(1).responseSeconds(600L).reason("Faltan bidones"));
        event(c1, OperationEventType.CONTROL_EDITED, ControlType.ENTRY, userA, MON, b -> b.resubmission(true));
        event(c1, OperationEventType.CONTROL_REJECTED, ControlType.ENTRY, userA, MON, b -> b.rejectionRound(2).responseSeconds(300L).reason("faltan bidones "));
        event(c1, OperationEventType.CONTROL_EDITED, ControlType.ENTRY, userA, MON, b -> b.resubmission(true));
        event(c1, OperationEventType.CONTROL_APPROVED, ControlType.ENTRY, userA, MON, b -> b.rejectionRound(2).responseSeconds(120L));
        event(c1, OperationEventType.CONTROL_CORRECTED, ControlType.ENTRY, userA, TUE, b -> b.reason("Cantidad mal cargada"));
        var c2 = UUID.randomUUID(); // approved on the first try
        event(c2, OperationEventType.CONTROL_SUBMITTED, ControlType.ENTRY, userA, MON, b -> b);
        event(c2, OperationEventType.CONTROL_APPROVED, ControlType.ENTRY, userA, MON, b -> b.rejectionRound(0).responseSeconds(60L));
        event(UUID.randomUUID(), OperationEventType.CONTROL_SUBMITTED, ControlType.EXIT, userA, MON, b -> b);
        var c4 = UUID.randomUUID(); // two failed sends: one control with errors
        event(c4, OperationEventType.CONTROL_SUBMITTED, ControlType.EXIT, userA, TUE, b -> b);
        event(c4, OperationEventType.INTEGRATION_ERROR, ControlType.EXIT, userA, TUE, b -> b);
        event(c4, OperationEventType.INTEGRATION_ERROR, ControlType.EXIT, userA, TUE, b -> b);
        var c5 = UUID.randomUUID(); // edited while pending: faulty, not a resubmission
        event(c5, OperationEventType.CONTROL_SUBMITTED, ControlType.ENTRY, userB, MON, b -> b);
        event(c5, OperationEventType.CONTROL_EDITED, ControlType.ENTRY, userB, MON, b -> b);
        var cancelled = cancelledControl();
        event(cancelled, OperationEventType.CONTROL_SUBMITTED, ControlType.ENTRY, userA, MON, b -> b);

        var rows = repository.findControlMetrics(query(false, null));

        var a = row(rows, userA);
        assertThat(a.exitsSubmitted()).isEqualTo(2);
        assertThat(a.entriesSubmitted()).isEqualTo(2);
        assertThat(a.edits()).isEqualTo(2);
        assertThat(a.resubmissions()).isEqualTo(2);
        assertThat(a.rejectionRounds()).isEqualTo(2);
        assertThat(a.approvals()).isEqualTo(2);
        assertThat(a.firstPassApprovals()).isEqualTo(1);
        assertThat(a.avgResponseSeconds()).isEqualTo(270.0);
        assertThat(a.corrections()).isEqualTo(1);
        assertThat(a.faultyControls()).isEqualTo(1);
        assertThat(a.controlsWithIntegrationErrors()).isEqualTo(1);
        assertThat(a.username()).isEqualTo("user-" + userA);
        // Corrected on Tuesday, but a faulty control counts on the day it was submitted (Monday).
        var perDay = repository.findControlMetrics(query(true, userA));
        assertThat(perDay).filteredOn(r -> MON.equals(r.day())).singleElement()
                .satisfies(r -> assertThat(r.faultyControls()).isEqualTo(1));
        assertThat(perDay).filteredOn(r -> TUE.equals(r.day())).singleElement()
                .satisfies(r -> assertThat(r.faultyControls()).isZero());
        var b = row(rows, userB);
        assertThat(b.faultyControls()).isEqualTo(1);
        assertThat(b.resubmissions()).isZero();
        assertThat(b.avgResponseSeconds()).isNull();
    }

    @Test
    void shouldSplitControlMetricsPerDayAndFilterByUserAndType() {
        var c1 = UUID.randomUUID();
        event(c1, OperationEventType.CONTROL_SUBMITTED, ControlType.ENTRY, userA, MON, b -> b);
        event(UUID.randomUUID(), OperationEventType.CONTROL_SUBMITTED, ControlType.ENTRY, userA, TUE, b -> b);
        event(UUID.randomUUID(), OperationEventType.CONTROL_SUBMITTED, ControlType.EXIT, userA, TUE, b -> b);
        event(UUID.randomUUID(), OperationEventType.CONTROL_SUBMITTED, ControlType.ENTRY, userB, TUE, b -> b);

        var rows = repository.findControlMetrics(new MetricsQuery(
                new MetricsScope(false, branchId), range(), userA, ControlType.ENTRY, null, true));

        assertThat(rows).extracting(ControlMetricsRow::day).containsExactly(MON, TUE);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.userId()).isEqualTo(userA);
            assertThat(r.entriesSubmitted()).isEqualTo(1);
            assertThat(r.exitsSubmitted()).isZero();
        });
    }

    @Test
    void shouldGroupReasonsIgnoringCaseAndSpacesPerActor() {
        var driver = UUID.randomUUID();
        var c1 = UUID.randomUUID();
        event(c1, OperationEventType.CONTROL_REJECTED, ControlType.ENTRY, userA, MON,
                b -> b.reason("Faltan bidones").actorUserId(driver).actorUsername("driver1"));
        event(c1, OperationEventType.CONTROL_REJECTED, ControlType.ENTRY, userA, TUE,
                b -> b.reason("  faltan BIDONES ").actorUserId(driver).actorUsername("driver1"));
        event(UUID.randomUUID(), OperationEventType.CONTROL_CORRECTED, ControlType.ENTRY, userB, TUE,
                b -> b.reason("Cantidad mal cargada").actorUserId(UUID.randomUUID()).actorUsername("super1"));

        var byActor = repository.findTopReasons(query(false, null), ReasonOwner.ACTOR);
        var byUser  = repository.findTopReasons(query(false, userA), ReasonOwner.RESPONSIBLE);

        assertThat(byActor).first().satisfies(r -> {
            assertThat(r.eventType()).isEqualTo(OperationEventType.CONTROL_REJECTED);
            assertThat(r.reason()).isEqualTo("faltan BIDONES");
            assertThat(r.userId()).isEqualTo(driver);
            assertThat(r.count()).isEqualTo(2);
        });
        assertThat(byUser).singleElement().satisfies(r -> assertThat(r.userId()).isEqualTo(userA));
    }

    // ---------- dispenser movements ----------

    @Test
    void shouldComputeDispenserMetricsWithEditChainsPausesAndCancellations() {
        var m1 = movement(DispenserMovementType.LOAD, userA, MON.atTime(8, 0), 600, false, null, branchId);
        serials(m1, 3);
        excluded(m1, 1);
        movement(DispenserMovementType.LOAD, userA, MON.atTime(8, 20), 2000, false, null, branchId);   // slow, gap 20 min
        movement(DispenserMovementType.UNLOAD, userA, MON.atTime(10, 0), null, false, null, branchId); // pause of 100 min
        var m4 = movement(DispenserMovementType.LOAD, userA, MON.atTime(10, 10), null, true, null, branchId);
        movement(DispenserMovementType.LOAD, userB, MON.atTime(10, 30), null, false, m4, branchId);    // edit of m4, by someone else
        movement(DispenserMovementType.LOAD, userA, MON.atTime(11, 0), null, true, null, branchId);    // real cancellation
        movement(DispenserMovementType.LOAD, userA, TUE.atTime(0, 5), null, false, null, branchId);    // next day: no gap
        movement(DispenserMovementType.LOAD, userA, MON.atTime(9, 0), null, false, null, null);        // no branch

        var rows = repository.findDispenserMetrics(query(false, null));

        var a = dispenserRow(rows, userA);
        assertThat(a.loads()).isEqualTo(4);
        assertThat(a.unloads()).isEqualTo(1);
        assertThat(a.serials()).isEqualTo(3);
        assertThat(a.excludedUnavailableInOdoo()).isEqualTo(1);
        assertThat(a.excludedUnregistered()).isZero();
        assertThat(a.avgLoadSeconds()).isEqualTo(1300.0);
        assertThat(a.medianLoadSeconds()).isEqualTo(1300.0);
        assertThat(a.slowLoads()).isEqualTo(1);
        assertThat(a.avgGapSeconds()).isEqualTo(900.0);
        assertThat(a.medianGapSeconds()).isEqualTo(900.0);
        assertThat(a.pauses()).isEqualTo(1);
        assertThat(a.cancellations()).isEqualTo(1);
        assertThat(rows).extracting(DispenserMetricsRow::userId).doesNotContain(userB);
    }

    @Test
    void shouldIncludeMovementsWithoutBranchOnlyForAllBranches() {
        movement(DispenserMovementType.LOAD, userA, MON.atTime(9, 0), null, false, null, null);

        var scoped = repository.findDispenserMetrics(query(false, userA));
        var all    = repository.findDispenserMetrics(new MetricsQuery(
                new MetricsScope(true, null), range(), userA, null, null, false));

        assertThat(scoped).isEmpty();
        assertThat(dispenserRow(all, userA).loads()).isEqualTo(1);
    }

    @Test
    void shouldSplitDispenserMetricsPerDay() {
        movement(DispenserMovementType.LOAD, userA, MON.atTime(8, 0), null, false, null, branchId);
        movement(DispenserMovementType.LOAD, userA, MON.atTime(8, 10), null, false, null, branchId);
        movement(DispenserMovementType.LOAD, userA, TUE.atTime(8, 0), null, false, null, branchId);

        var rows = repository.findDispenserMetrics(query(true, userA));

        assertThat(rows).extracting(DispenserMetricsRow::day).containsExactly(MON, TUE);
        assertThat(rows.get(0).avgGapSeconds()).isEqualTo(600.0);
        assertThat(rows.get(1).avgGapSeconds()).isNull();
    }

    // ---------- coverage ----------

    @Test
    void shouldReportMissingEntriesUntilTheLastEndedDay() {
        var r1 = route();
        var r2 = route();
        var r3 = route();
        control(ControlType.EXIT, r1, MON, "SENT_TO_AGUAS");
        control(ControlType.ENTRY, r1, MON, "SENT_TO_AGUAS");          // came back
        control(ControlType.EXIT, r2, MON, "SENT_TO_AGUAS");            // missing
        control(ControlType.EXIT, r3, MON, "SENT_TO_AGUAS");
        control(ControlType.ENTRY, r3, MON, "CANCELLED");               // cancelled entry: missing
        control(ControlType.EXIT, r1, WED, "SENT_TO_AGUAS");            // WED has not ended yet
        var r4 = route();
        control(ControlType.EXIT, r4, MON, "SENT_TO_AGUAS");
        controlInBranch(otherBranch(), ControlType.ENTRY, r4, MON);     // entry recorded in another branch: came back

        var rows = repository.findCoverage(new MetricsQuery(new MetricsScope(false, branchId),
                new MetricsRange(MON, WED, TUE), null, null, null, false));

        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.controlDate()).isEqualTo(MON);
            assertThat(r.expectedEntries()).isEqualTo(4);
            assertThat(r.missingEntries()).isEqualTo(2);
            assertThat(r.missingRoutes()).hasSize(2);
        });
    }

    // ---------- helpers ----------

    private MetricsQuery query(boolean byDay, UUID userId) {
        return new MetricsQuery(new MetricsScope(false, branchId), range(), userId, null, null, byDay);
    }

    private static MetricsRange range() {
        return new MetricsRange(MON, WED, WED);
    }

    private void event(UUID subjectId, OperationEventType type, ControlType controlType, UUID responsible, LocalDate day,
                       java.util.function.UnaryOperator<OperationEvent.OperationEventBuilder> extra) {
        events.saveAndFlush(extra.apply(OperationEvent.builder()
                .eventType(type)
                .occurredAt(day.atTime(9, 0).plusNanos(System.nanoTime() % 1_000_000))
                .eventDate(day)
                .subjectType(OperationEventSubject.STOCK_CONTROL)
                .subjectId(subjectId)
                .controlType(controlType)
                .branchId(branchId)
                .responsibleUserId(responsible)
                .responsibleUsername("user-" + responsible)).build());
    }

    private UUID cancelledControl() {
        return control(ControlType.ENTRY, route(), MON.minusDays(10), "CANCELLED");
    }

    private UUID otherBranch() {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO branches (id, name, code, active) VALUES (?, ?, ?, true)",
                id, "Branch " + id, "B-" + id.toString().substring(0, 8));
        return id;
    }

    private void controlInBranch(UUID branch, ControlType type, UUID routeId, LocalDate date) {
        jdbc.update("INSERT INTO stock_controls (id, type, status, branch_id, route_id, control_date) VALUES (?, ?, 'SENT_TO_AGUAS', ?, ?, ?)",
                UUID.randomUUID(), type.name(), branch, routeId, date);
    }

    private UUID route() {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO routes (id, code, branch_id, active) VALUES (?, ?, ?, true)", id, "T-" + id, branchId);
        return id;
    }

    private UUID control(ControlType type, UUID routeId, LocalDate date, String status) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO stock_controls (id, type, status, branch_id, route_id, control_date) VALUES (?, ?, ?, ?, ?, ?)",
                id, type.name(), status, branchId, routeId, date);
        return id;
    }

    private UUID movement(DispenserMovementType type, UUID user, LocalDateTime createdAt, Integer duration,
                          boolean cancelled, UUID replaces, UUID branch) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dispenser_movements (id, type, route_code, technician, location_id, state_id, movement_date,
                    status, registered_by, registered_by_username, created_at, updated_at,
                    load_duration_seconds, branch_id, replaces_movement_id)
                VALUES (?, ?, ?, 'Tech', 2, 2, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, type.name(), "R-" + id, createdAt.toLocalDate(), cancelled ? "CANCELLED" : "SENT_TO_AGUAS",
                user, "user-" + user, createdAt, createdAt, duration, branch, replaces);
        return id;
    }

    private void serials(UUID movementId, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("INSERT INTO dispenser_movement_serials (movement_id, serial) VALUES (?, ?)", movementId, "SN-" + i);
        }
    }

    private void excluded(UUID movementId, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("INSERT INTO dispenser_movement_excluded_serials (movement_id, serial) VALUES (?, ?)", movementId, "SN-" + i);
        }
    }

    private static ControlMetricsRow row(List<ControlMetricsRow> rows, UUID userId) {
        return rows.stream().filter(r -> userId.equals(r.userId())).findFirst().orElseThrow();
    }

    private static DispenserMetricsRow dispenserRow(List<DispenserMetricsRow> rows, UUID userId) {
        return rows.stream().filter(r -> userId.equals(r.userId())).findFirst().orElseThrow();
    }
}
