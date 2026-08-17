package com.loop.new_loop_api.dispensers.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.dispensers.dto.CreateDispenserMovementRequest;
import com.loop.new_loop_api.dispensers.dto.DispenserMovementResponse;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.dispensers.mapper.DispenserMovementMapper;
import com.loop.new_loop_api.dispensers.metrics.DispenserMovementMetrics;
import com.loop.new_loop_api.dispensers.repository.DispenserMovementRepository;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentService;
import com.loop.new_loop_api.integrations.jmobile.service.iService.UnregisteredDispenserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispenserMovementServiceImplTest {

    @Mock private DispenserMovementRepository   dispenserMovementRepository;
    @Mock private DispenserMovementMapper       dispenserMovementMapper;
    @Mock private CurrentUserProvider           currentUserProvider;
    @Mock private AuditService                  auditService;
    @Mock private ApplicationEventPublisher     eventPublisher;
    @Mock private AguasEquipmentService         aguasEquipmentService;
    @Mock private UnregisteredDispenserService  unregisteredDispenserService;
    @Mock private DispenserMovementMetrics      dispenserMovementMetrics;

    private DispenserMovementServiceImpl dispenserMovementService;

    @BeforeEach
    void setUp() {
        dispenserMovementService = new DispenserMovementServiceImpl(
                dispenserMovementRepository, dispenserMovementMapper, currentUserProvider, auditService,
                eventPublisher, aguasEquipmentService, unregisteredDispenserService, dispenserMovementMetrics);
    }

    private CreateDispenserMovementRequest unloadRequest(List<String> serials) {
        var request = new CreateDispenserMovementRequest();
        request.setType(DispenserMovementType.UNLOAD);
        request.setRouteCode("1");
        request.setTechnician("Tech 1");
        request.setSerials(serials);
        return request;
    }

    @Test
    void should_recordUnregisteredSerialsExcludedMetric_when_someScannedSerialsAreUnregisteredInJMobile() {
        var request = unloadRequest(List.of("SN-1", "SN-2", "SN-3"));
        var date = LocalDate.now();
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.UNLOAD)
                .routeCode("1")
                .serials(new java.util.ArrayList<>(List.of("SN-1", "SN-2", "SN-3")))
                .build();

        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(unregisteredDispenserService.findUnregisteredSerials(any()))
                .thenReturn(Set.of("SN-2"));
        when(unregisteredDispenserService.normalize("SN-1")).thenReturn("SN-1");
        when(unregisteredDispenserService.normalize("SN-2")).thenReturn("SN-2");
        when(unregisteredDispenserService.normalize("SN-3")).thenReturn("SN-3");
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(request);

        assertThat(movement.getExcludedSerials()).containsExactly("SN-2");
        assertThat(movement.getStatus()).isNotEqualTo(DispenserMovementStatus.SKIPPED_UNREGISTERED);
        verify(dispenserMovementMetrics).recordUnregisteredSerialsExcluded(1);
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void should_skipMovementAndNotPublishEvent_when_everyScannedSerialIsUnregistered() {
        var request = unloadRequest(List.of("SN-1"));
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.UNLOAD)
                .routeCode("1")
                .serials(new java.util.ArrayList<>(List.of("SN-1")))
                .build();

        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(unregisteredDispenserService.findUnregisteredSerials(any())).thenReturn(Set.of("SN-1"));
        when(unregisteredDispenserService.normalize("SN-1")).thenReturn("SN-1");
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(request);

        assertThat(movement.getStatus()).isEqualTo(DispenserMovementStatus.SKIPPED_UNREGISTERED);
        verify(dispenserMovementMetrics).recordUnregisteredSerialsExcluded(1);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void should_notRecordMetric_when_noSerialsAreUnregistered() {
        var request = unloadRequest(List.of("SN-1"));
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.UNLOAD)
                .routeCode("1")
                .serials(new java.util.ArrayList<>(List.of("SN-1")))
                .build();

        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(unregisteredDispenserService.findUnregisteredSerials(any())).thenReturn(Set.of());
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(request);

        verify(dispenserMovementMetrics, never()).recordUnregisteredSerialsExcluded(anyInt());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void should_notCheckUnregisteredSerials_when_movementTypeIsLoad() {
        var request = unloadRequest(List.of("SN-1"));
        request.setType(DispenserMovementType.LOAD);
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.LOAD)
                .routeCode("1")
                .serials(new java.util.ArrayList<>(List.of("SN-1")))
                .build();

        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(request);

        verify(unregisteredDispenserService, never()).findUnregisteredSerials(any());
        verify(dispenserMovementMetrics, never()).recordUnregisteredSerialsExcluded(anyInt());
    }
}
