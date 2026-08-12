package com.loop.new_loop_api.stockcontrols.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.branches.repository.BranchRepository;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.products.repository.ProductRepository;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import com.loop.new_loop_api.stockcontrols.dto.CorrectStockControlRequest;
import com.loop.new_loop_api.stockcontrols.dto.CreateStockControlItemRequest;
import com.loop.new_loop_api.stockcontrols.dto.StockControlResponse;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.entity.StockControlItem;
import com.loop.new_loop_api.stockcontrols.event.StockControlCorrectedEvent;
import com.loop.new_loop_api.stockcontrols.exception.CorrectionNotAllowedException;
import com.loop.new_loop_api.stockcontrols.exception.StockControlNotCorrectableException;
import com.loop.new_loop_api.stockcontrols.exception.StockControlNotFoundException;
import com.loop.new_loop_api.stockcontrols.mapper.StockControlMapper;
import com.loop.new_loop_api.stockcontrols.pdf.RemitoPdfGenerator;
import com.loop.new_loop_api.stockcontrols.repository.StockControlRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private StockControlServiceImpl stockControlService;

    private UUID controlId;
    private StockControl control;

    @BeforeEach
    void setUp() {
        stockControlService = new StockControlServiceImpl(
                stockControlRepository, stockControlMapper, branchRepository, routeRepository,
                productRepository, auditService, eventPublisher, remitoPdfGenerator, currentUserProvider);

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

    // 1. Rejects when the current user is not SUPERVISOR
    @Test
    void should_throwCorrectionNotAllowedException_when_userIsNotSupervisor() {
        when(currentUserProvider.current()).thenReturn(Optional.of(nonSupervisor(Role.CONTROLADOR)));

        assertThatThrownBy(() -> stockControlService.correctControl(controlId, correctionRequest()))
                .isInstanceOf(CorrectionNotAllowedException.class);

        verify(stockControlRepository, never()).save(any());
        verify(auditService, never()).register(any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
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

    private void assertHappyPath(StockControl existingControl) {
        var request = correctionRequest();
        var oldValueResponse = StockControlResponse.builder().id(controlId).observations("old obs").build();
        var newValueResponse = StockControlResponse.builder().id(controlId).observations("new obs").build();
        var newItem = StockControlItem.builder().id(UUID.randomUUID()).build();

        when(stockControlRepository.findById(controlId)).thenReturn(Optional.of(existingControl));
        when(currentUserProvider.current()).thenReturn(Optional.of(supervisor()));
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
    }
}
