package com.loop.new_loop_api.integrations.common.repository;

import com.loop.new_loop_api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationIssueRepositoryTest extends PostgresIntegrationTest {

    @Autowired private IntegrationIssueRepository repository;
    @Autowired private JdbcTemplate               jdbc;

    private final LocalDateTime now          = LocalDateTime.now();
    private final LocalDateTime stuckBefore  = now.minusMinutes(30);
    private final LocalDateTime lookbackFrom = now.minusDays(30);

    private String routeCode;
    // V30 allows one active movement per type/route/date, so each inserted movement gets its own date.
    private int    movementsInserted;

    @BeforeEach
    void setUp() {
        // Shared test DB: every assertion is scoped to the rows this test inserted.
        routeCode = "R-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void shouldReturnFailedLogsWithTheirMovementContext() {
        var movement = movement("SENT_TO_AGUAS", now.minusHours(2));
        var failed   = log(movement, "ODOO", "DISPATCH_CREATE", "ERROR", now.minusHours(1));

        var row = rowFor(repository.findLogIssues(stuckBefore), failed);

        assertThat(row.routeCode()).isEqualTo(routeCode);
        assertThat(row.entityKind()).isEqualTo("LOAD");
        assertThat(row.entityDate()).isEqualTo(LocalDate.now());
        assertThat(row.lastError()).isEqualTo("boom");
    }

    @Test
    void shouldSkipAFailureThatALaterAttemptAlreadySent() {
        var movement = movement("SENT_TO_AGUAS", now.minusHours(3));
        var failed   = log(movement, "AGUAS", "EQUIPMENT_OUT", "ERROR", now.minusHours(2));
        log(movement, "AGUAS", "EQUIPMENT_OUT", "SENT", now.minusHours(1));

        assertThat(logIds(repository.findLogIssues(stuckBefore))).doesNotContain(failed);
    }

    @Test
    void shouldIgnoreFailuresOfMovementsThatWereCancelledAfterwards() {
        var movement = movement("CANCELLED", now.minusHours(3));
        var failed   = log(movement, "AGUAS", "EQUIPMENT_OUT", "ERROR", now.minusHours(2));

        assertThat(logIds(repository.findLogIssues(stuckBefore))).doesNotContain(failed);
    }

    @Test
    void shouldReportAFailedCancellationOfAMovementStillInAguas() {
        // A failed Aguas delete rolls the cancellation back, so the movement stays SENT_TO_AGUAS.
        var movement     = movement("SENT_TO_AGUAS", now.minusHours(3));
        var deleteFailed = log(movement, "AGUAS", "EQUIPMENT_DELETE", "ERROR", now.minusHours(1));

        assertThat(logIds(repository.findLogIssues(stuckBefore))).contains(deleteFailed);
    }

    @Test
    void shouldShowOnlyTheLatestFailurePerRecordAndOperation() {
        var movement = movement("AGUAS_ERROR", now.minusHours(3));
        var first    = log(movement, "AGUAS", "EQUIPMENT_OUT", "ERROR", now.minusHours(2));
        var latest   = log(movement, "AGUAS", "EQUIPMENT_OUT", "ERROR", now.minusHours(1));

        var ids = logIds(repository.findLogIssues(stuckBefore));

        assertThat(ids).contains(latest).doesNotContain(first);
    }

    @Test
    void shouldReportPendingAttemptsOnlyOnceTheyAreStuck() {
        var movement = movement("REGISTERED", now.minusHours(3));
        var stuck    = log(movement, "AGUAS", "EQUIPMENT_OUT", "PENDING", now.minusHours(1));
        var fresh    = log(movement("REGISTERED", now.minusMinutes(5)), "AGUAS", "EQUIPMENT_OUT", "PENDING", now.minusMinutes(5));

        var ids = logIds(repository.findLogIssues(stuckBefore));

        assertThat(ids).contains(stuck).doesNotContain(fresh);
    }

    @Test
    void shouldFindMovementsThatNeverReachedAnySendAttempt() {
        var neverSent  = movement("REGISTERED", now.minusHours(2));
        var attempted  = movement("REGISTERED", now.minusHours(2));
        log(attempted, "AGUAS", "EQUIPMENT_OUT", "ERROR", now.minusHours(1));
        var tooRecent  = movement("REGISTERED", now.minusMinutes(5));
        var tooOld     = movement("REGISTERED", now.minusDays(60));

        var ids = entityIds(repository.findNeverSent(stuckBefore, lookbackFrom));

        assertThat(ids).contains(neverSent).doesNotContain(attempted, tooRecent, tooOld);
    }

    @Test
    void shouldFindExitControlsThatWereNeverSentToAguas() {
        var control = exitControl(now.minusHours(2));

        var row = repository.findNeverSent(stuckBefore, lookbackFrom).stream()
                .filter(r -> control.equals(r.entityId()))
                .findFirst().orElseThrow();

        assertThat(row.entityName()).isEqualTo("StockControl");
        assertThat(row.entityKind()).isEqualTo("EXIT");
        assertThat(row.routeCode()).startsWith(routeCode);
        assertThat(row.logId()).isNull();
    }

    @Test
    void shouldFindApprovedEntryControlsThatWereNeverSent() {
        var control = control("ENTRY", "ACCEPTED_BY_DRIVER", now.minusDays(2), now.minusHours(2));

        assertThat(entityIds(repository.findNeverSent(stuckBefore, lookbackFrom))).contains(control);
    }

    @Test
    void shouldIgnoreEntryControlsStillWaitingForTheDriver() {
        var control = control("ENTRY", "PENDING_DRIVER_APPROVAL", now.minusHours(2), null);

        assertThat(entityIds(repository.findNeverSent(stuckBefore, lookbackFrom))).doesNotContain(control);
    }

    private static IntegrationIssueRow rowFor(List<IntegrationIssueRow> rows, UUID logId) {
        return rows.stream().filter(r -> logId.equals(r.logId())).findFirst().orElseThrow();
    }

    private static List<UUID> logIds(List<IntegrationIssueRow> rows) {
        return rows.stream().map(IntegrationIssueRow::logId).toList();
    }

    private static List<UUID> entityIds(List<IntegrationIssueRow> rows) {
        return rows.stream().map(IntegrationIssueRow::entityId).toList();
    }

    private UUID movement(String status, LocalDateTime createdAt) {
        var id = UUID.randomUUID();
        jdbc.update("""
                insert into dispenser_movements (id, type, route_code, technician, location_id, state_id, movement_date, status, created_at)
                values (?, 'LOAD', ?, 'Tech', 2, 2, ?, ?, ?)
                """, id, routeCode, LocalDate.now().minusDays(movementsInserted++), status, Timestamp.valueOf(createdAt));
        return id;
    }

    private UUID exitControl(LocalDateTime createdAt) {
        return control("EXIT", "CONTROLLED", createdAt, null);
    }

    private UUID control(String type, String status, LocalDateTime createdAt, LocalDateTime approvedAt) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var branch = UUID.randomUUID();
        var route  = UUID.randomUUID();
        var id     = UUID.randomUUID();
        jdbc.update("insert into branches (id, name, code) values (?, ?, ?)", branch, "Branch " + suffix, "B-" + suffix);
        jdbc.update("insert into routes (id, code, branch_id) values (?, ?, ?)", route, routeCode + "-" + suffix, branch);
        jdbc.update("""
                insert into stock_controls (id, type, status, branch_id, route_id, control_date, created_at, approved_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, type, status, branch, route, LocalDate.now(), Timestamp.valueOf(createdAt),
                approvedAt == null ? null : Timestamp.valueOf(approvedAt));
        return id;
    }

    private UUID log(UUID movementId, String integration, String operation, String status, LocalDateTime createdAt) {
        var id = UUID.randomUUID();
        jdbc.update("""
                insert into integration_logs (id, integration_name, operation_type, entity_name, entity_id, status, error_message, created_at)
                values (?, ?, ?, 'DispenserMovement', ?, ?, 'boom', ?)
                """, id, integration, operation, movementId, status, Timestamp.valueOf(createdAt));
        return id;
    }
}
