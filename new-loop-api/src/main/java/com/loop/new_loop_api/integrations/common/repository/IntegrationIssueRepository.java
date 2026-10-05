package com.loop.new_loop_api.integrations.common.repository;

import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Finds stock controls and dispenser movements that did not reach Aguas/Odoo, with enough context to act on them. */
@Repository
@RequiredArgsConstructor
public class IntegrationIssueRepository {

    public static final int MAX_ISSUES = 500;

    private static final String STOCK_CONTROL          = "StockControl";
    private static final String DISPENSER_MOVEMENT     = "DispenserMovement";

    /**
     * Failed attempts, plus attempts that never got an answer: only the latest one per record and operation
     * (a resend after a correction would otherwise show twice). Skips failures that a later attempt of the same
     * operation already sent, and failures of records that were cancelled afterwards.
     */
    private static final String LOG_ISSUES_SQL = """
            SELECT * FROM (
                SELECT DISTINCT ON (l.entity_id, l.integration_name, l.operation_type)
                       l.id AS log_id, l.integration_name, l.operation_type, l.entity_name, l.entity_id,
                       l.status AS log_status, l.retry_count, l.error_message, l.created_at AS since,
                       COALESCE(r.code, dm.route_code)              AS route_code,
                       COALESCE(sc.type, dm.type)                   AS entity_kind,
                       COALESCE(sc.control_date, dm.movement_date)  AS entity_date
                FROM integration_logs l
                LEFT JOIN stock_controls sc      ON l.entity_name = :stockControl      AND sc.id = l.entity_id
                LEFT JOIN routes r               ON r.id = sc.route_id
                LEFT JOIN dispenser_movements dm ON l.entity_name = :dispenserMovement AND dm.id = l.entity_id
                WHERE (l.status = :error OR (l.status IN (:pending, :retrying) AND l.created_at < :stuckBefore))
                  AND sc.status IS DISTINCT FROM :controlCancelled
                  AND dm.status IS DISTINCT FROM :movementCancelled
                  AND NOT EXISTS (
                        SELECT 1 FROM integration_logs later
                        WHERE later.entity_id = l.entity_id
                          AND later.integration_name = l.integration_name
                          AND later.operation_type = l.operation_type
                          AND later.status = :sent
                          AND later.created_at > l.created_at)
                ORDER BY l.entity_id, l.integration_name, l.operation_type, l.created_at DESC
            ) latest
            ORDER BY since
            LIMIT :limit
            """;

    /**
     * Records in a "ready to send to Aguas" state with no Aguas attempt at all. The lookback keeps out old data
     * from before the integration existed.
     */
    private static final String NEVER_SENT_SQL = """
            SELECT CAST(NULL AS uuid) AS log_id, CAST(:aguas AS varchar) AS integration_name,
                   CAST(NULL AS varchar) AS operation_type, CAST(:dispenserMovement AS varchar) AS entity_name,
                   dm.id AS entity_id, CAST(NULL AS varchar) AS log_status, 0 AS retry_count,
                   CAST(NULL AS text) AS error_message, dm.created_at AS since,
                   dm.route_code, dm.type AS entity_kind, dm.movement_date AS entity_date
            FROM dispenser_movements dm
            WHERE dm.status = :registered
              AND dm.created_at < :stuckBefore AND dm.created_at >= :lookbackFrom
              AND NOT EXISTS (SELECT 1 FROM integration_logs l WHERE l.entity_id = dm.id AND l.integration_name = :aguas)
            UNION ALL
            SELECT CAST(NULL AS uuid), CAST(:aguas AS varchar), CAST(NULL AS varchar), CAST(:stockControl AS varchar),
                   sc.id, CAST(NULL AS varchar), 0, CAST(NULL AS text), COALESCE(sc.approved_at, sc.created_at),
                   r.code, sc.type, sc.control_date
            FROM stock_controls sc
            JOIN routes r ON r.id = sc.route_id
            WHERE ((sc.type = :exit AND sc.status = :controlled) OR sc.status = :accepted)
              AND COALESCE(sc.approved_at, sc.created_at) < :stuckBefore
              AND COALESCE(sc.approved_at, sc.created_at) >= :lookbackFrom
              AND NOT EXISTS (SELECT 1 FROM integration_logs l WHERE l.entity_id = sc.id AND l.integration_name = :aguas)
            ORDER BY since
            LIMIT :limit
            """;

    private static final RowMapper<IntegrationIssueRow> ROW_MAPPER = (rs, i) -> new IntegrationIssueRow(
            rs.getObject("log_id", UUID.class),
            rs.getString("integration_name"),
            rs.getString("operation_type"),
            rs.getString("entity_name"),
            rs.getObject("entity_id", UUID.class),
            rs.getString("log_status"),
            rs.getInt("retry_count"),
            rs.getString("error_message"),
            rs.getObject("since", LocalDateTime.class),
            rs.getString("route_code"),
            rs.getString("entity_kind"),
            rs.getObject("entity_date", LocalDate.class));

    private final NamedParameterJdbcTemplate jdbc;

    public List<IntegrationIssueRow> findLogIssues(LocalDateTime stuckBefore) {
        return jdbc.query(LOG_ISSUES_SQL, commonParams()
                .addValue("stuckBefore", stuckBefore)
                .addValue("error", IntegrationStatus.ERROR.name())
                .addValue("pending", IntegrationStatus.PENDING.name())
                .addValue("retrying", IntegrationStatus.RETRYING.name())
                .addValue("sent", IntegrationStatus.SENT.name())
                .addValue("controlCancelled", ControlStatus.CANCELLED.name())
                .addValue("movementCancelled", DispenserMovementStatus.CANCELLED.name()), ROW_MAPPER);
    }

    public List<IntegrationIssueRow> findNeverSent(LocalDateTime stuckBefore, LocalDateTime lookbackFrom) {
        return jdbc.query(NEVER_SENT_SQL, commonParams()
                .addValue("stuckBefore", stuckBefore)
                .addValue("lookbackFrom", lookbackFrom)
                .addValue("aguas", IntegrationName.AGUAS.name())
                .addValue("registered", DispenserMovementStatus.REGISTERED.name())
                .addValue("exit", ControlType.EXIT.name())
                .addValue("controlled", ControlStatus.CONTROLLED.name())
                .addValue("accepted", ControlStatus.ACCEPTED_BY_DRIVER.name()), ROW_MAPPER);
    }

    private static MapSqlParameterSource commonParams() {
        return new MapSqlParameterSource()
                .addValue("stockControl", STOCK_CONTROL)
                .addValue("dispenserMovement", DISPENSER_MOVEMENT)
                .addValue("limit", MAX_ISSUES);
    }
}
