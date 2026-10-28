package com.loop.new_loop_api.performance.domain;

import com.loop.new_loop_api.performance.domain.AuditEventTranslator.AuditEntry;
import com.loop.new_loop_api.performance.entity.OperationEvent;
import com.loop.new_loop_api.performance.entity.OperationEventSubject;
import com.loop.new_loop_api.performance.entity.OperationEventType;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventTranslatorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 10, 21, 30);

    private final UUID controlId    = UUID.randomUUID();
    private final UUID controllerId = UUID.randomUUID();
    private final UUID driverId     = UUID.randomUUID();
    private final UUID branchId     = UUID.randomUUID();
    private final UUID routeId      = UUID.randomUUID();

    private final AuditEventTranslator translator =
            new AuditEventTranslator(Map.of(controllerId, "ctrl1"));

    @Test
    void shouldRebuildRoundsResubmissionsAndResponseTimes() {
        var entries = List.of(
                entry("CREATE_STOCK_CONTROL", controllerId, "ctrl1", null, control("PENDING_DRIVER_APPROVAL", 10), null, T0),
                entry("REJECT_STOCK_CONTROL", driverId, "driver1", null, control("REJECTED_BY_DRIVER", 10), "Faltan bidones", T0.plusMinutes(10)),
                entry("UPDATE_STOCK_CONTROL", controllerId, "ctrl1", control("REJECTED_BY_DRIVER", 10), control("PENDING_DRIVER_APPROVAL", 8), null, T0.plusMinutes(15)),
                entry("REJECT_STOCK_CONTROL", driverId, "driver1", null, control("REJECTED_BY_DRIVER", 8), "Sigue mal", T0.plusMinutes(20)),
                entry("UPDATE_STOCK_CONTROL", controllerId, "ctrl1", control("REJECTED_BY_DRIVER", 8), control("PENDING_DRIVER_APPROVAL", 9), null, T0.plusMinutes(25)),
                entry("APPROVE_STOCK_CONTROL", driverId, "driver1", null, control("ACCEPTED_BY_DRIVER", 9), null, T0.plusMinutes(27)));

        var result = translator.translate(entries);

        assertThat(result.skipped()).isEmpty();
        var events = result.events();
        assertThat(events).extracting(OperationEvent::getEventType).containsExactly(
                OperationEventType.CONTROL_SUBMITTED, OperationEventType.CONTROL_REJECTED, OperationEventType.CONTROL_EDITED,
                OperationEventType.CONTROL_REJECTED, OperationEventType.CONTROL_EDITED, OperationEventType.CONTROL_APPROVED);
        assertThat(events.get(1).getRejectionRound()).isEqualTo(1);
        assertThat(events.get(1).getResponseSeconds()).isEqualTo(600L);
        assertThat(events.get(1).getReason()).isEqualTo("Faltan bidones");
        assertThat(events.get(2).isResubmission()).isTrue();
        assertThat(events.get(3).getRejectionRound()).isEqualTo(2);
        assertThat(events.get(3).getResponseSeconds()).isEqualTo(300L);
        assertThat(events.get(5).getRejectionRound()).isEqualTo(2);
        assertThat(events.get(5).getResponseSeconds()).isEqualTo(120L);
    }

    @Test
    void shouldFillCommonFieldsAndMarkEventsAsImported() {
        var auditId = UUID.randomUUID();
        var result  = translator.translate(List.of(new AuditEntry(auditId, "CREATE_STOCK_CONTROL", controllerId, "ctrl1",
                null, control("PENDING_DRIVER_APPROVAL", 10), null, T0)));

        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.isImported()).isTrue();
            assertThat(event.getSourceAuditId()).isEqualTo(auditId);
            assertThat(event.getSubjectType()).isEqualTo(OperationEventSubject.STOCK_CONTROL);
            assertThat(event.getSubjectId()).isEqualTo(controlId);
            assertThat(event.getControlType()).isEqualTo(ControlType.ENTRY);
            assertThat(event.getBranchId()).isEqualTo(branchId);
            assertThat(event.getRouteId()).isEqualTo(routeId);
            assertThat(event.getRouteCode()).isEqualTo("Rto 12");
            assertThat(event.getActorUsername()).isEqualTo("ctrl1");
            assertThat(event.getResponsibleUserId()).isEqualTo(controllerId);
            assertThat(event.getResponsibleUsername()).isEqualTo("ctrl1");
            // 21:30 is already the next day in UTC: the event date stays on the local day.
            assertThat(event.getEventDate()).isEqualTo(LocalDate.of(2026, 3, 10));
            assertThat(event.getItemsCount()).isEqualTo(1);
            assertThat(event.getUnitsTotal()).isEqualTo(10);
            assertThat(event.getItems()).singleElement().satisfies(item -> assertThat(item.getEvent()).isSameAs(event));
        });
    }

    @Test
    void shouldNotMarkAnEditWhilePendingAsResubmission() {
        var result = translator.translate(List.of(
                entry("CREATE_STOCK_CONTROL", controllerId, "ctrl1", null, control("PENDING_DRIVER_APPROVAL", 10), null, T0),
                entry("UPDATE_STOCK_CONTROL", controllerId, "ctrl1", control("PENDING_DRIVER_APPROVAL", 10), control("PENDING_DRIVER_APPROVAL", 11), null, T0.plusMinutes(5)),
                entry("APPROVE_STOCK_CONTROL", driverId, "driver1", null, control("ACCEPTED_BY_DRIVER", 11), null, T0.plusMinutes(9))));

        assertThat(result.events().get(1).isResubmission()).isFalse();
        // The pending edit does not send the control to the driver again: the wait runs from the creation.
        assertThat(result.events().get(2).getResponseSeconds()).isEqualTo(540L);
        assertThat(result.events().get(2).getRejectionRound()).isZero();
    }

    @Test
    void shouldTranslateACorrectionWithItsReasonAndCorrectedItems() {
        var result = translator.translate(List.of(entry("CORRECT_STOCK_CONTROL", UUID.randomUUID(), "super1",
                control("SENT_TO_AGUAS", 10), control("SENT_TO_AGUAS", 7), "Cantidad mal cargada", T0)));

        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo(OperationEventType.CONTROL_CORRECTED);
            assertThat(event.getReason()).isEqualTo("Cantidad mal cargada");
            assertThat(event.getUnitsTotal()).isEqualTo(7);
            assertThat(event.getResponsibleUserId()).isEqualTo(controllerId);
        });
    }

    @Test
    void shouldLeaveResponseTimeEmpty_when_theCreationIsNoLongerInTheAuditLog() {
        var result = translator.translate(List.of(
                entry("APPROVE_STOCK_CONTROL", driverId, "driver1", null, control("ACCEPTED_BY_DRIVER", 9), null, T0)));

        assertThat(result.events().get(0).getResponseSeconds()).isNull();
    }

    @Test
    void shouldSkipUnreadableOrIncompleteEntriesAndKeepTheRest() {
        var broken = new AuditEntry(UUID.randomUUID(), "CREATE_STOCK_CONTROL", controllerId, "ctrl1", null, "{not json", null, T0);
        var noRoute = entry("UPDATE_STOCK_CONTROL", controllerId, "ctrl1", null,
                "{\"id\":\"" + controlId + "\",\"type\":\"ENTRY\",\"branchId\":\"" + branchId + "\"}", null, T0.plusMinutes(1));
        var good = entry("APPROVE_STOCK_CONTROL", driverId, "driver1", null, control("ACCEPTED_BY_DRIVER", 9), null, T0.plusMinutes(2));

        var result = translator.translate(List.of(broken, noRoute, good));

        assertThat(result.events()).extracting(OperationEvent::getEventType).containsExactly(OperationEventType.CONTROL_APPROVED);
        assertThat(result.skipped()).extracting(AuditEventTranslator.Skip::reason)
                .containsExactly(AuditEventTranslator.UNREADABLE_JSON, AuditEventTranslator.MISSING_FIELDS);
    }

    private AuditEntry entry(String action, UUID actorId, String actorUsername, String oldValue, String newValue,
                             String reason, LocalDateTime createdAt) {
        return new AuditEntry(UUID.randomUUID(), action, actorId, actorUsername, oldValue, newValue, reason, createdAt);
    }

    private String control(String status, int total) {
        var items = new ArrayList<String>();
        items.add("{\"productId\":\"" + UUID.randomUUID() + "\",\"productName\":\"Bidón 20L\",\"totalQuantity\":" + total
                + ",\"fullQuantity\":" + total + ",\"exchangeQuantity\":0}");
        items.add("{\"productId\":\"" + UUID.randomUUID() + "\",\"productName\":\"Dispenser\",\"totalQuantity\":0,"
                + "\"fullQuantity\":0,\"exchangeQuantity\":0}");
        return "{\"id\":\"" + controlId + "\",\"type\":\"ENTRY\",\"status\":\"" + status + "\",\"branchId\":\"" + branchId
                + "\",\"routeId\":\"" + routeId + "\",\"routeCode\":\"Rto 12\",\"controllerId\":\"" + controllerId
                + "\",\"items\":[" + String.join(",", items) + "]}";
    }
}
