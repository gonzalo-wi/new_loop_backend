package com.loop.new_loop_api.dispensers.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.branches.entity.Branch;
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
import com.loop.new_loop_api.dispensers.exception.MovementHasLaterReloadException;
import com.loop.new_loop_api.dispensers.exception.SerialsAlreadyLoadedException;
import com.loop.new_loop_api.dispensers.mapper.DispenserMovementMapper;
import com.loop.new_loop_api.dispensers.metrics.DispenserMovementMetrics;
import com.loop.new_loop_api.dispensers.repository.DispenserMovementRepository;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentLookupService;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentService;
import com.loop.new_loop_api.integrations.jmobile.service.iService.UnregisteredDispenserService;
import com.loop.new_loop_api.integrations.odoo.service.iService.OdooDispatchService;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
    @Mock private RouteRepository               routeRepository;
    @Mock private AguasEquipmentLookupService   aguasEquipmentLookupService;

    private DispenserMovementServiceImpl dispenserMovementService;

    @BeforeEach
    void setUp() {
        dispenserMovementService = new DispenserMovementServiceImpl(
                dispenserMovementRepository, dispenserMovementMapper, currentUserProvider, auditService,
                eventPublisher, aguasEquipmentService, odooDispatchService, unregisteredDispenserService,
                dispenserMovementMetrics, routeRepository, aguasEquipmentLookupService);
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
    void shouldExcludeSerialsMissingInAguasAndStillPublish_when_someLoadSerialsAreNotInAguas() {
        var movement = loadMovement(List.of("SN-1", "SN-2", "SN-3"));
        stubLoadUpToResponse(movement);
        when(odooDispatchService.findUnavailableEquipos(any())).thenReturn(Set.of());
        when(aguasEquipmentLookupService.findMissingSerials(List.of("SN-1", "SN-2", "SN-3")))
                .thenReturn(Set.of("SN-3"));

        dispenserMovementService.createMovement(loadRequest(List.of("SN-1", "SN-2", "SN-3")));

        assertThat(movement.getExcludedSerials()).containsExactly("SN-3");
        assertThat(movement.serialsToSend()).containsExactly("SN-1", "SN-2");
        assertThat(movement.getStatus()).isNotEqualTo(DispenserMovementStatus.SKIPPED_UNREGISTERED);
        verify(dispenserMovementMetrics).recordMissingInAguasExcluded(1);
        verify(eventPublisher).publishEvent(any(DispenserMovementReadyForAguasEvent.class));
    }

    @Test
    void shouldOnlyCheckInAguasTheSerialsOdooLeftToSend_when_loadHasSerialsUnavailableInOdoo() {
        var movement = loadMovement(List.of("SN-1", "sn-2"));
        stubLoadUpToResponse(movement);
        when(odooDispatchService.findUnavailableEquipos(any())).thenReturn(Set.of("SN-1"));
        when(aguasEquipmentLookupService.findMissingSerials(List.of("sn-2"))).thenReturn(Set.of("SN-2"));

        dispenserMovementService.createMovement(loadRequest(List.of("SN-1", "sn-2")));

        assertThat(movement.getExcludedSerials()).containsExactly("SN-1", "sn-2");
        assertThat(movement.getStatus()).isEqualTo(DispenserMovementStatus.SKIPPED_UNREGISTERED);
        verify(dispenserMovementMetrics).recordMissingInAguasExcluded(1);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldNotCheckInAguas_when_odooAlreadyExcludedEveryLoadSerial() {
        var movement = loadMovement(List.of("SN-1"));
        stubLoadUpToResponse(movement);
        when(odooDispatchService.findUnavailableEquipos(any())).thenReturn(Set.of("SN-1"));

        dispenserMovementService.createMovement(loadRequest(List.of("SN-1")));

        verify(aguasEquipmentLookupService, never()).findMissingSerials(any());
    }

    @Test
    void shouldNotCheckInAguas_when_movementTypeIsUnload() {
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.UNLOAD)
                .routeCode("1")
                .serials(new java.util.ArrayList<>(List.of("SN-1")))
                .build();
        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        stubLoadUpToResponse(movement);
        when(unregisteredDispenserService.findUnregisteredSerials(any())).thenReturn(Set.of());

        dispenserMovementService.createMovement(unloadRequest(List.of("SN-1")));

        verify(aguasEquipmentLookupService, never()).findMissingSerials(any());
    }

    private static DispenserMovement loadMovement(List<String> serials) {
        return DispenserMovement.builder()
                .type(DispenserMovementType.LOAD)
                .routeCode("1")
                .serials(new java.util.ArrayList<>(serials))
                .build();
    }

    private void stubLoadUpToResponse(DispenserMovement movement) {
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());
    }

    @Test
    void should_throwDuplicate_when_concurrentInsertHitsActiveUnloadUniqueIndex() {
        stubUnloadMovementUpToSave();
        when(dispenserMovementRepository.saveAndFlush(any()))
                .thenThrow(uniqueViolation("uq_dispenser_movements_unload_route_date_active"));

        assertThatThrownBy(() -> dispenserMovementService.createMovement(unloadRequest(List.of("SN-1"))))
                .isInstanceOf(DuplicateDispenserMovementException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldRejectASecondUnload_when_routeAlreadyHasAnActiveUnloadThatDay() {
        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> dispenserMovementService.createMovement(unloadRequest(List.of("SN-1"))))
                .isInstanceOf(DuplicateDispenserMovementException.class);
        verify(dispenserMovementRepository, never()).saveAndFlush(any());
    }

    // ---------- recarga: 2nd+ LOAD on the same route and date ----------

    @Test
    void shouldRegisterAReload_when_routeAlreadyHasALoadThatDay() {
        stubLoadMovementUpToSave();
        when(dispenserMovementRepository.findByTypeAndRouteCodeAndMovementDateAndStatusNot(
                DispenserMovementType.LOAD, "1", LocalDate.now(), DispenserMovementStatus.CANCELLED))
                .thenReturn(List.of(activeLoad(List.of("SN-9"))));
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        var response = dispenserMovementService.createMovement(loadRequest(List.of("SN-1")));

        assertThat(response.isReload()).isTrue();
        verify(dispenserMovementRepository, never())
                .existsByTypeAndRouteCodeAndMovementDateAndStatusNot(any(), any(), any(), any());
        verify(eventPublisher).publishEvent(any(DispenserMovementReadyForAguasEvent.class));
    }

    @Test
    void shouldLockTheRouteDay_when_creatingALoad() {
        stubLoadMovementUpToSave();
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(loadRequest(List.of("SN-1")));

        // Without a unique index for LOAD, the lock is what keeps two concurrent loads from both passing the checks.
        var order = inOrder(dispenserMovementRepository);
        order.verify(dispenserMovementRepository).lockLoadsOfRouteDay("dispenser-load:1:" + LocalDate.now());
        order.verify(dispenserMovementRepository).findByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any());
    }

    @Test
    void shouldLockTheRouteDay_when_cancellingALoad() {
        var load = activeLoad(List.of("SN-1"));
        when(dispenserMovementRepository.findById(load.getId())).thenReturn(Optional.of(load));
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.cancelMovement(load.getId());

        var order = inOrder(dispenserMovementRepository);
        order.verify(dispenserMovementRepository).lockLoadsOfRouteDay("dispenser-load:1:" + load.getMovementDate());
        order.verify(dispenserMovementRepository).existsByTypeAndRouteCodeAndMovementDateAndStatusNotAndCreatedAtAfter(
                any(), any(), any(), any(), any());
    }

    @Test
    void shouldNotLock_when_creatingAnUnload() {
        stubUnloadMovementUpToSave();
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(unloadRequest(List.of("SN-1")));

        verify(dispenserMovementRepository, never()).lockLoadsOfRouteDay(any());
    }

    @Test
    void shouldNotFlagAsReload_when_itIsTheFirstLoadOfTheDay() {
        stubLoadMovementUpToSave();
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        var response = dispenserMovementService.createMovement(loadRequest(List.of("SN-1")));

        assertThat(response.isReload()).isFalse();
    }

    @Test
    void shouldRejectAReload_when_aSerialAlreadyWentOutInAnEarlierLoadThatDay() {
        when(dispenserMovementRepository.findByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any()))
                .thenReturn(List.of(activeLoad(List.of("SN-1", "SN-5"))));

        assertThatThrownBy(() -> dispenserMovementService.createMovement(loadRequest(List.of("sn-1", "SN-2"))))
                .isInstanceOf(SerialsAlreadyLoadedException.class)
                .hasMessageContaining("sn-1")
                .hasMessageNotContaining("SN-2");
        verify(dispenserMovementRepository, never()).saveAndFlush(any());
        verify(odooDispatchService, never()).findUnavailableEquipos(any());
    }

    @Test
    void shouldAllowReloadingASerial_when_itWasExcludedFromTheEarlierLoad() {
        stubLoadMovementUpToSave();
        var earlier = activeLoad(List.of("SN-1"));
        earlier.getExcludedSerials().add("SN-1"); // never left: unavailable in Odoo at the time
        when(dispenserMovementRepository.findByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(List.of(earlier));
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(loadRequest(List.of("SN-1")));

        verify(dispenserMovementRepository).saveAndFlush(any());
    }

    @Test
    void shouldExcludeUnavailableInOdoo_when_scannedSerialCaseDiffersFromOdoo() {
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.LOAD)
                .routeCode("1")
                .serials(new java.util.ArrayList<>(List.of("sn-1", "sn-2")))
                .build();
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(odooDispatchService.findUnavailableEquipos(any())).thenReturn(Set.of("SN-2"));
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.createMovement(loadRequest(List.of("sn-1", "sn-2")));

        assertThat(movement.getExcludedSerials()).containsExactly("sn-2");
    }

    @Test
    void shouldRejectCancellingALoad_when_aLaterReloadExists() {
        var load = activeLoad(List.of("SN-1"));
        load.setAguasMovementId("77");
        when(dispenserMovementRepository.findById(load.getId())).thenReturn(Optional.of(load));
        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNotAndCreatedAtAfter(
                DispenserMovementType.LOAD, "1", load.getMovementDate(), DispenserMovementStatus.CANCELLED,
                load.getCreatedAt())).thenReturn(true);

        assertThatThrownBy(() -> dispenserMovementService.cancelMovement(load.getId()))
                .isInstanceOf(MovementHasLaterReloadException.class);
        verify(aguasEquipmentService, never()).deleteInAguas(any());
        assertThat(load.getStatus()).isNotEqualTo(DispenserMovementStatus.CANCELLED);
    }

    @Test
    void shouldRejectCorrectingALoad_when_aLaterReloadExists() {
        var load = activeLoad(List.of("SN-1"));
        when(dispenserMovementRepository.findById(load.getId())).thenReturn(Optional.of(load));
        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNotAndCreatedAtAfter(
                any(), any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> dispenserMovementService.updateMovement(load.getId(), loadRequest(List.of("SN-2"))))
                .isInstanceOf(MovementHasLaterReloadException.class);
        verify(aguasEquipmentService, never()).deleteInAguas(any());
        verify(dispenserMovementRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldCancelTheLastReload_when_noLaterLoadExists() {
        var reload = activeLoad(List.of("SN-1"));
        reload.setAguasMovementId("78");
        when(dispenserMovementRepository.findById(reload.getId())).thenReturn(Optional.of(reload));
        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNotAndCreatedAtAfter(
                any(), any(), any(), any(), any())).thenReturn(false);
        when(aguasEquipmentService.deleteInAguas(reload.getId())).thenReturn(true);
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.cancelMovement(reload.getId());

        assertThat(reload.getStatus()).isEqualTo(DispenserMovementStatus.CANCELLED);
    }

    @Test
    void shouldNotCheckForLaterReloads_when_cancellingAnUnload() {
        var unload = activeLoad(List.of("SN-1"));
        unload.setType(DispenserMovementType.UNLOAD);
        when(dispenserMovementRepository.findById(unload.getId())).thenReturn(Optional.of(unload));
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());

        dispenserMovementService.cancelMovement(unload.getId());

        verify(dispenserMovementRepository, never()).existsByTypeAndRouteCodeAndMovementDateAndStatusNotAndCreatedAtAfter(
                any(), any(), any(), any(), any());
    }

    @Test
    void shouldFlagReloadsInTheListing_when_anEarlierLoadExistsForTheSameRouteAndDate() {
        var first  = activeLoad(List.of("SN-1"));
        var second = activeLoad(List.of("SN-2"));
        var pageable = PageRequest.of(0, 20);
        when(dispenserMovementRepository.findAll(ArgumentMatchers.<Specification<DispenserMovement>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        when(dispenserMovementRepository.findReloadIds(any(), any(), any())).thenReturn(Set.of(second.getId()));
        when(dispenserMovementMapper.toResponse(first)).thenReturn(DispenserMovementResponse.builder().id(first.getId()).build());
        when(dispenserMovementMapper.toResponse(second)).thenReturn(DispenserMovementResponse.builder().id(second.getId()).build());

        var page = dispenserMovementService.getAllMovements(null, null, null, null, null, null, null, pageable);

        assertThat(page.getContent()).extracting(DispenserMovementResponse::isReload).containsExactly(false, true);
    }

    @Test
    void shouldFlagReload_when_gettingAReloadById() {
        var reload = activeLoad(List.of("SN-2"));
        when(dispenserMovementRepository.findById(reload.getId())).thenReturn(Optional.of(reload));
        when(dispenserMovementRepository.findReloadIds(any(), any(), any())).thenReturn(Set.of(reload.getId()));
        when(dispenserMovementMapper.toResponse(reload)).thenReturn(DispenserMovementResponse.builder().build());

        assertThat(dispenserMovementService.getMovementById(reload.getId()).isReload()).isTrue();
    }

    private static DispenserMovement activeLoad(List<String> serials) {
        return DispenserMovement.builder()
                .id(UUID.randomUUID())
                .type(DispenserMovementType.LOAD)
                .status(DispenserMovementStatus.SENT_TO_AGUAS)
                .routeCode("1")
                .movementDate(LocalDate.now())
                .createdAt(java.time.LocalDateTime.now().minusHours(1))
                .serials(new java.util.ArrayList<>(serials))
                .build();
    }

    private void stubUnloadMovementUpToSave() {
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.UNLOAD)
                .routeCode("1")
                .movementDate(LocalDate.now())
                .serials(new java.util.ArrayList<>(List.of("SN-1")))
                .build();
        when(dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(unregisteredDispenserService.findUnregisteredSerials(any())).thenReturn(Set.of());
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

    // ---------- operational metrics: load duration, route branch, edit link ----------

    @Test
    void shouldStoreLoadDurationAndRouteBranch_when_creatingALoad() {
        stubLoadMovementUpToSave();
        stubSaveAndResponse();
        var branchId = UUID.randomUUID();
        when(routeRepository.findByRouteNumber(1L)).thenReturn(List.of(routeWithBranch("Rto 1", branchId)));
        var request = loadRequest(List.of("SN-1"));
        request.setLoadDurationSeconds(600);

        dispenserMovementService.createMovement(request);

        var saved = savedMovement();
        assertThat(saved.getLoadDurationSeconds()).isEqualTo(600);
        assertThat(saved.getBranchId()).isEqualTo(branchId);
        assertThat(saved.getReplacesMovementId()).isNull();
    }

    @Test
    void shouldDropLoadDuration_when_itIsAnHourOrMore() {
        stubLoadMovementUpToSave();
        stubSaveAndResponse();
        var request = loadRequest(List.of("SN-1"));
        request.setLoadDurationSeconds(3600);

        dispenserMovementService.createMovement(request);

        assertThat(savedMovement().getLoadDurationSeconds()).isNull();
    }

    @Test
    void shouldAcceptMovementWithoutDurationOrBranch_when_appIsOldAndRouteIsUnknown() {
        stubLoadMovementUpToSave();
        stubSaveAndResponse();
        when(routeRepository.findByRouteNumber(1L)).thenReturn(List.of());

        dispenserMovementService.createMovement(loadRequest(List.of("SN-1")));

        var saved = savedMovement();
        assertThat(saved.getLoadDurationSeconds()).isNull();
        assertThat(saved.getBranchId()).isNull();
    }

    @Test
    void shouldLeaveBranchEmpty_when_severalRoutesShareTheNumber() {
        stubLoadMovementUpToSave();
        stubSaveAndResponse();
        when(routeRepository.findByRouteNumber(1L)).thenReturn(List.of(
                routeWithBranch("Rto 1", UUID.randomUUID()), routeWithBranch("R01", UUID.randomUUID())));

        dispenserMovementService.createMovement(loadRequest(List.of("SN-1")));

        assertThat(savedMovement().getBranchId()).isNull();
    }

    @Test
    void shouldNotLookUpTheRoute_when_routeCodeHasNoNumber() {
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.LOAD)
                .routeCode("Rto")
                .movementDate(LocalDate.now())
                .serials(new java.util.ArrayList<>(List.of("SN-1")))
                .build();
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(odooDispatchService.findUnavailableEquipos(any())).thenReturn(Set.of());
        stubSaveAndResponse();
        var request = loadRequest(List.of("SN-1"));
        request.setRouteCode("Rto");

        dispenserMovementService.createMovement(request);

        assertThat(savedMovement().getBranchId()).isNull();
        verify(routeRepository, never()).findByRouteNumber(anyLong());
    }

    @Test
    void shouldLinkToTheReplacedMovementAndKeepItsDuration_when_editingAMovement() {
        var original = activeLoad(List.of("SN-1"));
        original.setLoadDurationSeconds(420);
        when(dispenserMovementRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubLoadMovementUpToSave();
        stubSaveAndResponse();
        var request = loadRequest(List.of("SN-2"));
        request.setLoadDurationSeconds(15);

        dispenserMovementService.updateMovement(original.getId(), request);

        var saved = savedMovement();
        assertThat(original.getStatus()).isEqualTo(DispenserMovementStatus.CANCELLED);
        assertThat(saved.getReplacesMovementId()).isEqualTo(original.getId());
        assertThat(saved.getLoadDurationSeconds()).isEqualTo(420);
    }

    private static Route routeWithBranch(String code, UUID branchId) {
        return Route.builder().code(code).branch(Branch.builder().id(branchId).build()).build();
    }

    private void stubSaveAndResponse() {
        when(dispenserMovementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementMapper.toResponse(any())).thenReturn(DispenserMovementResponse.builder().build());
    }

    private DispenserMovement savedMovement() {
        var captor = ArgumentCaptor.forClass(DispenserMovement.class);
        verify(dispenserMovementRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private void stubLoadMovementUpToSave() {
        var movement = DispenserMovement.builder()
                .type(DispenserMovementType.LOAD)
                .routeCode("1")
                .movementDate(LocalDate.now())
                .serials(new java.util.ArrayList<>(List.of("SN-1")))
                .build();
        when(dispenserMovementMapper.toEntity(any(), any())).thenReturn(movement);
        when(currentUserProvider.current()).thenReturn(Optional.empty());
        when(odooDispatchService.findUnavailableEquipos(any())).thenReturn(Set.of());
    }

    private static DataIntegrityViolationException uniqueViolation(String constraintName) {
        var cause = new ConstraintViolationException("duplicate key", new SQLException("23505"), constraintName);
        return new DataIntegrityViolationException("could not execute statement", cause);
    }
}
