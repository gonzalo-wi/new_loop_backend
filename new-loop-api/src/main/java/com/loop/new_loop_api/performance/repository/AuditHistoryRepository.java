package com.loop.new_loop_api.performance.repository;

import com.loop.new_loop_api.performance.domain.AuditEventTranslator;
import com.loop.new_loop_api.performance.domain.AuditEventTranslator.AuditEntry;
import com.loop.new_loop_api.performance.entity.OperationEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Reads stock-control history from audit_logs and writes it as imported operation events (spec 001). */
@Repository
public class AuditHistoryRepository {

    private static final String STOCK_CONTROL_ENTITY = "StockControl";
    // Column length of the username snapshots in operation_events.
    private static final int    MAX_USERNAME_LENGTH  = 100;

    // Controls are read in pages so the whole audit history (full JSON snapshots) is never in memory at once.
    static final int CONTROLS_PER_PAGE = 200;

    // The audit entry is written a few milliseconds before the live event of the same operation. Entries of a
    // control that are this close to (or after) its first live event belong to live recording.
    static final int LIVE_OVERLAP_SECONDS = 5;

    private static final String CONTROL_PAGE_SQL = """
            SELECT DISTINCT a.entity_id
            FROM audit_logs a
            WHERE a.entity_name = :entity
              AND a.action IN (:actions)
              AND a.entity_id IS NOT NULL
              AND a.created_at < :before
              AND (CAST(:after AS uuid) IS NULL OR a.entity_id > CAST(:after AS uuid))
            ORDER BY a.entity_id
            LIMIT :pageSize
            """;

    private static final String HISTORY_SQL = """
            SELECT a.id, a.action, a.user_id, a.username, a.entity_id, a.old_value, a.new_value, a.reason, a.created_at
            FROM audit_logs a
            LEFT JOIN (SELECT subject_id, min(occurred_at) AS first_live
                       FROM operation_events
                       WHERE NOT imported AND subject_id IN (:controlIds)
                       GROUP BY subject_id) live ON live.subject_id = a.entity_id
            WHERE a.entity_name = :entity
              AND a.action IN (:actions)
              AND a.entity_id IN (:controlIds)
              AND a.created_at < :before
              AND (live.first_live IS NULL OR a.created_at < live.first_live - make_interval(secs => :overlapSeconds))
            ORDER BY a.entity_id, a.created_at, a.id
            """;

    // ON CONFLICT: re-running the import (or two runs at once) never duplicates an audit entry.
    private static final String INSERT_EVENT_SQL = """
            INSERT INTO operation_events (id, event_type, occurred_at, event_date, subject_type, subject_id,
                control_type, branch_id, route_id, route_code, actor_user_id, actor_username,
                responsible_user_id, responsible_username, items_count, units_total, rejection_round,
                response_seconds, resubmission, integration, reason, imported, source_audit_id)
            VALUES (:id, :eventType, :occurredAt, :eventDate, :subjectType, :subjectId,
                :controlType, :branchId, :routeId, :routeCode, :actorUserId, :actorUsername,
                :responsibleUserId, :responsibleUsername, :itemsCount, :unitsTotal, :rejectionRound,
                :responseSeconds, :resubmission, NULL, :reason, TRUE, :sourceAuditId)
            ON CONFLICT (source_audit_id) DO NOTHING
            """;

    // Only when the event itself was inserted (it is absent when ON CONFLICT skipped it).
    private static final String INSERT_ITEM_SQL = """
            INSERT INTO operation_event_items (id, event_id, product_id, product_name, total_quantity,
                full_quantity, exchange_quantity)
            SELECT :id, :eventId, :productId, :productName, :total, :full, :exchange
            WHERE EXISTS (SELECT 1 FROM operation_events WHERE id = :eventId)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AuditHistoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** When live recording started: the first event that was not imported. */
    public Optional<LocalDateTime> findLiveRecordingStart() {
        var start = jdbc.queryForObject("SELECT min(occurred_at) FROM operation_events WHERE NOT imported",
                new MapSqlParameterSource(), LocalDateTime.class);
        return Optional.ofNullable(start);
    }

    public Set<UUID> findImportedAuditIds() {
        return new HashSet<>(jdbc.queryForList(
                "SELECT source_audit_id FROM operation_events WHERE source_audit_id IS NOT NULL",
                new MapSqlParameterSource(), UUID.class));
    }

    public Map<UUID, String> findUsernames() {
        var usernames = new HashMap<UUID, String>();
        jdbc.query("SELECT id, username FROM users", rs -> {
            usernames.put(rs.getObject("id", UUID.class), rs.getString("username"));
        });
        return usernames;
    }

    /**
     * Calls {@code perControl} once per control with its entries before {@code before}, oldest first, reading
     * {@link #CONTROLS_PER_PAGE} controls at a time. A control that already has live events only contributes
     * the entries older than its first live event.
     */
    public void forEachControlHistory(LocalDateTime before, Consumer<List<AuditEntry>> perControl) {
        var base = new MapSqlParameterSource()
                .addValue("entity", STOCK_CONTROL_ENTITY)
                .addValue("actions", AuditEventTranslator.ACTIONS)
                .addValue("before", Timestamp.valueOf(before));
        UUID after = null;
        while (true) {
            var controlIds = jdbc.queryForList(CONTROL_PAGE_SQL,
                    copy(base).addValue("after", after).addValue("pageSize", CONTROLS_PER_PAGE), UUID.class);
            if (controlIds.isEmpty()) return;
            readHistories(copy(base).addValue("controlIds", controlIds).addValue("overlapSeconds", LIVE_OVERLAP_SECONDS),
                    perControl);
            after = controlIds.get(controlIds.size() - 1);
        }
    }

    private void readHistories(MapSqlParameterSource params, Consumer<List<AuditEntry>> perControl) {
        var current   = new ArrayList<AuditEntry>();
        var controlId = new UUID[1];
        jdbc.query(HISTORY_SQL, params, rs -> {
            var entityId = rs.getObject("entity_id", UUID.class);
            if (controlId[0] != null && !controlId[0].equals(entityId)) {
                perControl.accept(List.copyOf(current));
                current.clear();
            }
            controlId[0] = entityId;
            current.add(new AuditEntry(rs.getObject("id", UUID.class), rs.getString("action"),
                    rs.getObject("user_id", UUID.class), rs.getString("username"),
                    rs.getString("old_value"), rs.getString("new_value"), rs.getString("reason"),
                    rs.getObject("created_at", LocalDateTime.class)));
        });
        if (!current.isEmpty()) perControl.accept(List.copyOf(current));
    }

    private static MapSqlParameterSource copy(MapSqlParameterSource source) {
        return new MapSqlParameterSource(source.getValues());
    }

    /** Returns how many events were actually inserted. */
    public int insertImportedEvents(List<OperationEvent> events) {
        if (events.isEmpty()) return 0;
        var eventParams = new ArrayList<MapSqlParameterSource>();
        var itemParams  = new ArrayList<MapSqlParameterSource>();
        for (var event : events) {
            var eventId = UUID.randomUUID();
            eventParams.add(eventParams(eventId, event));
            event.getItems().forEach(item -> itemParams.add(new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("eventId", eventId)
                    .addValue("productId", item.getProductId())
                    .addValue("productName", item.getProductName())
                    .addValue("total", item.getTotalQuantity())
                    .addValue("full", item.getFullQuantity())
                    .addValue("exchange", item.getExchangeQuantity())));
        }
        var inserted = jdbc.batchUpdate(INSERT_EVENT_SQL, eventParams.toArray(MapSqlParameterSource[]::new));
        if (!itemParams.isEmpty()) jdbc.batchUpdate(INSERT_ITEM_SQL, itemParams.toArray(MapSqlParameterSource[]::new));
        var total = 0;
        for (var count : inserted) total += Math.max(count, 0);
        return total;
    }

    private static MapSqlParameterSource eventParams(UUID eventId, OperationEvent event) {
        return new MapSqlParameterSource()
                .addValue("id", eventId)
                .addValue("eventType", event.getEventType().name())
                .addValue("occurredAt", Timestamp.valueOf(event.getOccurredAt()))
                .addValue("eventDate", event.getEventDate())
                .addValue("subjectType", event.getSubjectType().name())
                .addValue("subjectId", event.getSubjectId())
                .addValue("controlType", event.getControlType() != null ? event.getControlType().name() : null)
                .addValue("branchId", event.getBranchId())
                .addValue("routeId", event.getRouteId())
                .addValue("routeCode", event.getRouteCode())
                .addValue("actorUserId", event.getActorUserId())
                .addValue("actorUsername", truncate(event.getActorUsername()))
                .addValue("responsibleUserId", event.getResponsibleUserId())
                .addValue("responsibleUsername", truncate(event.getResponsibleUsername()))
                .addValue("itemsCount", event.getItemsCount())
                .addValue("unitsTotal", event.getUnitsTotal())
                .addValue("rejectionRound", event.getRejectionRound())
                .addValue("responseSeconds", event.getResponseSeconds())
                .addValue("resubmission", event.isResubmission())
                .addValue("reason", event.getReason())
                .addValue("sourceAuditId", event.getSourceAuditId());
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_USERNAME_LENGTH ? value : value.substring(0, MAX_USERNAME_LENGTH);
    }
}
