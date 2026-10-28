package com.loop.new_loop_api.stockcontrols.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.branches.repository.BranchRepository;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.integrations.aguas.metrics.AguasControlMetrics;
import com.loop.new_loop_api.products.repository.ProductRepository;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import com.loop.new_loop_api.stockcontrols.dto.CorrectStockControlRequest;
import com.loop.new_loop_api.stockcontrols.dto.CreateStockControlItemRequest;
import com.loop.new_loop_api.stockcontrols.dto.RejectStockControlRequest;
import com.loop.new_loop_api.stockcontrols.dto.StockControlResponse;
import com.loop.new_loop_api.stockcontrols.dto.UpdateStockControlRequest;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.entity.StockControlItem;
import com.loop.new_loop_api.stockcontrols.event.StockControlCorrectedEvent;
import com.loop.new_loop_api.stockcontrols.event.StockControlReadyForAguasEvent;
import com.loop.new_loop_api.stockcontrols.exception.ControlEditNotAllowedException;
import com.loop.new_loop_api.stockcontrols.exception.CorrectionNotAllowedException;
import com.loop.new_loop_api.stockcontrols.exception.InvalidControlStatusException;
import com.loop.new_loop_api.stockcontrols.exception.StockControlNotCorrectableException;
import com.loop.new_loop_api.stockcontrols.exception.StockControlNotFoundException;
import com.loop.new_loop_api.stockcontrols.exception.StockControlNotModifiableException;
import com.loop.new_loop_api.stockcontrols.mapper.StockControlMapper;
import com.loop.new_loop_api.stockcontrols.pdf.RemitoPdfGenerator;
import com.loop.new_loop_api.stockcontrols.repository.StockControlRepository;
import com.loop.new_loop_api.stockcontrols.repository.StockControlSpecification;
import com.loop.new_loop_api.users.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockControlServiceImplTest {

    @Mock private StockControlRepository stockControlRepository;
    @Mock private StockControlMapper     stockControlMapper;
    @Mock private BranchRepository       branchRepository;
    @Mock private RouteRepository        routeRepository;
    @Mock private ProductRepository      productRepository;
    @Mock private AuditService           auditService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RemitoPdfGenerator     remitoPdfGenerator;
    @Mock private CurrentUserProvider    currentUserProvider;
    @Mock private AguasControlMetrics    aguasControlMetrics;

    private StockControlServiceImpl stockControlService;

    private UUID controlId;
    private StockControl control;

    @BeforeEach
    void setUp() {
        stockControlService = new StockControlServiceImpl(
                stockControlRepository, stockControlMapper, branchRepository, routeRepository,
                productRepository, auditService, eventPublisher, remitoPdfGenerator, currentUserProvider,
                aguasControlMetrics);

        controlId = UUID.randomUUID();
        control = entryControl(ControlStatus.SENT_TO_AGUAS);
    }

    private StockControl entryControl(ControlStatus status) {
        return StockControl.builder()
                .id(controlId)
                .type(ControlType.ENTRY)
                .status(status)
                .branch(Branch.builder().id(UUID.randomUUID()).name("Branch 1").build())
                .route(Route.builder().id(UUID.randomUUID()).code("R1").build())
                .controlDate(java.time.LocalDate.now())
                .truckOrdered(true)
                .observations("old obs")
                .build();
    }

    private AuthenticatedUser supervisor() {
        return new AuthenticatedUser(UUID.randomUUID(), "supervisor1", "Super Visor", Role.SUPERVISOR, UUID.randomUUID());
    }

    private AuthenticatedUser nonSupervisor(Role role) {
        return new AuthenticatedUser(UUID.randomUUID(), "user1", "Some User", role, UUID.randomUUID());
    }

    private CorrectStockControlRequest correctionRequest() {
        var request = new CorrectStockControlRequest();
        request.setReason("Cantidad mal cargada por el chofer");
        request.setObservations("new obs");
        request.setTruckOrdered(false);

        var item = new CreateStockControlItemRequest();
        item.setProductId(UUID.randomUUID());
        item.setTotalQuantity(10);
        item.setFullQuantity(8);
        item.setExchangeQuantity(2);
        request.setItems(List.of(item));
        return request;
    }

    // 1. Rejects when the current user is neither SUPERVISOR nor ADMIN
    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"SUPERVISOR", "ADMIN"}, mode = EnumSource.Mode.EXCLUDE)
    void should_throwCorrectionNotAllowedException_when_userIsNotSupervisorOrAdmin(Role role) {
        when(currentUserProvider.current()).thenReturn(Optional.of(nonSupervisor(role)));

        assertThatThrownBy(() -> stockControlService.correctControl(controlId, correctionRequest()))
                .isInstanceOf(CorrectionNotAllowedException.class);

        verify(stockControlRepository, never()).save(any());
        verify(auditService, never()).register(any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(aguasControlMetrics, never()).recordCorrected();
    }

    @Test
    void should_throwCorrectionNotAllowedException_when_noAuthenticatedUser() {
        when(currentUserProvider.current()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockControlService.correctControl(controlId, correctionRequest()))
                .isInstanceOf(CorrectionNotAllowedException.class);

        verify(stockControlRepository, never()).save(any());
    }

    // 2. Rejects when the control is not ENTRY
    @Test
    void should_throwStockControlNotCorrectableException_when_controlIsNotEntry() {
        var exitControl = entryControl(ControlStatus.SENT_TO_AGUAS);
        exitControl.setType(ControlType.EXIT);
        when(stockControlRepository.findById(controlId)).thenReturn(Optional.of(exitControl));
        when(currentUserProvider.current()).thenReturn(Optional.of(supervisor()));

        assertThatThrownBy(() -> stockControlService.correctControl(controlId, correctionRequest()))
                .isInstanceOf(StockControlNotCorrectableException.class);

        verify(stockControlRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // 3. Rejects when the status is not correctable (e.g. CONTROLLED)
    @ParameterizedTest
    @EnumSource(value = ControlStatus.class, names = {"SENT_TO_AGUAS", "AGUAS_ERROR"}, mode = EnumSource.Mode.EXCLUDE)
    void should_throwStockControlNotCorrectableException_when_statusIsNotCorrectable(ControlStatus status) {
        var nonCorrectableControl = entryControl(status);
        when(stockControlRepository.findById(controlId)).thenReturn(Optional.of(nonCorrectableControl));
        when(currentUserProvider.current()).thenReturn(Optional.of(supervisor()));

        assertThatThrownBy(() -> stockControlService.correctControl(controlId, correctionRequest()))
                .isInstanceOf(StockControlNotCorrectableException.class);

        verify(stockControlRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void should_throwStockControlNotFoundException_when_controlDoesNotExist() {
        when(currentUserProvider.current()).thenReturn(Optional.of(supervisor()));
        when(stockControlRepository.findById(controlId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockControlService.correctControl(controlId, correctionRequest()))
                .isInstanceOf(StockControlNotFoundException.class);
    }

    // 4. Happy path from SENT_TO_AGUAS
    @Test
    void should_correctControlAndPublishEvent_when_supervisorCorrectsControlSentToAguas() {
        assertHappyPath(entryControl(ControlStatus.SENT_TO_AGUAS));
    }

    // 4b. Happy path from AGUAS_ERROR
    @Test
    void should_correctControlAndPublishEvent_when_supervisorCorrectsControlInAguasError() {
        assertHappyPath(entryControl(ControlStatus.AGUAS_ERROR));
    }

    // 4c. Admins can correct too
    @Test
    void should_correctControlAndPublishEvent_when_adminCorrectsControlSentToAguas() {
        assertHappyPath(entryControl(ControlStatus.SENT_TO_AGUAS), nonSupervisor(Role.ADMIN));
    }

    private void assertHappyPath(StockControl existingControl) {
        assertHappyPath(existingControl, supervisor());
    }

    private void assertHappyPath(StockControl existingControl, AuthenticatedUser user) {
        var request = correctionRequest();
        var oldValueResponse = StockControlResponse.builder().id(controlId).observations("old obs").build();
        var newValueResponse = StockControlResponse.builder().id(controlId).observations("new obs").build();
        var newItem = StockControlItem.builder().id(UUID.randomUUID()).build();

        when(stockControlRepository.findById(controlId)).thenReturn(Optional.of(existingControl));
        when(currentUserProvider.current()).thenReturn(Optional.of(user));
        when(stockControlMapper.toResponse(existingControl)).thenReturn(oldValueResponse, newValueResponse);
        when(productRepository.findById(any())).thenReturn(Optional.of(
                com.loop.new_loop_api.products.entity.Product.builder()
                        .id(request.getItems().get(0).getProductId())
                        .code("P1")
                        .active(true)
                        .build()));
        when(productRepository.findByActiveTrue()).thenReturn(List.of());
        when(stockControlMapper.toItem(eq(request.getItems().get(0)), any(), eq(existingControl))).thenReturn(newItem);
        when(stockControlRepository.saveAndFlush(existingControl)).thenReturn(existingControl);
        when(stockControlRepository.save(existingControl)).thenReturn(existingControl);

        var result = stockControlService.correctControl(controlId, request);

        assertThat(result).isEqualTo(newValueResponse);
        assertThat(existingControl.getObservations()).isEqualTo("new obs");
        assertThat(existingControl.getTruckOrdered()).isFalse();
        assertThat(existingControl.getItems()).containsExactly(newItem);

        verify(auditService).register(
                eq("CORRECT_STOCK_CONTROL"), eq("StockControl"), eq(controlId),
                eq(oldValueResponse), eq(newValueResponse), eq(request.getReason()));

        var eventCaptor = ArgumentCaptor.forClass(StockControlCorrectedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().controlId()).isEqualTo(controlId);

        verify(stockControlRepository, times(1)).save(existingControl);
        verify(aguasControlMetrics, times(1)).recordCorrected();
    }

    // 5. Driver rejection of an ENTRY waiting for the driver's check
    private RejectStockControlRequest rejectRequest() {
        var request = new RejectStockControlRequest();
        request.setReason("Faltan 2 bidones de 20L");
        return request;
    }

    @Test
    void should_markControlRejectedAndAudit_when_driverRejectsPendingControl() {
        var pending  = entryControl(ControlStatus.PENDING_DRIVER_APPROVAL);
        var response = StockControlResponse.builder().id(controlId).status(ControlStatus.REJECTED_BY_DRIVER).build();
        when(stockControlRepository.findByIdForUpdate(controlId)).thenReturn(Optional.of(pending));
        when(stockControlRepository.save(pending)).thenReturn(pending);
        when(stockControlMapper.toResponse(pending)).thenReturn(response);

        var result = stockControlService.rejectControl(controlId, rejectRequest());

        assertThat(result).isEqualTo(response);
        assertThat(pending.getStatus()).isEqualTo(ControlStatus.REJECTED_BY_DRIVER);
        assertThat(pending.getDriverRejectionReason()).isEqualTo("Faltan 2 bidones de 20L");
        assertThat(pending.getApprovedAt()).isNull();
        verify(auditService).register(
                eq("REJECT_STOCK_CONTROL"), eq("StockControl"), eq(controlId),
                eq(null), eq(response), eq("Faltan 2 bidones de 20L"));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @ParameterizedTest
    @EnumSource(value = ControlStatus.class, names = {"PENDING_DRIVER_APPROVAL"}, mode = EnumSource.Mode.EXCLUDE)
    void should_throwInvalidControlStatusException_when_rejectingControlNotPendingDriverApproval(ControlStatus status) {
        var notPending = entryControl(status);
        when(stockControlRepository.findByIdForUpdate(controlId)).thenReturn(Optional.of(notPending));

        assertThatThrownBy(() -> stockControlService.rejectControl(controlId, rejectRequest()))
                .isInstanceOf(InvalidControlStatusException.class);

        assertThat(notPending.getStatus()).isEqualTo(status);
        verify(stockControlRepository, never()).save(any());
        verify(auditService, never()).register(any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void should_storeTrimmedReason_when_driverRejectsWithSurroundingSpaces() {
        var pending = entryControl(ControlStatus.PENDING_DRIVER_APPROVAL);
        when(stockControlRepository.findByIdForUpdate(controlId)).thenReturn(Optional.of(pending));
        when(stockControlRepository.save(pending)).thenReturn(pending);
        var request = new RejectStockControlRequest();
        request.setReason("  Faltan bidones  ");

        stockControlService.rejectControl(controlId, request);

        assertThat(pending.getDriverRejectionReason()).isEqualTo("Faltan bidones");
    }

    @Test
    void should_throwStockControlNotFoundException_when_rejectingMissingControl() {
        when(stockControlRepository.findByIdForUpdate(controlId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockControlService.rejectControl(controlId, rejectRequest()))
                .isInstanceOf(StockControlNotFoundException.class);
    }

    // 6. Controller edits before the driver's check
    private StockControl stubUpdatableControl(ControlStatus status) {
        return stubUpdatableControl(status, Role.CONTROLADOR);
    }

    private StockControl stubUpdatableControl(ControlStatus status, Role editorRole) {
        when(currentUserProvider.current()).thenReturn(Optional.of(nonSupervisor(editorRole)));
        var existing = entryControl(status);
        when(stockControlRepository.findByIdForUpdate(controlId)).thenReturn(Optional.of(existing));
        when(stockControlRepository.saveAndFlush(existing)).thenReturn(existing);
        when(stockControlMapper.toResponse(existing)).thenReturn(StockControlResponse.builder().id(controlId).build());
        return existing;
    }

    @Test
    void should_sendControlBackToDriver_when_controllerUpdatesRejectedControl() {
        var rejected = stubUpdatableControl(ControlStatus.REJECTED_BY_DRIVER);
        rejected.setDriverRejectionReason("Faltan 2 bidones de 20L");

        stockControlService.updateControl(controlId, new UpdateStockControlRequest());

        assertThat(rejected.getStatus()).isEqualTo(ControlStatus.PENDING_DRIVER_APPROVAL);
        assertThat(rejected.getDriverRejectionReason()).isNull();
        assertThat(rejected.getConfirmedAt()).isNotNull();
        verify(auditService).register(eq("UPDATE_STOCK_CONTROL"), eq("StockControl"), eq(controlId), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void should_keepControlPendingWithoutSendingToAguas_when_controllerUpdatesPendingControl() {
        var pending = stubUpdatableControl(ControlStatus.PENDING_DRIVER_APPROVAL);

        stockControlService.updateControl(controlId, new UpdateStockControlRequest());

        assertThat(pending.getStatus()).isEqualTo(ControlStatus.PENDING_DRIVER_APPROVAL);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void should_keepStatusControlled_when_controllerUpdatesControlledControl() {
        var controlled = stubUpdatableControl(ControlStatus.CONTROLLED);

        stockControlService.updateControl(controlId, new UpdateStockControlRequest());

        assertThat(controlled.getStatus()).isEqualTo(ControlStatus.CONTROLLED);
        assertThat(controlled.getConfirmedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ControlStatus.class,
            names = {"CONTROLLED", "PENDING_DRIVER_APPROVAL", "REJECTED_BY_DRIVER"}, mode = EnumSource.Mode.EXCLUDE)
    void should_throwStockControlNotModifiableException_when_updatingControlPastDriverCheck(ControlStatus status) {
        when(currentUserProvider.current()).thenReturn(Optional.of(nonSupervisor(Role.CONTROLADOR)));
        when(stockControlRepository.findByIdForUpdate(controlId)).thenReturn(Optional.of(entryControl(status)));

        assertThatThrownBy(() -> stockControlService.updateControl(controlId, new UpdateStockControlRequest()))
                .isInstanceOf(StockControlNotModifiableException.class);

        verify(stockControlRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"SUPERVISOR", "ADMIN"})
    void should_sendControlBackToDriver_when_supervisorOrAdminUpdatesRejectedControl(Role role) {
        var rejected = stubUpdatableControl(ControlStatus.REJECTED_BY_DRIVER, role);

        stockControlService.updateControl(controlId, new UpdateStockControlRequest());

        assertThat(rejected.getStatus()).isEqualTo(ControlStatus.PENDING_DRIVER_APPROVAL);
    }

    // The driver checks the control; letting them edit it would let them approve their own numbers.
    @ParameterizedTest
    @EnumSource(value = ControlStatus.class,
            names = {"CONTROLLED", "PENDING_DRIVER_APPROVAL", "REJECTED_BY_DRIVER"})
    void should_throwControlEditNotAllowedException_when_driverUpdatesControl(ControlStatus status) {
        when(currentUserProvider.current()).thenReturn(Optional.of(nonSupervisor(Role.REPARTIDOR)));

        assertThatThrownBy(() -> stockControlService.updateControl(controlId, new UpdateStockControlRequest()))
                .isInstanceOf(ControlEditNotAllowedException.class);

        verify(stockControlRepository, never()).findByIdForUpdate(any());
        verify(stockControlRepository, never()).saveAndFlush(any());
        verify(auditService, never()).register(any(), any(), any(), any(), any());
    }

    @Test
    void should_throwControlEditNotAllowedException_when_noAuthenticatedUserUpdatesControl() {
        when(currentUserProvider.current()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockControlService.updateControl(controlId, new UpdateStockControlRequest()))
                .isInstanceOf(ControlEditNotAllowedException.class);

        verify(stockControlRepository, never()).saveAndFlush(any());
    }

    // 7. Driver approval, locked against a concurrent edit by the controller
    @Test
    void should_acceptControlAndSendToAguas_when_driverApprovesPendingControl() {
        var pending = entryControl(ControlStatus.PENDING_DRIVER_APPROVAL);
        when(stockControlRepository.findByIdForUpdate(controlId)).thenReturn(Optional.of(pending));
        when(stockControlRepository.save(pending)).thenReturn(pending);

        stockControlService.approveControl(controlId);

        assertThat(pending.getStatus()).isEqualTo(ControlStatus.ACCEPTED_BY_DRIVER);
        assertThat(pending.getApprovedAt()).isNotNull();
        var eventCaptor = ArgumentCaptor.forClass(StockControlReadyForAguasEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().controlId()).isEqualTo(controlId);
    }

    @ParameterizedTest
    @EnumSource(value = ControlStatus.class, names = {"PENDING_DRIVER_APPROVAL"}, mode = EnumSource.Mode.EXCLUDE)
    void should_throwInvalidControlStatusException_when_approvingControlNotPendingDriverApproval(ControlStatus status) {
        when(stockControlRepository.findByIdForUpdate(controlId)).thenReturn(Optional.of(entryControl(status)));

        assertThatThrownBy(() -> stockControlService.approveControl(controlId))
                .isInstanceOf(InvalidControlStatusException.class);

        verify(stockControlRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // 8. History: a controller only lists the controls they created
    @Test
    void should_listOnlyOwnControls_when_controllerAsksForAnotherControllersControls() {
        var controller = nonSupervisor(Role.CONTROLADOR);
        assertListedControllerId(controller, UUID.randomUUID(), controller.id());
    }

    @Test
    void should_listOnlyOwnControls_when_controllerAsksWithoutControllerFilter() {
        var controller = nonSupervisor(Role.CONTROLADOR);
        assertListedControllerId(controller, null, controller.id());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"SUPERVISOR", "ADMIN", "REPARTIDOR"})
    void should_keepRequestedControllerFilter_when_userIsNotController(Role role) {
        assertListedControllerId(nonSupervisor(role), null, null);
    }

    private void assertListedControllerId(AuthenticatedUser user, UUID requested, UUID expected) {
        when(currentUserProvider.current()).thenReturn(Optional.of(user));
        when(stockControlRepository.findAll(org.mockito.ArgumentMatchers.<Specification<StockControl>>any(), any(Pageable.class))).thenReturn(Page.empty());

        try (var specs = mockStatic(StockControlSpecification.class)) {
            stockControlService.getAllControls(null, null, null, requested, null, null, null, null, null, Pageable.unpaged());

            specs.verify(() -> StockControlSpecification.withFilters(
                    isNull(), isNull(), isNull(), expected == null ? isNull() : eq(expected),
                    any(), isNull(), isNull(), isNull(), isNull()));
        }
    }
}
