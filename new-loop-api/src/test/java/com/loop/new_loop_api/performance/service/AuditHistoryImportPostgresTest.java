package com.loop.new_loop_api.performance.service;

import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.performance.service.iService.AuditHistoryImportService;
import com.loop.new_loop_api.support.PostgresIntegrationTest;
import com.loop.new_loop_api.users.entity.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Audit entries dated in 2025, before any live event in the shared test database. */
class AuditHistoryImportPostgresTest extends PostgresIntegrationTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2025, 6, 2, 9, 0);

    @Autowired private AuditHistoryImportService importService;
    @Autowired private JdbcTemplate              jdbc;

    private final UUID controlId    = UUID.randomUUID();
    private final UUID controllerId = UUID.randomUUID();

    @BeforeEach
    void actAsAdmin() {
        var admin = new AuthenticatedUser(UUID.randomUUID(), "admin", "Admin", Role.ADMIN, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, null, List.of()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldImportTheHistoryOnceAndNothingOnASecondRun() {
        audit("CREATE_STOCK_CONTROL", null, control("PENDING_DRIVER_APPROVAL"), null, T0);
        audit("REJECT_STOCK_CONTROL", null, control("REJECTED_BY_DRIVER"), "Faltan bidones", T0.plusMinutes(10));
        audit("UPDATE_STOCK_CONTROL", control("REJECTED_BY_DRIVER"), control("PENDING_DRIVER_APPROVAL"), null, T0.plusMinutes(20));
        audit("APPROVE_STOCK_CONTROL", null, control("ACCEPTED_BY_DRIVER"), null, T0.plusMinutes(25));

        var first  = importService.importHistory();
        var second = importService.importHistory();

        // The test database is shared: the first run may also import other tests' audit entries.
        assertThat(first.imported()).isGreaterThanOrEqualTo(4);
        assertThat(second.imported()).isZero();
        assertThat(jdbc.queryForList("""
                SELECT event_type FROM operation_events WHERE subject_id = ? AND imported ORDER BY occurred_at""",
                String.class, controlId))
                .containsExactly("CONTROL_SUBMITTED", "CONTROL_REJECTED", "CONTROL_EDITED", "CONTROL_APPROVED");
        assertThat(jdbc.queryForObject("""
                SELECT rejection_round FROM operation_events WHERE subject_id = ? AND event_type = 'CONTROL_APPROVED'""",
                Integer.class, controlId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM operation_event_items i JOIN operation_events e ON e.id = i.event_id
                WHERE e.subject_id = ?""", Integer.class, controlId)).isEqualTo(2);
    }

    @Test
    void shouldNotImportTheAuditEntryOfAnOperationAlreadyRecordedLive() {
        var liveAt = LocalDateTime.of(2025, 7, 1, 9, 0);
        audit("CREATE_STOCK_CONTROL", null, control("PENDING_DRIVER_APPROVAL"), null, liveAt.minusHours(1));
        // Same operation: its audit entry is written a few milliseconds before its live event.
        audit("REJECT_STOCK_CONTROL", null, control("REJECTED_BY_DRIVER"), "Faltan bidones", liveAt);
        jdbc.update("""
                INSERT INTO operation_events (id, event_type, occurred_at, event_date, subject_type, subject_id,
                    control_type, rejection_round, reason)
                VALUES (?, 'CONTROL_REJECTED', ?, ?, 'STOCK_CONTROL', ?, 'ENTRY', 1, 'Faltan bidones')""",
                UUID.randomUUID(), liveAt.plusNanos(3_000_000), liveAt.toLocalDate(), controlId);

        importService.importHistory();

        assertThat(jdbc.queryForList("SELECT event_type FROM operation_events WHERE subject_id = ? ORDER BY occurred_at",
                String.class, controlId))
                .containsExactly("CONTROL_SUBMITTED", "CONTROL_REJECTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operation_events WHERE subject_id = ? AND imported",
                Integer.class, controlId)).isEqualTo(1);
    }

    private void audit(String action, String oldValue, String newValue, String reason, LocalDateTime createdAt) {
        jdbc.update("""
                INSERT INTO audit_logs (id, user_id, username, user_role, action, entity_name, entity_id,
                    old_value, new_value, reason, source, created_at)
                VALUES (?, ?, 'ctrl1', 'CONTROLADOR', ?, 'StockControl', ?, ?, ?, ?, 'MOBILE_APP', ?)""",
                UUID.randomUUID(), controllerId, action, controlId, oldValue, newValue, reason, createdAt);
    }

    private String control(String status) {
        return "{\"id\":\"" + controlId + "\",\"type\":\"ENTRY\",\"status\":\"" + status + "\",\"branchId\":\""
                + UUID.randomUUID() + "\",\"routeId\":\"" + UUID.randomUUID() + "\",\"routeCode\":\"Rto 9\","
                + "\"controllerId\":\"" + controllerId + "\",\"items\":[{\"productId\":\"" + UUID.randomUUID()
                + "\",\"productName\":\"Bidón 20L\",\"totalQuantity\":10,\"fullQuantity\":8,\"exchangeQuantity\":2}]}";
    }
}
