package com.loop.new_loop_api.performance.repository;

import com.loop.new_loop_api.performance.domain.MetricsRange;
import com.loop.new_loop_api.performance.domain.MetricsScope;
import com.loop.new_loop_api.performance.domain.RouteStatisticsCalculator.Arrival;
import com.loop.new_loop_api.performance.domain.RouteStatisticsCalculator.Exit;
import com.loop.new_loop_api.performance.domain.RouteStatisticsCalculator.Reason;
import com.loop.new_loop_api.performance.domain.RouteStatisticsCalculator.Rejections;
import com.loop.new_loop_api.performance.entity.OperationEventType;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only rows for the route statistics (spec 006); the arithmetic is in RouteStatisticsCalculator.
 * created_at is written in Argentina time (the JVM zone), so its date and hour are read as stored.
 */
@Repository
public class RouteStatisticsRepository {

    // The stay exit is made the arrival day for the next delivery day: Sunday is skipped and holidays may
    // push it further. Bounding control_date keeps the lookup an index range on (route, type, date)
    // instead of every later exit of the route, which would grow with the history.
    public static final int MAX_EXIT_DAYS_AHEAD = 7;
    public static final int MAX_REASONS_PER_ROUTE = 3;

    private static final String ARRIVALS_SQL = """
            SELECT sc.branch_id, b.name AS branch_name, sc.route_id, r.code AS route_code,
                   sc.control_date, sc.created_at AS arrived_at, x.created_at AS exit_at
            FROM stock_controls sc
            JOIN routes r   ON r.id = sc.route_id
            JOIN branches b ON b.id = sc.branch_id
            LEFT JOIN LATERAL (
                SELECT ex.created_at
                FROM stock_controls ex
                WHERE ex.route_id = sc.route_id
                  AND ex.type = :exit
                  AND ex.status <> :cancelled
                  AND ex.control_date BETWEEN sc.control_date + 1 AND sc.control_date + :maxExitDays
                  AND ex.created_at >= sc.control_date AND ex.created_at < sc.control_date + 1
                ORDER BY ex.created_at
                LIMIT 1
            ) x ON true
            WHERE sc.type = :entry
              AND sc.status <> :cancelled
              AND sc.control_date BETWEEN :from AND :to
              AND (:allBranches OR sc.branch_id = CAST(:branchId AS uuid))
            """;

    private static final String EXITS_SQL = """
            SELECT sc.branch_id, b.name AS branch_name, sc.route_id, r.code AS route_code,
                   sc.control_date, CAST(sc.created_at AS date) AS created_date
            FROM stock_controls sc
            JOIN routes r   ON r.id = sc.route_id
            JOIN branches b ON b.id = sc.branch_id
            WHERE sc.type = :exit
              AND sc.status <> :cancelled
              AND sc.control_date BETWEEN :from AND :to
              AND (:allBranches OR sc.branch_id = CAST(:branchId AS uuid))
            """;

    // Each rejection with the route code it had then (FR-11); events imported from the audit history may
    // lack the snapshot, so the current code fills in, and null is left for the panel to label. Computed
    // once here: a bound parameter repeated in SELECT and GROUP BY would be two different $n to Postgres.
    private static final String REJECTED_EVENTS = """
            WITH rejected AS (
                SELECT oe.subject_id, oe.route_id, COALESCE(oe.route_code, r.code) AS route_code,
                       b.name AS branch_name, oe.occurred_at, oe.reason
                FROM operation_events oe
                LEFT JOIN routes r   ON r.id = oe.route_id
                LEFT JOIN branches b ON b.id = oe.branch_id
                WHERE oe.event_type = :rejected
                  AND oe.event_date BETWEEN :from AND :to
                  AND (:allBranches OR oe.branch_id = CAST(:branchId AS uuid))
                  AND NOT EXISTS (SELECT 1 FROM stock_controls sc
                                  WHERE sc.id = oe.subject_id AND sc.status = :cancelled)
            )
            """;

    private static final String REJECTIONS_SQL = REJECTED_EVENTS + """
            SELECT route_id, route_code,
                   (array_agg(branch_name ORDER BY occurred_at DESC))[1] AS branch_name,
                   count(*)                   AS rejections,
                   count(DISTINCT subject_id) AS rejected_controls
            FROM rejected
            GROUP BY route_id, route_code
            """;

    private static final String REASONS_SQL = REJECTED_EVENTS + """
            , grouped AS (
                SELECT route_id, route_code, lower(trim(reason)) AS reason_key,
                       (array_agg(trim(reason) ORDER BY occurred_at DESC))[1] AS reason,
                       count(*) AS total
                FROM rejected
                WHERE reason IS NOT NULL AND trim(reason) <> ''
                GROUP BY route_id, route_code, lower(trim(reason))
            )
            SELECT route_id, route_code, reason, total
            FROM (
                SELECT g.*, row_number() OVER (PARTITION BY route_id, route_code
                                               ORDER BY total DESC, reason_key) AS position
                FROM grouped g
            ) ranked
            WHERE position <= :maxReasons
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public RouteStatisticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Arrival> findArrivals(MetricsScope scope, MetricsRange range) {
        return jdbc.query(ARRIVALS_SQL, params(scope, range), (rs, i) -> {
            var exitAt = rs.getTimestamp("exit_at");
            return new Arrival(rs.getObject("branch_id", UUID.class), rs.getString("branch_name"),
                    rs.getObject("route_id", UUID.class), rs.getString("route_code"),
                    rs.getObject("control_date", LocalDate.class), rs.getTimestamp("arrived_at").toLocalDateTime(),
                    exitAt == null ? null : exitAt.toLocalDateTime());
        });
    }

    public List<Exit> findExits(MetricsScope scope, MetricsRange range) {
        return jdbc.query(EXITS_SQL, params(scope, range), (rs, i) -> new Exit(
                rs.getObject("branch_id", UUID.class), rs.getString("branch_name"),
                rs.getObject("route_id", UUID.class), rs.getString("route_code"),
                rs.getObject("control_date", LocalDate.class), rs.getObject("created_date", LocalDate.class)));
    }

    public List<Rejections> findRejections(MetricsScope scope, MetricsRange range) {
        return jdbc.query(REJECTIONS_SQL, params(scope, range), (rs, i) -> new Rejections(
                rs.getObject("route_id", UUID.class), rs.getString("route_code"), rs.getString("branch_name"),
                rs.getInt("rejections"), rs.getInt("rejected_controls")));
    }

    public List<Reason> findTopReasons(MetricsScope scope, MetricsRange range) {
        return jdbc.query(REASONS_SQL, params(scope, range).addValue("maxReasons", MAX_REASONS_PER_ROUTE),
                (rs, i) -> new Reason(rs.getObject("route_id", UUID.class), rs.getString("route_code"),
                        rs.getString("reason"), rs.getInt("total")));
    }

    private static MapSqlParameterSource params(MetricsScope scope, MetricsRange range) {
        return new MapSqlParameterSource()
                .addValue("from", range.from())
                .addValue("to", range.to())
                .addValue("allBranches", scope.allBranches())
                .addValue("branchId", scope.branchId())
                .addValue("entry", ControlType.ENTRY.name())
                .addValue("exit", ControlType.EXIT.name())
                .addValue("cancelled", ControlStatus.CANCELLED.name())
                .addValue("rejected", OperationEventType.CONTROL_REJECTED.name())
                .addValue("maxExitDays", MAX_EXIT_DAYS_AHEAD);
    }
}
