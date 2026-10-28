package com.loop.new_loop_api.performance.service;

import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.performance.domain.ControlFigures;
import com.loop.new_loop_api.performance.entity.OperationEvent;
import com.loop.new_loop_api.performance.entity.OperationEventItem;
import com.loop.new_loop_api.performance.entity.OperationEventSubject;
import com.loop.new_loop_api.performance.entity.OperationEventType;
import com.loop.new_loop_api.performance.repository.OperationEventRepository;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.users.entity.User;
import com.loop.new_loop_api.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Writes operational events (spec 001). Callers invoke it inside their own transaction, next to the
 * audit entry, so an event exists if and only if the operation it describes was persisted.
 * The responsible user of a stock-control event is always the control's controller, whoever acts.
 */
@Service
@RequiredArgsConstructor
public class OperationEventRecorder {

    private final OperationEventRepository repository;
    private final UserRepository           userRepository;
    private final CurrentUserProvider      currentUserProvider;
    private final Clock                    clock;

    public void controlSubmitted(StockControl control) {
        save(withItems(actedOn(control, OperationEventType.CONTROL_SUBMITTED), control));
    }

    public void controlEdited(StockControl control, boolean resubmission) {
        save(withItems(actedOn(control, OperationEventType.CONTROL_EDITED).resubmission(resubmission), control));
    }

    /** The response time runs from the control's last send to the driver (confirmedAt is reset on each resubmission). */
    public void controlApproved(StockControl control) {
        save(actedOn(control, OperationEventType.CONTROL_APPROVED)
                .rejectionRound((int) previousRejections(control))
                .responseSeconds(secondsSinceSentToDriver(control))
                .build());
    }

    public void controlRejected(StockControl control) {
        save(actedOn(control, OperationEventType.CONTROL_REJECTED)
                .rejectionRound((int) previousRejections(control) + 1)
                .responseSeconds(secondsSinceSentToDriver(control))
                .reason(control.getDriverRejectionReason())
                .build());
    }

    public void controlCorrected(StockControl control, String reason) {
        save(withItems(actedOn(control, OperationEventType.CONTROL_CORRECTED).reason(reason), control));
    }

    /** Integration sends run in the background, so there is no actor: only who the control belongs to. */
    public void integrationError(IntegrationName integration, StockControl control) {
        save(aboutControl(control, OperationEventType.INTEGRATION_ERROR, null)
                .integration(integration)
                .build());
    }

    public void integrationError(IntegrationName integration, DispenserMovement movement) {
        save(base(OperationEventType.INTEGRATION_ERROR, OperationEventSubject.DISPENSER_MOVEMENT, movement.getId())
                .branchId(movement.getBranchId())
                .routeCode(movement.getRouteCode())
                .responsibleUserId(movement.getRegisteredBy())
                .responsibleUsername(movement.getRegisteredByUsername())
                .integration(integration)
                .build());
    }

    private OperationEvent.OperationEventBuilder actedOn(StockControl control, OperationEventType type) {
        var actor = currentUserProvider.current().orElse(null);
        return aboutControl(control, type, actor)
                .actorUserId(actor != null ? actor.id() : null)
                .actorUsername(actor != null ? actor.username() : null);
    }

    private OperationEvent.OperationEventBuilder aboutControl(StockControl control, OperationEventType type,
                                                              AuthenticatedUser actor) {
        var route = control.getRoute();
        return base(type, OperationEventSubject.STOCK_CONTROL, control.getId())
                .controlType(control.getType())
                .branchId(control.getBranch() != null ? control.getBranch().getId() : null)
                .routeId(route != null ? route.getId() : null)
                .routeCode(route != null ? route.getCode() : null)
                .responsibleUserId(control.getControllerId())
                .responsibleUsername(usernameOf(control.getControllerId(), actor));
    }

    private OperationEvent.OperationEventBuilder base(OperationEventType type, OperationEventSubject subjectType,
                                                      UUID subjectId) {
        var now = LocalDateTime.now(clock);
        return OperationEvent.builder()
                .eventType(type)
                .occurredAt(now)
                .eventDate(now.toLocalDate())
                .subjectType(subjectType)
                .subjectId(subjectId);
    }

    private OperationEvent withItems(OperationEvent.OperationEventBuilder builder, StockControl control) {
        var figures = ControlFigures.of(control);
        var event   = builder.itemsCount(figures.itemsCount()).unitsTotal(figures.unitsTotal()).build();
        figures.items().forEach(item -> event.getItems().add(OperationEventItem.builder()
                .event(event)
                .productId(item.productId())
                .productName(item.productName())
                .totalQuantity(item.totalQuantity())
                .fullQuantity(item.fullQuantity())
                .exchangeQuantity(item.exchangeQuantity())
                .build()));
        return event;
    }

    /** The controller is usually the one acting, which saves a lookup. */
    private String usernameOf(UUID userId, AuthenticatedUser actor) {
        if (userId == null) return null;
        if (actor != null && userId.equals(actor.id())) return actor.username();
        return userRepository.findById(userId).map(User::getUsername).orElse(null);
    }

    private long previousRejections(StockControl control) {
        return repository.countByEventTypeAndSubjectId(OperationEventType.CONTROL_REJECTED, control.getId());
    }

    private Long secondsSinceSentToDriver(StockControl control) {
        if (control.getConfirmedAt() == null) return null;
        return Duration.between(control.getConfirmedAt(), LocalDateTime.now(clock)).toSeconds();
    }

    private void save(OperationEvent event) {
        repository.save(event);
    }
}
