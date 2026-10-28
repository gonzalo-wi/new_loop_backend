package com.loop.new_loop_api.performance.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.performance.entity.OperationEvent;
import com.loop.new_loop_api.performance.entity.OperationEventItem;
import com.loop.new_loop_api.performance.entity.OperationEventSubject;
import com.loop.new_loop_api.performance.entity.OperationEventType;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rebuilds the operational events of one stock control from its audit log entries (spec 001, history
 * import). The entries must be that control's, oldest first: rejection rounds and driver response times
 * depend on what happened before. Audit timestamps are server-local (Argentina), as live events are.
 */
public class AuditEventTranslator {

    public static final String UNREADABLE_JSON = "UNREADABLE_JSON";
    public static final String MISSING_FIELDS  = "MISSING_FIELDS";
    public static final String UNKNOWN_ACTION  = "UNKNOWN_ACTION";

    public static final String CREATE  = "CREATE_STOCK_CONTROL";
    public static final String UPDATE  = "UPDATE_STOCK_CONTROL";
    public static final String APPROVE = "APPROVE_STOCK_CONTROL";
    public static final String REJECT  = "REJECT_STOCK_CONTROL";
    public static final String CORRECT = "CORRECT_STOCK_CONTROL";

    public static final List<String> ACTIONS = List.of(CREATE, UPDATE, APPROVE, REJECT, CORRECT);

    private static final ObjectMapper JSON = new ObjectMapper();

    public record AuditEntry(UUID auditId, String action, UUID actorUserId, String actorUsername,
                             String oldValue, String newValue, String reason, LocalDateTime createdAt) {}

    public record Skip(UUID auditId, String reason) {}

    public record Result(List<OperationEvent> events, List<Skip> skipped) {}

    private final Map<UUID, String> usernames;

    /** {@code usernames}: user id to username, to name the responsible controller (the JSON only has the id). */
    public AuditEventTranslator(Map<UUID, String> usernames) {
        this.usernames = usernames;
    }

    public Result translate(List<AuditEntry> entries) {
        var state   = new ControlHistory();
        var events  = new ArrayList<OperationEvent>();
        var skipped = new ArrayList<Skip>();
        for (var entry : entries) {
            try {
                events.add(translate(entry, state));
            } catch (SkippedEntry ex) {
                skipped.add(new Skip(entry.auditId(), ex.getMessage()));
            }
        }
        return new Result(events, skipped);
    }

    private OperationEvent translate(AuditEntry entry, ControlHistory state) {
        var control  = snapshot(entry.newValue() != null ? entry.newValue() : entry.oldValue());
        var previous = entry.oldValue() != null ? snapshot(entry.oldValue()) : null;
        return switch (entry.action()) {
            case CREATE -> {
                if (control.type() == ControlType.ENTRY) state.sentToDriverAt = entry.createdAt();
                yield withItems(base(entry, control, OperationEventType.CONTROL_SUBMITTED), control);
            }
            case UPDATE -> {
                var resubmission = previous != null && previous.status() == ControlStatus.REJECTED_BY_DRIVER;
                if (resubmission) state.sentToDriverAt = entry.createdAt();
                yield withItems(base(entry, control, OperationEventType.CONTROL_EDITED).resubmission(resubmission), control);
            }
            case REJECT -> base(entry, control, OperationEventType.CONTROL_REJECTED)
                    .rejectionRound(++state.rounds)
                    .responseSeconds(state.secondsSinceSent(entry.createdAt()))
                    .reason(entry.reason() != null ? entry.reason() : control.rejectionReason())
                    .build();
            case APPROVE -> base(entry, control, OperationEventType.CONTROL_APPROVED)
                    .rejectionRound(state.rounds)
                    .responseSeconds(state.secondsSinceSent(entry.createdAt()))
                    .build();
            case CORRECT -> withItems(base(entry, control, OperationEventType.CONTROL_CORRECTED).reason(entry.reason()), control);
            default -> throw new SkippedEntry(UNKNOWN_ACTION);
        };
    }

    private OperationEvent.OperationEventBuilder base(AuditEntry entry, ControlSnapshot control, OperationEventType type) {
        return OperationEvent.builder()
                .eventType(type)
                .occurredAt(entry.createdAt())
                .eventDate(entry.createdAt().toLocalDate())
                .subjectType(OperationEventSubject.STOCK_CONTROL)
                .subjectId(control.id())
                .controlType(control.type())
                .branchId(control.branchId())
                .routeId(control.routeId())
                .routeCode(control.routeCode())
                .actorUserId(entry.actorUserId())
                .actorUsername(entry.actorUsername())
                .responsibleUserId(control.controllerId())
                .responsibleUsername(control.controllerId() != null ? usernames.get(control.controllerId()) : null)
                .imported(true)
                .sourceAuditId(entry.auditId());
    }

    private static OperationEvent withItems(OperationEvent.OperationEventBuilder builder, ControlSnapshot control) {
        var items = control.items().stream().filter(ItemSnapshot::hasQuantities).toList();
        var event = builder.itemsCount(items.size())
                .unitsTotal(items.stream().mapToInt(ItemSnapshot::total).sum())
                .build();
        items.forEach(item -> event.getItems().add(OperationEventItem.builder()
                .event(event)
                .productId(item.productId())
                .productName(item.productName())
                .totalQuantity(item.total())
                .fullQuantity(item.full())
                .exchangeQuantity(item.exchange())
                .build()));
        return event;
    }

    private static ControlSnapshot snapshot(String json) {
        if (json == null) throw new SkippedEntry(MISSING_FIELDS);
        JsonNode node;
        try {
            node = JSON.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new SkippedEntry(UNREADABLE_JSON);
        }
        var id       = uuid(node, "id");
        var type     = text(node, "type");
        var branchId = uuid(node, "branchId");
        var routeId  = uuid(node, "routeId");
        if (id == null || type == null || branchId == null || routeId == null) throw new SkippedEntry(MISSING_FIELDS);
        var status = text(node, "status");
        var items  = new ArrayList<ItemSnapshot>();
        node.path("items").forEach(item -> {
            var productId = uuid(item, "productId");
            if (productId == null) return;
            var name = text(item, "productName");
            items.add(new ItemSnapshot(productId, name != null ? name : "",
                    item.path("totalQuantity").asInt(0), item.path("fullQuantity").asInt(0),
                    item.path("exchangeQuantity").asInt(0)));
        });
        try {
            return new ControlSnapshot(id, ControlType.valueOf(type), status != null ? ControlStatus.valueOf(status) : null,
                    branchId, routeId, text(node, "routeCode"), uuid(node, "controllerId"),
                    text(node, "driverRejectionReason"), items);
        } catch (IllegalArgumentException ex) {
            throw new SkippedEntry(MISSING_FIELDS);
        }
    }

    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static UUID uuid(JsonNode node, String field) {
        var value = text(node, field);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new SkippedEntry(MISSING_FIELDS);
        }
    }

    private record ControlSnapshot(UUID id, ControlType type, ControlStatus status, UUID branchId, UUID routeId,
                                   String routeCode, UUID controllerId, String rejectionReason, List<ItemSnapshot> items) {}

    private record ItemSnapshot(UUID productId, String productName, int total, int full, int exchange) {
        boolean hasQuantities() {
            return total != 0 || full != 0 || exchange != 0;
        }
    }

    /** What the control had gone through before the entry being translated. */
    private static final class ControlHistory {
        private int           rounds;
        private LocalDateTime sentToDriverAt;

        Long secondsSinceSent(LocalDateTime at) {
            return sentToDriverAt == null ? null : Duration.between(sentToDriverAt, at).toSeconds();
        }
    }

    /** Control flow only: the entry is reported as skipped with this reason, never thrown to callers. */
    private static final class SkippedEntry extends RuntimeException {
        SkippedEntry(String reason) {
            super(reason, null, false, false);
        }
    }
}
