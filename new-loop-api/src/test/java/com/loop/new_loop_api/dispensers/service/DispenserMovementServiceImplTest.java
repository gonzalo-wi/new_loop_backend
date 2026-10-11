package com.loop.new_loop_api.dispensers.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.dispensers.dto.CreateDispenserMovementRequest;
import com.loop.new_loop_api.dispensers.dto.DispenserMovementResponse;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.dispensers.event.DispenserMovementReadyForAguasEvent;
import com.loop.new_loop_api.dispensers.event.DispenserMovementUnregisteredSerialsExcludedEvent;
import com.loop.new_loop_api.dispensers.exception.DuplicateDispenserMovementException;
import com.loop.new_loop_api.dispensers.exception.InvalidBaggedSerialsException;
import com.loop.new_loop_api.dispensers.mapper.DispenserMovementMapper;
import com.loop.new_loop_api.dispensers.metrics.DispenserMovementMetrics;
import com.loop.new_loop_api.dispensers.repository.DispenserMovementRepository;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentService;
import com.loop.new_loop_api.integrations.jmobile.service.iService.UnregisteredDispenserService;
import com.loop.new_loop_api.integrations.odoo.service.iService.OdooDispatchService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Mock private OdooDispatchService           odooDispatchService;
    @Mock private UnregisteredDispenserService  unregisteredDispenserService;
    @Mock private DispenserMovementMetrics      dispenserMovementMetrics;

    private DispenserMovementServiceImpl dispenserMovementService;

    @BeforeEach
    void setUp() {
        dispenserMovementService = new DispenserMovementServiceImpl(
                dispenserMovementRepository, dispenserMovementMapper, currentUserProvider, auditService,
                eventPublisher, aguasEquipmentService, odooDispatchService, unregisteredDispenserService,
                dispenserMovementMetrics);
    }

    private CreateDispenserMovementRequest loadRequest(List<String> serials) {
        var request = new CreateDispenserMovementRequest();
        request.setType(DispenserMovementType.LOAD);
        request.setRouteCode("1");
        request.setTechnician("Tech 1");
        request.setSerials(serials);
        return request;
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
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(request);

        assertThat(movement.getExcludedSerials()).containsExactly("SN-2");
        assertThat(movement.getStatus()).isNotEqualTo(DispenserMovementStatus.SKIPPED_UNREGISTERED);
        verify(dispenserMovementMetrics).recordUnregisteredSerialsExcluded(1);
        verify(eventPublisher).publishEvent(any(DispenserMovementReadyForAguasEvent.class));
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
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(request);

        assertThat(movement.getStatus()).isEqualTo(DispenserMovementStatus.SKIPPED_UNREGISTERED);
        verify(dispenserMovementMetrics).recordUnregisteredSerialsExcluded(1);
        verify(eventPublisher, never()).publishEvent(any(DispenserMovementReadyForAguasEvent.class));
        verify(eventPublisher).publishEvent(any(DispenserMovementUnregisteredSerialsExcludedEvent.class));
    }

    @Test
    void should_publishBothEvents_when_unloadHasSomeUnregisteredSerials() {
        var request = unloadRequest(List.of("SN-1", "SN-2"));
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.UNLOAD)
                .routeCode("1")
                .serials(new java.util.ArrayList<>(List.of("SN-1", "SN-2")))
                .build();

        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(unregisteredDispenserService.findUnregisteredSerials(any())).thenReturn(Set.of("SN-2"));
        when(unregisteredDispenserService.normalize("SN-1")).thenReturn("SN-1");
        when(unregisteredDispenserService.normalize("SN-2")).thenReturn("SN-2");
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(request);

        verify(eventPublisher).publishEvent(any(DispenserMovementReadyForAguasEvent.class));
        verify(eventPublisher).publishEvent(any(DispenserMovementUnregisteredSerialsExcludedEvent.class));
    }

    @Test
    void should_notPublishNoNormalizadosEvent_when_loadHasSerialsUnavailableInOdoo() {
        var request = loadRequest(List.of("SN-1", "SN-2"));
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.LOAD)
                .routeCode("1")
                .serials(new java.util.ArrayList<>(List.of("SN-1", "SN-2")))
                .build();

        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(odooDispatchService.findUnavailableEquipos(any())).thenReturn(Set.of("SN-2"));
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(request);

        verify(eventPublisher, never()).publishEvent(any(DispenserMovementUnregisteredSerialsExcludedEvent.class));
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
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(request);

        verify(dispenserMovementMetrics, never()).recordUnregisteredSerialsExcluded(anyInt());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void should_notCheckUnregisteredSerials_when_movementTypeIsLoad() {
        var request = loadRequest(List.of("SN-1"));
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.LOAD)
                .routeCode("1")
                .serials(new java.util.ArrayList<>(List.of("SN-1")))
                .build();

        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(odooDispatchService.findUnavailableEquipos(any())).thenReturn(Set.of());
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(request);

        verify(unregisteredDispenserService, never()).findUnregisteredSerials(any());
        verify(dispenserMovementMetrics, never()).recordUnregisteredSerialsExcluded(anyInt());
    }

    @Test
    void should_excludeUnavailableInOdooAndStillPublish_when_someLoadSerialsAreNotInOdoo() {
        var request = loadRequest(List.of("SN-1", "SN-2", "SN-3"));
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.LOAD)
                .routeCode("1")
                .serials(new java.util.ArrayList<>(List.of("SN-1", "SN-2", "SN-3")))
                .build();

        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(odooDispatchService.findUnavailableEquipos(any())).thenReturn(Set.of("SN-2"));
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(request);

        assertThat(movement.getExcludedSerials()).containsExactly("SN-2");
        assertThat(movement.serialsToSend()).containsExactly("SN-1", "SN-3");
        assertThat(movement.getStatus()).isNotEqualTo(DispenserMovementStatus.SKIPPED_UNREGISTERED);
        verify(dispenserMovementMetrics).recordUnavailableInOdooExcluded(1);
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void should_skipMovementAndNotPublish_when_everyLoadSerialIsUnavailableInOdoo() {
        var request = loadRequest(List.of("SN-1"));
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.LOAD)
                .routeCode("1")
                .serials(new java.util.ArrayList<>(List.of("SN-1")))
                .build();

        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(odooDispatchService.findUnavailableEquipos(any())).thenReturn(Set.of("SN-1"));
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(request);

        assertThat(movement.getStatus()).isEqualTo(DispenserMovementStatus.SKIPPED_UNREGISTERED);
        verify(dispenserMovementMetrics).recordUnavailableInOdooExcluded(1);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void should_throwDuplicate_when_concurrentInsertHitsActiveMovementUniqueIndex() {
        stubLoadMovementUpToSave();
        when(dispenserMovementRepository.saveAndFlush(any()))
                .thenThrow(uniqueViolation("uq_dispenser_movements_type_route_date_active"));

        assertThatThrownBy(() -> dispenserMovementService.createMovement(loadRequest(List.of("SN-1"))))
                .isInstanceOf(DuplicateDispenserMovementException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void should_rethrow_when_integrityViolationIsNotTheDuplicateIndex() {
        stubLoadMovementUpToSave();
        var other = uniqueViolation("some_other_constraint");
        when(dispenserMovementRepository.saveAndFlush(any())).thenThrow(other);

        assertThatThrownBy(() -> dispenserMovementService.createMovement(loadRequest(List.of("SN-1"))))
                .isSameAs(other);
    }

    @Test
    void shouldRejectBaggedSerials_when_movementIsALoad() {
        var request = loadRequest(List.of("SN-1"));
        request.setBaggedSerials(List.of("SN-1"));

        assertThatThrownBy(() -> dispenserMovementService.createMovement(request))
                .isInstanceOf(InvalidBaggedSerialsException.class);
        verify(dispenserMovementRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectBaggedSerials_when_aBaggedSerialWasNotScanned() {
        var request = unloadRequest(List.of("SN-1"));
        request.setBaggedSerials(List.of("SN-2"));

        assertThatThrownBy(() -> dispenserMovementService.createMovement(request))
                .isInstanceOf(InvalidBaggedSerialsException.class)
                .hasMessageContaining("SN-2");
        verify(dispenserMovementRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldKeepBaggedSerialsInWhatIsSent_when_unloadHasBaggedSerials() {
        var request = unloadRequest(List.of("SN-1", "SN-2"));
        request.setBaggedSerials(List.of("sn-2"));
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.UNLOAD)
                .routeCode("1")
                .serials(new java.util.ArrayList<>(List.of("SN-1", "SN-2")))
                .baggedSerials(new java.util.ArrayList<>(List.of("sn-2")))
                .build();
        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(unregisteredDispenserService.findUnregisteredSerials(any())).thenReturn(Set.of());
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(request);

        assertThat(movement.serialsToSend()).containsExactly("SN-1", "SN-2");
        verify(eventPublisher).publishEvent(any(DispenserMovementReadyForAguasEvent.class));
    }

    @Test
    void shouldNotTouchAguas_when_updateHasInvalidBaggedSerials() {
        var request = unloadRequest(List.of("SN-1"));
        request.setBaggedSerials(List.of("SN-9"));

        assertThatThrownBy(() -> dispenserMovementService.updateMovement(UUID.randomUUID(), request))
                .isInstanceOf(InvalidBaggedSerialsException.class);
        verify(dispenserMovementRepository, never()).findById(any());
        verify(aguasEquipmentService, never()).deleteInAguas(any());
    }

    @Test
    void shouldAcceptALoad_when_baggedSerialsIsEmpty() {
        stubLoadMovementUpToSave();
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());
        var request = loadRequest(List.of("SN-1"));
        request.setBaggedSerials(List.of());

        dispenserMovementService.createMovement(request);

        verify(dispenserMovementRepository).saveAndFlush(any());
    }

    private void stubLoadMovementUpToSave() {
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.LOAD)
                .routeCode("1")
                .movementDate(LocalDate.now())
                .serials(new java.util.ArrayList<>(List.of("SN-1")))
                .build();
        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(odooDispatchService.findUnavailableEquipos(any())).thenReturn(Set.of());
    }

    private static DataIntegrityViolationException uniqueViolation(String constraintName) {
        var cause = new ConstraintViolationException("duplicate key", new SQLException("23505"), constraintName);
        return new DataIntegrityViolationException("could not execute statement", cause);
    }
}
