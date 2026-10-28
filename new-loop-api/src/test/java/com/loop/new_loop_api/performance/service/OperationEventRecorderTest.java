package com.loop.new_loop_api.performance.service;

import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.performance.entity.OperationEvent;
import com.loop.new_loop_api.performance.entity.OperationEventSubject;
import com.loop.new_loop_api.performance.entity.OperationEventType;
import com.loop.new_loop_api.performance.repository.OperationEventRepository;
import com.loop.new_loop_api.products.entity.Product;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.entity.StockControlItem;
import com.loop.new_loop_api.users.entity.Role;
import com.loop.new_loop_api.users.entity.User;
import com.loop.new_loop_api.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationEventRecorderTest {

    private static final ZoneId        ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    // 23:30 in Argentina is already the next day in UTC: the event date must stay on the 28th.
    private static final LocalDateTime NOW       = LocalDateTime.of(2026, 10, 28, 23, 30);

    @Mock private OperationEventRepository repository;
    @Mock private UserRepository           userRepository;
    @Mock private CurrentUserProvider      currentUserProvider;

    private OperationEventRecorder recorder;

    private final UUID controllerId = UUID.randomUUID();
    private final UUID branchId     = UUID.randomUUID();
    private final UUID routeId      = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(NOW.atZone(ARGENTINA).toInstant(), ARGENTINA);
        recorder = new OperationEventRecorder(repository, userRepository, currentUserProvider, clock);
    }

    @Test
    void shouldRecordSubmittedControlWithFiguresAndSnapshots() {
        actingAs(controllerId, "ctrl1", Role.CONTROLADOR);
        var control = entryControl(ControlStatus.PENDING_DRIVER_APPROVAL);

        recorder.controlSubmitted(control);

        var event = savedEvent();
        assertThat(event.getEventType()).isEqualTo(OperationEventType.CONTROL_SUBMITTED);
        assertThat(event.getOccurredAt()).isEqualTo(NOW);
        assertThat(event.getEventDate()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(event.getSubjectType()).isEqualTo(OperationEventSubject.STOCK_CONTROL);
        assertThat(event.getSubjectId()).isEqualTo(control.getId());
        assertThat(event.getControlType()).isEqualTo(ControlType.ENTRY);
        assertThat(event.getBranchId()).isEqualTo(branchId);
        assertThat(event.getRouteId()).isEqualTo(routeId);
        assertThat(event.getRouteCode()).isEqualTo("Rto 12");
        assertThat(event.getActorUsername()).isEqualTo("ctrl1");
        assertThat(event.getResponsibleUserId()).isEqualTo(controllerId);
        assertThat(event.getResponsibleUsername()).isEqualTo("ctrl1");
        assertThat(event.getItemsCount()).isEqualTo(1);
        assertThat(event.getUnitsTotal()).isEqualTo(10);
        assertThat(event.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getEvent()).isSameAs(event);
            assertThat(item.getProductName()).isEqualTo("Bidón 20L");
            assertThat(item.getFullQuantity()).isEqualTo(8);
            assertThat(item.getExchangeQuantity()).isEqualTo(2);
        });
        verify(userRepository, never()).findById(any());
    }

    @Test
    void shouldLookUpControllerUsernameWhenSomeoneElseActs() {
        actingAs(UUID.randomUUID(), "super1", Role.SUPERVISOR);
        when(userRepository.findById(controllerId)).thenReturn(Optional.of(User.builder().username("ctrl1").build()));

        recorder.controlEdited(entryControl(ControlStatus.PENDING_DRIVER_APPROVAL), false);

        var event = savedEvent();
        assertThat(event.getEventType()).isEqualTo(OperationEventType.CONTROL_EDITED);
        assertThat(event.getActorUsername()).isEqualTo("super1");
        assertThat(event.getResponsibleUsername()).isEqualTo("ctrl1");
        assertThat(event.isResubmission()).isFalse();
    }

    @Test
    void shouldMarkResubmissionAfterRejection() {
        actingAs(controllerId, "ctrl1", Role.CONTROLADOR);

        recorder.controlEdited(entryControl(ControlStatus.PENDING_DRIVER_APPROVAL), true);

        assertThat(savedEvent().isResubmission()).isTrue();
    }

    @Test
    void shouldRecordRejectionWithRoundResponseTimeAndReason() {
        var driverId = UUID.randomUUID();
        actingAs(driverId, "driver1", Role.REPARTIDOR);
        when(userRepository.findById(controllerId)).thenReturn(Optional.of(User.builder().username("ctrl1").build()));
        var control = entryControl(ControlStatus.REJECTED_BY_DRIVER);
        control.setConfirmedAt(NOW.minusMinutes(12));
        control.setDriverRejectionReason("Faltan 2 bidones");
        when(repository.countByEventTypeAndSubjectId(OperationEventType.CONTROL_REJECTED, control.getId())).thenReturn(1L);

        recorder.controlRejected(control);

        var event = savedEvent();
        assertThat(event.getEventType()).isEqualTo(OperationEventType.CONTROL_REJECTED);
        assertThat(event.getActorUserId()).isEqualTo(driverId);
        assertThat(event.getResponsibleUserId()).isEqualTo(controllerId);
        assertThat(event.getRejectionRound()).isEqualTo(2);
        assertThat(event.getResponseSeconds()).isEqualTo(720L);
        assertThat(event.getReason()).isEqualTo("Faltan 2 bidones");
        assertThat(event.getItems()).isEmpty();
    }

    @Test
    void shouldRecordApprovalWithPreviousRoundsAndResponseTime() {
        actingAs(UUID.randomUUID(), "driver1", Role.REPARTIDOR);
        when(userRepository.findById(controllerId)).thenReturn(Optional.of(User.builder().username("ctrl1").build()));
        var control = entryControl(ControlStatus.ACCEPTED_BY_DRIVER);
        control.setConfirmedAt(NOW.minusSeconds(90));
        when(repository.countByEventTypeAndSubjectId(OperationEventType.CONTROL_REJECTED, control.getId())).thenReturn(0L);

        recorder.controlApproved(control);

        var event = savedEvent();
        assertThat(event.getEventType()).isEqualTo(OperationEventType.CONTROL_APPROVED);
        assertThat(event.getRejectionRound()).isZero();
        assertThat(event.getResponseSeconds()).isEqualTo(90L);
    }

    @Test
    void shouldLeaveResponseTimeEmptyWhenControlWasNeverSentToDriver() {
        actingAs(UUID.randomUUID(), "driver1", Role.REPARTIDOR);
        when(userRepository.findById(controllerId)).thenReturn(Optional.empty());
        var control = entryControl(ControlStatus.ACCEPTED_BY_DRIVER);

        recorder.controlApproved(control);

        var event = savedEvent();
        assertThat(event.getResponseSeconds()).isNull();
        assertThat(event.getResponsibleUserId()).isEqualTo(controllerId);
        assertThat(event.getResponsibleUsername()).isNull();
    }

    @Test
    void shouldRecordCorrectionWithReasonAndCorrectedItems() {
        actingAs(UUID.randomUUID(), "super1", Role.SUPERVISOR);
        when(userRepository.findById(controllerId)).thenReturn(Optional.of(User.builder().username("ctrl1").build()));

        recorder.controlCorrected(entryControl(ControlStatus.SENT_TO_AGUAS), "Cantidad mal cargada");

        var event = savedEvent();
        assertThat(event.getEventType()).isEqualTo(OperationEventType.CONTROL_CORRECTED);
        assertThat(event.getReason()).isEqualTo("Cantidad mal cargada");
        assertThat(event.getItems()).hasSize(1);
    }

    @Test
    void shouldRecordEventWithoutActorOrResponsibleWhenBothAreUnknown() {
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        var control = entryControl(ControlStatus.PENDING_DRIVER_APPROVAL);
        control.setControllerId(null);

        recorder.controlSubmitted(control);

        var event = savedEvent();
        assertThat(event.getActorUserId()).isNull();
        assertThat(event.getActorUsername()).isNull();
        assertThat(event.getResponsibleUserId()).isNull();
        assertThat(event.getResponsibleUsername()).isNull();
    }

    @Test
    void shouldRecordIntegrationErrorOfControlWithoutActor() {
        when(userRepository.findById(controllerId)).thenReturn(Optional.of(User.builder().username("ctrl1").build()));
        var control = entryControl(ControlStatus.AGUAS_ERROR);

        recorder.integrationError(IntegrationName.AGUAS, control);

        var event = savedEvent();
        assertThat(event.getEventType()).isEqualTo(OperationEventType.INTEGRATION_ERROR);
        assertThat(event.getIntegration()).isEqualTo(IntegrationName.AGUAS);
        assertThat(event.getSubjectId()).isEqualTo(control.getId());
        assertThat(event.getActorUserId()).isNull();
        assertThat(event.getResponsibleUsername()).isEqualTo("ctrl1");
        verify(currentUserProvider, never()).current();
    }

    @Test
    void shouldRecordIntegrationErrorOfMovementWithItsRegisteringUser() {
        var userId   = UUID.randomUUID();
        var movement = DispenserMovement.builder()
                .id(UUID.randomUUID())
                .type(DispenserMovementType.LOAD)
                .routeCode("Rto 7")
                .branchId(branchId)
                .registeredBy(userId)
                .registeredByUsername("ctrl2")
                .build();

        recorder.integrationError(IntegrationName.ODOO, movement);

        var event = savedEvent();
        assertThat(event.getSubjectType()).isEqualTo(OperationEventSubject.DISPENSER_MOVEMENT);
        assertThat(event.getSubjectId()).isEqualTo(movement.getId());
        assertThat(event.getIntegration()).isEqualTo(IntegrationName.ODOO);
        assertThat(event.getRouteCode()).isEqualTo("Rto 7");
        assertThat(event.getBranchId()).isEqualTo(branchId);
        assertThat(event.getResponsibleUserId()).isEqualTo(userId);
        assertThat(event.getResponsibleUsername()).isEqualTo("ctrl2");
        assertThat(event.getControlType()).isNull();
    }

    private void actingAs(UUID id, String username, Role role) {
        when(currentUserProvider.current()).thenReturn(Optional.of(new AuthenticatedUser(id, username, username, role, branchId)));
    }

    private OperationEvent savedEvent() {
        var captor = ArgumentCaptor.forClass(OperationEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private StockControl entryControl(ControlStatus status) {
        var control = StockControl.builder()
                .id(UUID.randomUUID())
                .type(ControlType.ENTRY)
                .status(status)
                .branch(Branch.builder().id(branchId).build())
                .route(Route.builder().id(routeId).code("Rto 12").build())
                .controllerId(controllerId)
                .build();
        control.getItems().add(StockControlItem.builder()
                .stockControl(control)
                .product(Product.builder().id(UUID.randomUUID()).name("Bidón 20L").build())
                .totalQuantity(10)
                .fullQuantity(8)
                .exchangeQuantity(2)
                .build());
        return control;
    }
}
