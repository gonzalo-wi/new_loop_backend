package com.loop.new_loop_api.performance.repository;

import com.loop.new_loop_api.performance.entity.OperationEvent;
import com.loop.new_loop_api.performance.entity.OperationEventItem;
import com.loop.new_loop_api.performance.entity.OperationEventSubject;
import com.loop.new_loop_api.performance.entity.OperationEventType;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationEventRepositoryTest extends PostgresIntegrationTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 28, 9, 30);

    @Autowired private OperationEventRepository repository;
    @Autowired private JdbcTemplate             jdbc;

    @Test
    void shouldSaveEventWithItemsAndCountBySubject() {
        var subjectId = UUID.randomUUID();
        repository.saveAndFlush(eventWithItem(subjectId, OperationEventType.CONTROL_REJECTED));
        repository.saveAndFlush(eventWithItem(subjectId, OperationEventType.CONTROL_REJECTED));

        assertThat(repository.countByEventTypeAndSubjectId(OperationEventType.CONTROL_REJECTED, subjectId)).isEqualTo(2);
        assertThat(repository.countByEventTypeAndSubjectId(OperationEventType.CONTROL_APPROVED, subjectId)).isZero();
        Integer items = jdbc.queryForObject("""
                SELECT count(*) FROM operation_event_items i JOIN operation_events e ON e.id = i.event_id
                WHERE e.subject_id = ?""", Integer.class, subjectId);
        assertThat(items).isEqualTo(2);
    }

    @Test
    void shouldRejectUpdatesAndDeletesOfEvents() {
        var saved = repository.saveAndFlush(eventWithItem(UUID.randomUUID(), OperationEventType.CONTROL_SUBMITTED));

        assertThatThrownBy(() -> jdbc.update("UPDATE operation_events SET reason = 'x' WHERE id = ?", saved.getId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM operation_event_items WHERE event_id = ?", saved.getId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM operation_events WHERE id = ?", saved.getId()))
                .hasMessageContaining("append-only");
    }

    @Test
    void shouldRejectTwoEventsFromTheSameAuditEntry() {
        var auditId = UUID.randomUUID();
        repository.saveAndFlush(importedEvent(auditId));

        assertThatThrownBy(() -> repository.saveAndFlush(importedEvent(auditId)))
                .hasMessageContaining("uq_operation_events_source_audit");
    }

    private static OperationEvent eventWithItem(UUID subjectId, OperationEventType type) {
        var event = baseEvent(subjectId, type).build();
        event.getItems().add(OperationEventItem.builder()
                .event(event)
                .productId(UUID.randomUUID())
                .productName("Bidón 20L")
                .totalQuantity(10)
                .fullQuantity(8)
                .exchangeQuantity(2)
                .build());
        return event;
    }

    private static OperationEvent importedEvent(UUID auditId) {
        return baseEvent(UUID.randomUUID(), OperationEventType.CONTROL_SUBMITTED)
                .imported(true)
                .sourceAuditId(auditId)
                .build();
    }

    private static OperationEvent.OperationEventBuilder baseEvent(UUID subjectId, OperationEventType type) {
        return OperationEvent.builder()
                .eventType(type)
                .occurredAt(OCCURRED_AT)
                .eventDate(OCCURRED_AT.toLocalDate())
                .subjectType(OperationEventSubject.STOCK_CONTROL)
                .subjectId(subjectId)
                .controlType(ControlType.ENTRY)
                .routeCode("Rto 12");
    }
}
