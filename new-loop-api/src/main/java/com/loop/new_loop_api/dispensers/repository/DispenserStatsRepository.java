package com.loop.new_loop_api.dispensers.repository;

import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse.DailyPoint;
import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse.HeatmapCell;
import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse.QualityIssue;
import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse.TechnicianStats;
import com.loop.new_loop_api.dispensers.dto.DispenserStatsResponse.UserStats;
import com.loop.new_loop_api.dispensers.dto.QualityIssueKind;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregate queries over dispenser movements. Everything is computed in Postgres;
 * every aggregate except {@link #countCancelled} works on non-cancelled movements only.
 */
@Repository
public class DispenserStatsRepository {

    public static final String NO_USER_LABEL       = "Sin usuario";
    public static final String NO_TECHNICIAN_LABEL = "Sin técnico";
    public static final int    MAX_GAP_MINUTES     = 60;
    public static final int    MAX_QUALITY_ISSUES  = 200;

    private static final String ODOO_ERROR = "ERROR";

    public record VolumeTotals(int installMovements, int withdrawalMovements,
                               int installedDispensers, int withdrawnDispensers, int maxSerialsPerMovement) {}

    public record QualityTotals(int activeMovements, int sentToAguas, int skippedUnregistered, int aguasErrors,
                                int odooErrors, int withExcludedSerials, int withoutUser) {}

    /** Non-cancelled movements in the range, with per-movement serial and excluded-serial counts (no join fan-out). */
    private static final String MOVEMENTS_CTE = """
            WITH m AS (
                SELECT dm.id, dm.type, dm.route_code, dm.technician, dm.status, dm.odoo_status,
                       dm.movement_date, dm.created_at,
                       COALESCE(NULLIF(trim(dm.registered_by_username), ''), :noUser) AS user_name,
                       (NULLIF(trim(dm.registered_by_username), '') IS NULL)          AS without_user,
                       (SELECT count(*) FROM dispenser_movement_serials s
                         WHERE s.movement_id = dm.id)                                 AS serial_count,
                       (SELECT count(*) FROM dispenser_movement_excluded_serials e
                         WHERE e.movement_id = dm.id)                                 AS excluded_count
                FROM dispenser_movements dm
                WHERE dm.movement_date BETWEEN :from AND :to
                  AND dm.status <> :cancelled
            )
            """;

    private static final String VOLUME_SQL = MOVEMENTS_CTE + """
            SELECT count(*) FILTER (WHERE type = :load)                               AS install_movements,
                   count(*) FILTER (WHERE type = :unload)                             AS withdrawal_movements,
                   COALESCE(sum(serial_count) FILTER (WHERE type = :load), 0)         AS installed,
                   COALESCE(sum(serial_count) FILTER (WHERE type = :unload), 0)       AS withdrawn,
                   COALESCE(max(serial_count), 0)                                     AS max_serials
            FROM m
            """;

    private static final String DAILY_SQL = MOVEMENTS_CTE + """
            SELECT CAST(g AS date)                                                    AS day,
                   COALESCE(sum(m.serial_count) FILTER (WHERE m.type = :load), 0)     AS installed,
                   COALESCE(sum(m.serial_count) FILTER (WHERE m.type = :unload), 0)   AS withdrawn,
                   count(m.id) FILTER (WHERE m.type = :load)                          AS install_movements,
                   count(m.id) FILTER (WHERE m.type = :unload)                        AS withdrawal_movements
            FROM generate_series(CAST(:from AS timestamp), CAST(:to AS timestamp), interval '1 day') g
            LEFT JOIN m ON m.movement_date = CAST(g AS date)
            GROUP BY g
            ORDER BY g
            """;

    private static final String HEATMAP_SQL = MOVEMENTS_CTE + """
            SELECT CAST(EXTRACT(ISODOW FROM created_at) AS int) - 1 AS weekday,
                   CAST(EXTRACT(HOUR FROM created_at) AS int)       AS hour,
                   count(*)                                         AS movements
            FROM m
            WHERE created_at IS NOT NULL
            GROUP BY 1, 2
            ORDER BY 1, 2
            """;

    private static final String USERS_SQL = MOVEMENTS_CTE + """
            , gaps AS (
                SELECT user_name,
                       CAST(EXTRACT(EPOCH FROM created_at
                            - LAG(created_at) OVER (PARTITION BY user_name ORDER BY created_at)) / 60.0
                            AS double precision) AS gap_minutes
                FROM m
                WHERE created_at IS NOT NULL
            ), medians AS (
                SELECT user_name,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY gap_minutes) AS median_gap
                FROM gaps
                WHERE gap_minutes > 0 AND gap_minutes <= :maxGapMinutes
                GROUP BY user_name
            )
            SELECT m.user_name,
                   count(*)                                                                     AS movements,
                   count(*) FILTER (WHERE m.type = :load)                                       AS install_movements,
                   count(*) FILTER (WHERE m.type = :unload)                                     AS withdrawal_movements,
                   COALESCE(sum(m.serial_count), 0)                                             AS dispensers,
                   avg(CASE WHEN m.status = :skipped THEN 1.0 ELSE 0.0 END)                     AS skipped_rate,
                   avg(CASE WHEN m.status = :aguasError OR m.odoo_status = :odooError
                            THEN 1.0 ELSE 0.0 END)                                              AS error_rate,
                   avg(CASE WHEN m.excluded_count > 0 THEN 1.0 ELSE 0.0 END)                    AS excluded_rate,
                   max(md.median_gap)                                                           AS median_gap
            FROM m
            LEFT JOIN medians md ON md.user_name = m.user_name
            GROUP BY m.user_name
            ORDER BY count(*) DESC, m.user_name
            """;

    private static final String TECHNICIANS_SQL = MOVEMENTS_CTE + """
            SELECT COALESCE(NULLIF(trim(technician), ''), :noTechnician)               AS technician,
                   count(DISTINCT route_code)                                          AS routes,
                   COALESCE(sum(serial_count) FILTER (WHERE type = :load), 0)          AS installed,
                   COALESCE(sum(serial_count) FILTER (WHERE type = :unload), 0)        AS withdrawn,
                   count(*)                                                            AS movements
            FROM m
            GROUP BY 1
            ORDER BY COALESCE(sum(serial_count), 0) DESC, 1
            """;

    private static final String QUALITY_SQL = MOVEMENTS_CTE + """
            SELECT count(*)                                              AS active_movements,
                   count(*) FILTER (WHERE status = :sentToAguas)         AS sent_to_aguas,
                   count(*) FILTER (WHERE status = :skipped)             AS skipped,
                   count(*) FILTER (WHERE status = :aguasError)          AS aguas_errors,
                   count(*) FILTER (WHERE odoo_status = :odooError)      AS odoo_errors,
                   count(*) FILTER (WHERE excluded_count > 0)            AS with_excluded,
                   count(*) FILTER (WHERE without_user)                  AS without_user
            FROM m
            """;

    private static final String CANCELLED_SQL = """
            SELECT count(*) FROM dispenser_movements
            WHERE movement_date BETWEEN :from AND :to AND status = :cancelled
            """;

    private static final String ISSUES_SQL = MOVEMENTS_CTE + """
            SELECT id, created_at, type, route_code, technician, user_name, excluded_count,
                   (status = :skipped)                       AS is_skipped,
                   (status = :aguasError)                    AS is_aguas_error,
                   COALESCE(odoo_status = :odooError, false) AS is_odoo_error
            FROM m
            WHERE status IN (:skipped, :aguasError) OR odoo_status = :odooError OR excluded_count > 0
            ORDER BY created_at DESC
            LIMIT :issuesLimit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DispenserStatsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public VolumeTotals findVolume(LocalDate from, LocalDate to) {
        return jdbc.queryForObject(VOLUME_SQL, params(from, to), (rs, i) -> new VolumeTotals(
                rs.getInt("install_movements"), rs.getInt("withdrawal_movements"),
                rs.getInt("installed"), rs.getInt("withdrawn"), rs.getInt("max_serials")));
    }

    public List<DailyPoint> findDaily(LocalDate from, LocalDate to) {
        return jdbc.query(DAILY_SQL, params(from, to), (rs, i) -> new DailyPoint(
                rs.getObject("day", LocalDate.class), rs.getInt("installed"), rs.getInt("withdrawn"),
                rs.getInt("install_movements"), rs.getInt("withdrawal_movements")));
    }

    public List<HeatmapCell> findHeatmap(LocalDate from, LocalDate to) {
        return jdbc.query(HEATMAP_SQL, params(from, to), (rs, i) -> new HeatmapCell(
                rs.getInt("weekday"), rs.getInt("hour"), rs.getInt("movements")));
    }

    public List<UserStats> findUsers(LocalDate from, LocalDate to) {
        return jdbc.query(USERS_SQL, params(from, to), (rs, i) -> new UserStats(
                rs.getString("user_name"), rs.getInt("movements"), rs.getInt("install_movements"),
                rs.getInt("withdrawal_movements"), rs.getInt("dispensers"), rs.getDouble("skipped_rate"),
                rs.getDouble("error_rate"), rs.getDouble("excluded_rate"),
                rs.getObject("median_gap", Double.class)));
    }

    public List<TechnicianStats> findTechnicians(LocalDate from, LocalDate to) {
        return jdbc.query(TECHNICIANS_SQL, params(from, to), (rs, i) -> new TechnicianStats(
                rs.getString("technician"), rs.getInt("routes"), rs.getInt("installed"),
                rs.getInt("withdrawn"), rs.getInt("movements")));
    }

    public QualityTotals findQualityTotals(LocalDate from, LocalDate to) {
        return jdbc.queryForObject(QUALITY_SQL, params(from, to), (rs, i) -> new QualityTotals(
                rs.getInt("active_movements"), rs.getInt("sent_to_aguas"), rs.getInt("skipped"),
                rs.getInt("aguas_errors"), rs.getInt("odoo_errors"), rs.getInt("with_excluded"),
                rs.getInt("without_user")));
    }

    public int countCancelled(LocalDate from, LocalDate to) {
        Integer count = jdbc.queryForObject(CANCELLED_SQL, params(from, to), Integer.class);
        return count == null ? 0 : count;
    }

    public List<QualityIssue> findQualityIssues(LocalDate from, LocalDate to) {
        return jdbc.query(ISSUES_SQL, params(from, to), (rs, i) -> new QualityIssue(
                rs.getObject("id", UUID.class), rs.getObject("created_at", LocalDateTime.class),
                rs.getString("type"), rs.getString("route_code"), rs.getString("technician"),
                rs.getString("user_name"), issueKinds(rs), rs.getInt("excluded_count")));
    }

    private List<QualityIssueKind> issueKinds(ResultSet rs) throws SQLException {
        List<QualityIssueKind> kinds = new ArrayList<>();
        if (rs.getBoolean("is_skipped"))      kinds.add(QualityIssueKind.SKIPPED);
        if (rs.getBoolean("is_aguas_error"))  kinds.add(QualityIssueKind.AGUAS_ERROR);
        if (rs.getBoolean("is_odoo_error"))   kinds.add(QualityIssueKind.ODOO_ERROR);
        if (rs.getInt("excluded_count") > 0)  kinds.add(QualityIssueKind.EXCLUDED);
        return kinds;
    }

    private MapSqlParameterSource params(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("load", DispenserMovementType.LOAD.name())
                .addValue("unload", DispenserMovementType.UNLOAD.name())
                .addValue("cancelled", DispenserMovementStatus.CANCELLED.name())
                .addValue("skipped", DispenserMovementStatus.SKIPPED_UNREGISTERED.name())
                .addValue("aguasError", DispenserMovementStatus.AGUAS_ERROR.name())
                .addValue("sentToAguas", DispenserMovementStatus.SENT_TO_AGUAS.name())
                .addValue("odooError", ODOO_ERROR)
                .addValue("noUser", NO_USER_LABEL)
                .addValue("noTechnician", NO_TECHNICIAN_LABEL)
                .addValue("maxGapMinutes", MAX_GAP_MINUTES)
                .addValue("issuesLimit", MAX_QUALITY_ISSUES);
    }
}
