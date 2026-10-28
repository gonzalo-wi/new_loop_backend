package com.loop.new_loop_api.performance.repository;

import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.performance.domain.LoadDuration;
import com.loop.new_loop_api.performance.entity.OperationEventSubject;
import com.loop.new_loop_api.performance.entity.OperationEventType;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregates for the performance page (spec 001). Everything is computed in Postgres.
 * Stock-control figures come from operation_events; dispenser figures from dispenser_movements, where
 * an edit chain (replaces_movement_id) counts once, at the time and for the user of its first movement.
 */
@Repository
public class PerformanceMetricsRepository {

    public static final int SLOW_LOAD_SECONDS = 30 * 60;
    public static final int PAUSE_SECONDS     = LoadDuration.MAX_PLAUSIBLE_SECONDS;
    public static final int MAX_REASONS       = 50;

    public record ControlMetricsRow(UUID userId, String username, LocalDate day,
                                    int exitsSubmitted, int entriesSubmitted, int edits, int resubmissions,
                                    int rejectionRounds, int approvals, int firstPassApprovals,
                                    Double avgResponseSeconds, int corrections, int faultyControls,
                                    int controlsWithIntegrationErrors) {}

    public record DispenserMetricsRow(UUID userId, String username, LocalDate day,
                                      int loads, int unloads, int serials,
                                      int excludedUnregistered, int excludedUnavailableInOdoo,
                                      Double avgLoadSeconds, Double medianLoadSeconds, int slowLoads,
                                      Double avgGapSeconds, Double medianGapSeconds, int pauses,
                                      int cancellations, int movementsWithIntegrationErrors) {}

    public record CoverageRow(UUID branchId, String branchName, LocalDate controlDate,
                              int expectedEntries, int missingEntries, List<String> missingRoutes) {}

    public record ReasonRow(OperationEventType eventType, String reason, UUID userId, String username, int count) {}

    /** Who a reason is counted for: the user it is attributed to, or who wrote it (the driver, the corrector). */
    public enum ReasonOwner { RESPONSIBLE, ACTOR }

    private static final String CONTROL_SQL = """
            WITH e AS (
                SELECT oe.*, CASE WHEN :byDay THEN oe.event_date END AS day
                FROM operation_events oe
                WHERE oe.subject_type = :stockControl
                  AND oe.event_date BETWEEN :from AND :to
                  AND (:allBranches OR oe.branch_id = CAST(:branchId AS uuid))
                  AND (CAST(:userId AS uuid) IS NULL OR oe.responsible_user_id = CAST(:userId AS uuid))
                  AND (CAST(:controlType AS varchar) IS NULL OR oe.control_type = CAST(:controlType AS varchar))
                  AND NOT EXISTS (SELECT 1 FROM stock_controls sc
                                  WHERE sc.id = oe.subject_id AND sc.status = :cancelledControl)
            )
            SELECT e.responsible_user_id                                                        AS user_id,
                   (array_agg(e.responsible_username ORDER BY e.occurred_at DESC))[1]          AS username,
                   e.day,
                   count(*) FILTER (WHERE e.event_type = :submitted AND e.control_type = :exit)  AS exits_submitted,
                   count(*) FILTER (WHERE e.event_type = :submitted AND e.control_type = :entry) AS entries_submitted,
                   count(*) FILTER (WHERE e.event_type = :edited)                              AS edits,
                   count(*) FILTER (WHERE e.event_type = :edited AND e.resubmission)           AS resubmissions,
                   count(*) FILTER (WHERE e.event_type = :rejected)                            AS rejection_rounds,
                   count(*) FILTER (WHERE e.event_type = :approved)                            AS approvals,
                   count(*) FILTER (WHERE e.event_type = :approved AND e.rejection_round = 0)  AS first_pass,
                   avg(e.response_seconds) FILTER (WHERE e.event_type IN (:approved, :rejected)) AS avg_response,
                   count(*) FILTER (WHERE e.event_type = :corrected)                           AS corrections,
                   count(*) FILTER (WHERE e.event_type = :submitted
                                      AND EXISTS (SELECT 1 FROM operation_events f
                                                  WHERE f.subject_id = e.subject_id
                                                    AND f.event_type IN (:rejected, :edited, :corrected))) AS faulty_controls,
                   count(DISTINCT e.subject_id) FILTER (WHERE e.event_type = :integrationError) AS with_errors
            FROM e
            GROUP BY e.responsible_user_id, e.day
            ORDER BY e.day NULLS FIRST, username
            """;

    // Edits cancel the old movement and create a new one: the chain keeps the first movement's time and
    // user, so an edit made by someone else (e.g. from the panel) stays with the operator who loaded.
    // The anchor is bounded by the range (the chain's time is its first movement's) so each request only
    // walks that range's movements and their later edits, not the whole never-purged table.
    private static final String DISPENSER_SQL = """
            WITH RECURSIVE chain AS (
                SELECT id, created_at AS registered_at, registered_by AS user_id, registered_by_username AS username
                FROM dispenser_movements
                WHERE replaces_movement_id IS NULL
                  AND created_at >= :fromStart AND created_at < :toEnd
                UNION ALL
                SELECT d.id, c.registered_at, c.user_id, c.username
                FROM dispenser_movements d
                JOIN chain c ON d.replaces_movement_id = c.id
            ), m AS (
                SELECT dm.id, dm.type, dm.status, dm.load_duration_seconds,
                       c.user_id, c.username, c.registered_at,
                       CAST(c.registered_at AS date)                                          AS reg_date,
                       CASE WHEN :byDay THEN CAST(c.registered_at AS date) END                AS day,
                       (dm.status <> :cancelledMovement)                                     AS active,
                       (SELECT count(*) FROM dispenser_movement_serials s
                         WHERE s.movement_id = dm.id)                                        AS serial_count,
                       (SELECT count(*) FROM dispenser_movement_excluded_serials x
                         WHERE x.movement_id = dm.id)                                        AS excluded_count,
                       EXISTS (SELECT 1 FROM dispenser_movements nx
                               WHERE nx.replaces_movement_id = dm.id)                        AS replaced,
                       EXISTS (SELECT 1 FROM operation_events oe
                               WHERE oe.subject_id = dm.id AND oe.event_type = :integrationError) AS has_error
                FROM dispenser_movements dm
                JOIN chain c ON c.id = dm.id
                WHERE CAST(c.registered_at AS date) BETWEEN :from AND :to
                  AND (:allBranches OR dm.branch_id = CAST(:branchId AS uuid))
                  AND (CAST(:userId AS uuid) IS NULL OR c.user_id = CAST(:userId AS uuid))
                  AND (CAST(:movementType AS varchar) IS NULL OR dm.type = CAST(:movementType AS varchar))
            ), per_user AS (
                SELECT m.user_id,
                       m.day,
                       (array_agg(m.username ORDER BY m.registered_at DESC))[1]               AS username,
                       count(*) FILTER (WHERE m.active AND m.type = :load)                    AS loads,
                       count(*) FILTER (WHERE m.active AND m.type = :unload)                  AS unloads,
                       COALESCE(sum(m.serial_count) FILTER (WHERE m.active), 0)               AS serials,
                       COALESCE(sum(m.excluded_count) FILTER (WHERE m.active AND m.type = :unload), 0) AS excluded_unregistered,
                       COALESCE(sum(m.excluded_count) FILTER (WHERE m.active AND m.type = :load), 0)   AS excluded_unavailable,
                       avg(m.load_duration_seconds) FILTER (WHERE m.active)                   AS avg_load,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY m.load_duration_seconds)
                           FILTER (WHERE m.active AND m.load_duration_seconds IS NOT NULL)    AS median_load,
                       count(*) FILTER (WHERE m.active AND m.load_duration_seconds > :slowLoadSeconds) AS slow_loads,
                       count(*) FILTER (WHERE NOT m.active AND NOT m.replaced)                AS cancellations,
                       count(*) FILTER (WHERE m.active AND m.has_error)                       AS with_errors
                FROM m
                GROUP BY m.user_id, m.day
            ), gaps AS (
                SELECT m.user_id, m.day,
                       CAST(EXTRACT(EPOCH FROM m.registered_at - LAG(m.registered_at)
                            OVER (PARTITION BY m.user_id, m.reg_date ORDER BY m.registered_at))
                            AS double precision)                                              AS gap_seconds
                FROM m
                WHERE m.active
            ), gap_stats AS (
                SELECT g.user_id,
                       g.day,
                       avg(g.gap_seconds) FILTER (WHERE g.gap_seconds < :pauseSeconds)        AS avg_gap,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY g.gap_seconds)
                           FILTER (WHERE g.gap_seconds < :pauseSeconds)                       AS median_gap,
                       count(*) FILTER (WHERE g.gap_seconds >= :pauseSeconds)                 AS pauses
                FROM gaps g
                WHERE g.gap_seconds IS NOT NULL
                GROUP BY g.user_id, g.day
            )
            SELECT p.*, gs.avg_gap, gs.median_gap, COALESCE(gs.pauses, 0) AS pauses
            FROM per_user p
            LEFT JOIN gap_stats gs ON gs.user_id IS NOT DISTINCT FROM p.user_id
                                  AND gs.day IS NOT DISTINCT FROM p.day
            ORDER BY p.day NULLS FIRST, p.username
            """;

    private static final String COVERAGE_SQL = """
            WITH exits AS (
                SELECT DISTINCT sc.branch_id, sc.route_id, sc.control_date
                FROM stock_controls sc
                WHERE sc.type = :exit
                  AND sc.status <> :cancelledControl
                  AND sc.control_date BETWEEN :from AND :coverageEnd
                  AND (:allBranches OR sc.branch_id = CAST(:branchId AS uuid))
            ), entries AS (
                SELECT DISTINCT sc.route_id, sc.control_date
                FROM stock_controls sc
                WHERE sc.type = :entry
                  AND sc.status <> :cancelledControl
                  AND sc.control_date BETWEEN :from AND :coverageEnd
            )
            SELECT x.branch_id, b.name AS branch_name, x.control_date,
                   count(*)                                                       AS expected,
                   count(*) FILTER (WHERE en.route_id IS NULL)                    AS missing,
                   array_agg(r.code ORDER BY r.code) FILTER (WHERE en.route_id IS NULL) AS missing_routes
            FROM exits x
            JOIN routes r   ON r.id = x.route_id
            JOIN branches b ON b.id = x.branch_id
            LEFT JOIN entries en ON en.route_id = x.route_id AND en.control_date = x.control_date
            GROUP BY x.branch_id, b.name, x.control_date
            ORDER BY x.control_date, b.name
            """;

    // Reasons are free text: grouped ignoring case and surrounding spaces, shown as last written.
    private static final String REASONS_SQL = """
            SELECT oe.event_type,
                   (array_agg(trim(oe.reason) ORDER BY oe.occurred_at DESC))[1] AS reason,
                   %1$s_user_id                                                 AS user_id,
                   (array_agg(%1$s_username ORDER BY oe.occurred_at DESC))[1]   AS username,
                   count(*)                                                     AS total
            FROM operation_events oe
            WHERE oe.event_type IN (:rejected, :corrected)
              AND oe.reason IS NOT NULL AND trim(oe.reason) <> ''
              AND oe.event_date BETWEEN :from AND :to
              AND (:allBranches OR oe.branch_id = CAST(:branchId AS uuid))
              AND (CAST(:userId AS uuid) IS NULL OR oe.responsible_user_id = CAST(:userId AS uuid))
              AND (CAST(:controlType AS varchar) IS NULL OR oe.control_type = CAST(:controlType AS varchar))
              AND NOT EXISTS (SELECT 1 FROM stock_controls sc
                              WHERE sc.id = oe.subject_id AND sc.status = :cancelledControl)
            GROUP BY oe.event_type, lower(trim(oe.reason)), %1$s_user_id
            ORDER BY count(*) DESC, 2
            LIMIT :maxReasons
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PerformanceMetricsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ControlMetricsRow> findControlMetrics(MetricsQuery query) {
        return jdbc.query(CONTROL_SQL, params(query), (rs, i) -> new ControlMetricsRow(
                rs.getObject("user_id", UUID.class), rs.getString("username"), rs.getObject("day", LocalDate.class),
                rs.getInt("exits_submitted"), rs.getInt("entries_submitted"), rs.getInt("edits"),
                rs.getInt("resubmissions"), rs.getInt("rejection_rounds"), rs.getInt("approvals"),
                rs.getInt("first_pass"), nullableDouble(rs, "avg_response"), rs.getInt("corrections"),
                rs.getInt("faulty_controls"), rs.getInt("with_errors")));
    }

    public List<DispenserMetricsRow> findDispenserMetrics(MetricsQuery query) {
        return jdbc.query(DISPENSER_SQL, params(query), (rs, i) -> new DispenserMetricsRow(
                rs.getObject("user_id", UUID.class), rs.getString("username"), rs.getObject("day", LocalDate.class),
                rs.getInt("loads"), rs.getInt("unloads"), rs.getInt("serials"),
                rs.getInt("excluded_unregistered"), rs.getInt("excluded_unavailable"),
                nullableDouble(rs, "avg_load"), nullableDouble(rs, "median_load"), rs.getInt("slow_loads"),
                nullableDouble(rs, "avg_gap"), nullableDouble(rs, "median_gap"), rs.getInt("pauses"),
                rs.getInt("cancellations"), rs.getInt("with_errors")));
    }

    /** Only control dates that already ended: today's trucks may still be on the road. */
    public List<CoverageRow> findCoverage(MetricsQuery query) {
        var range       = query.range();
        var coverageEnd = range.to().isBefore(range.lastEndedDay()) ? range.to() : range.lastEndedDay();
        if (coverageEnd.isBefore(range.from())) return List.of();
        return jdbc.query(COVERAGE_SQL, params(query).addValue("coverageEnd", coverageEnd), (rs, i) -> new CoverageRow(
                rs.getObject("branch_id", UUID.class), rs.getString("branch_name"),
                rs.getObject("control_date", LocalDate.class), rs.getInt("expected"), rs.getInt("missing"),
                strings(rs.getArray("missing_routes"))));
    }

    public List<ReasonRow> findTopReasons(MetricsQuery query, ReasonOwner owner) {
        var column = owner == ReasonOwner.ACTOR ? "oe.actor" : "oe.responsible";
        return jdbc.query(REASONS_SQL.formatted(column), params(query).addValue("maxReasons", MAX_REASONS),
                (rs, i) -> new ReasonRow(OperationEventType.valueOf(rs.getString("event_type")), rs.getString("reason"),
                        rs.getObject("user_id", UUID.class), rs.getString("username"), rs.getInt("total")));
    }

    private MapSqlParameterSource params(MetricsQuery query) {
        return new MapSqlParameterSource()
                .addValue("from", query.range().from())
                .addValue("to", query.range().to())
                .addValue("fromStart", Timestamp.valueOf(query.range().from().atStartOfDay()))
                .addValue("toEnd", Timestamp.valueOf(query.range().to().plusDays(1).atStartOfDay()))
                .addValue("byDay", query.byDay())
                .addValue("allBranches", query.scope().allBranches())
                .addValue("branchId", query.scope().branchId())
                .addValue("userId", query.responsibleUserId())
                .addValue("controlType", query.controlType() != null ? query.controlType().name() : null)
                .addValue("movementType", query.movementType() != null ? query.movementType().name() : null)
                .addValue("stockControl", OperationEventSubject.STOCK_CONTROL.name())
                .addValue("submitted", OperationEventType.CONTROL_SUBMITTED.name())
                .addValue("edited", OperationEventType.CONTROL_EDITED.name())
                .addValue("approved", OperationEventType.CONTROL_APPROVED.name())
                .addValue("rejected", OperationEventType.CONTROL_REJECTED.name())
                .addValue("corrected", OperationEventType.CONTROL_CORRECTED.name())
                .addValue("integrationError", OperationEventType.INTEGRATION_ERROR.name())
                .addValue("exit", ControlType.EXIT.name())
                .addValue("entry", ControlType.ENTRY.name())
                .addValue("cancelledControl", ControlStatus.CANCELLED.name())
                .addValue("cancelledMovement", DispenserMovementStatus.CANCELLED.name())
                .addValue("load", DispenserMovementType.LOAD.name())
                .addValue("unload", DispenserMovementType.UNLOAD.name())
                .addValue("slowLoadSeconds", SLOW_LOAD_SECONDS)
                .addValue("pauseSeconds", PAUSE_SECONDS);
    }

    /** avg() returns numeric and percentile_cont double precision: both read as a nullable Double. */
    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        var value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}
