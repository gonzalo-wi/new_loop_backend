package com.loop.new_loop_api.performance.repository;

import com.loop.new_loop_api.performance.domain.MetricsRange;
import com.loop.new_loop_api.performance.domain.MetricsScope;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.ReasonOwner;
import com.loop.new_loop_api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 001 NFR: the summary for one month and one branch answers in under 2 seconds. Seeds about three
 * times the expected volume of a branch with 45 routes: per day 45 exits and 45 entries with driver
 * responses and edits, and 270 dispenser movements with 10 serials each.
 */
class PerformanceMetricsLoadTest extends PostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PerformanceMetricsLoadTest.class);

    private static final LocalDate FROM   = LocalDate.of(2026, 5, 1);
    private static final LocalDate TO     = FROM.plusDays(29);
    private static final int       DAYS   = 30;
    private static final int       ROUTES = 45;
    private static final Duration  BUDGET = Duration.ofSeconds(2);

    @Autowired private PerformanceMetricsRepository repository;
    @Autowired private JdbcTemplate                 jdbc;

    @Test
    void shouldAnswerTheMonthlySummaryOfABranchWithinTheBudget() {
        var branchId = seedBranch();
        var query    = new MetricsQuery(new MetricsScope(false, branchId), new MetricsRange(FROM, TO, TO), null, null, null, false);
        runSummary(query); // warm-up: the first statement also pays for planning and caches

        var started = System.nanoTime();
        var rows    = runSummary(query);
        var elapsed = Duration.ofNanos(System.nanoTime() - started);

        log.info("Monthly summary of one branch answered in {} ms", elapsed.toMillis());
        assertThat(rows).isPositive();
        assertThat(elapsed).isLessThan(BUDGET);
    }

    private int runSummary(MetricsQuery query) {
        return repository.findControlMetrics(query).size()
                + repository.findDispenserMetrics(query).size()
                + repository.findCoverage(query).size()
                + repository.findTopReasons(query, ReasonOwner.ACTOR).size();
    }

    private UUID seedBranch() {
        var branchId = UUID.randomUUID();
        var tag      = branchId.toString().substring(0, 8);
        jdbc.update("INSERT INTO branches (id, name, code, active) VALUES (?, ?, ?, true)", branchId, "Load " + tag, "L-" + tag);
        jdbc.update("""
                INSERT INTO routes (id, code, branch_id, active)
                SELECT gen_random_uuid(), 'L-' || ? || '-' || r, ?, true FROM generate_series(1, ?) r""",
                tag, branchId, ROUTES);
        // One exit and one entry per route and day.
        jdbc.update("""
                INSERT INTO stock_controls (id, type, status, branch_id, route_id, control_date)
                SELECT gen_random_uuid(), t.type, 'SENT_TO_AGUAS', ?, r.id, CAST(? AS date) + d
                FROM routes r, generate_series(0, ? - 1) d, (VALUES ('EXIT'), ('ENTRY')) t(type)
                WHERE r.branch_id = ?""",
                branchId, FROM, DAYS, branchId);
        // Per control: submitted, then (for entries) rejected, edited and approved: ~3x a real branch.
        jdbc.update("""
                INSERT INTO operation_events (id, event_type, occurred_at, event_date, subject_type, subject_id,
                    control_type, branch_id, responsible_user_id, responsible_username, rejection_round,
                    response_seconds, resubmission, reason)
                SELECT gen_random_uuid(), e.event_type, sc.control_date + time '08:00' + e.offset_minutes * interval '1 minute',
                       sc.control_date, 'STOCK_CONTROL', sc.id, sc.type, sc.branch_id,
                       md5(? || (hashtext(sc.route_id::text) % 10))::uuid, 'load-user', e.round, 300,
                       e.event_type = 'CONTROL_EDITED', CASE WHEN e.event_type = 'CONTROL_REJECTED' THEN 'Faltan bidones' END
                FROM stock_controls sc
                JOIN (VALUES ('CONTROL_SUBMITTED', 0, 0, 'ANY'), ('CONTROL_REJECTED', 10, 1, 'ENTRY'),
                             ('CONTROL_EDITED', 15, NULL, 'ENTRY'), ('CONTROL_APPROVED', 20, 1, 'ENTRY'))
                     e(event_type, offset_minutes, round, applies_to)
                  ON e.applies_to = 'ANY' OR e.applies_to = sc.type
                WHERE sc.branch_id = ?""",
                tag, branchId);
        jdbc.update("""
                INSERT INTO dispenser_movements (id, type, route_code, technician, location_id, state_id, movement_date,
                    status, registered_by, registered_by_username, created_at, updated_at, load_duration_seconds, branch_id)
                SELECT gen_random_uuid(), CASE WHEN g % 2 = 0 THEN 'LOAD' ELSE 'UNLOAD' END, 'L-' || ? || '-' || g, 'Tech', 2, 2,
                       CAST(? AS date) + (g % ?), 'SENT_TO_AGUAS', md5(? || (g % 10))::uuid, 'load-user',
                       CAST(? AS date) + (g % ?) + time '06:00' + (g / ?) * interval '3 minutes',
                       CAST(? AS date) + (g % ?) + time '06:00', 600 + g % 900, ?
                FROM generate_series(1, ? * 270) g""",
                tag, FROM, DAYS, tag, FROM, DAYS, DAYS, FROM, DAYS, branchId, DAYS);
        jdbc.update("""
                INSERT INTO dispenser_movement_serials (movement_id, serial)
                SELECT m.id, 'SN-' || n FROM dispenser_movements m, generate_series(1, 10) n WHERE m.branch_id = ?""",
                branchId);
        jdbc.execute("ANALYZE operation_events, dispenser_movements, dispenser_movement_serials, stock_controls");
        return branchId;
    }
}
