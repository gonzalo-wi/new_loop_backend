package com.loop.new_loop_api.dispensers.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.dispensers.dto.CreateDispenserMovementRequest;
import com.loop.new_loop_api.dispensers.dto.DispenserMovementResponse;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.dispensers.event.DispenserMovementReadyForAguasEvent;
import com.loop.new_loop_api.dispensers.exception.AguasDeleteFailedException;
import com.loop.new_loop_api.dispensers.exception.DispenserMovementAlreadyCancelledException;
import com.loop.new_loop_api.dispensers.exception.DispenserMovementNotFoundException;
import com.loop.new_loop_api.dispensers.exception.DuplicateDispenserMovementException;
import com.loop.new_loop_api.dispensers.mapper.DispenserMovementMapper;
import com.loop.new_loop_api.dispensers.metrics.DispenserMovementMetrics;
import com.loop.new_loop_api.dispensers.repository.DispenserMovementRepository;
import com.loop.new_loop_api.dispensers.service.iService.DispenserMovementService;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentService;
import com.loop.new_loop_api.integrations.jmobile.service.iService.UnregisteredDispenserService;
import com.loop.new_loop_api.integrations.odoo.service.iService.OdooDispatchService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DispenserMovementServiceImpl implements DispenserMovementService {

    private static final Logger log = LoggerFactory.getLogger(DispenserMovementServiceImpl.class);

    // Movimientos de aguas por deafault
    private static final int LOAD_LOCATION_EN_CAMIONETA    = 2;  
    private static final int LOAD_STATE_OPERATIVO          = 2;  
    private static final int UNLOAD_LOCATION_PLANTA_BAJA   = 49; 
    private static final int UNLOAD_STATE_EN_REPARACION    = 4;  

    private final DispenserMovementRepository dispenserMovementRepository;
    private final DispenserMovementMapper     dispenserMovementMapper;
    private final CurrentUserProvider         currentUserProvider;
    private final AuditService                auditService;
    private final ApplicationEventPublisher   eventPublisher;
    private final AguasEquipmentService       aguasEquipmentService;
    private final OdooDispatchService         odooDispatchService;
    private final UnregisteredDispenserService unregisteredDispenserService;
    private final DispenserMovementMetrics    dispenserMovementMetrics;

    @Override
    @Transactional
    public DispenserMovementResponse createMovement(CreateDispenserMovementRequest request) {
        var date = request.getMovementDate() != null ? request.getMovementDate() : LocalDate.now();
        validateNoDuplicate(request, date);
        var movement = buildMovement(request, date);
        excludeUnregisteredSerials(movement);
        excludeUnavailableInOdoo(movement);
        var saved    = dispenserMovementRepository.save(movement);
        var response = dispenserMovementMapper.toResponse(saved);
        auditService.register("CREATE_DISPENSER_MOVEMENT", "DispenserMovement", saved.getId(), null, response);
        if (saved.getStatus() != DispenserMovementStatus.SKIPPED_UNREGISTERED) {
            eventPublisher.publishEvent(new DispenserMovementReadyForAguasEvent(saved.getId()));
        }
        return response;
    }


    @Override
    @Transactional(readOnly = true)
    public Page<DispenserMovementResponse> getAllMovements(DispenserMovementType type, String routeCode,
                                                           DispenserMovementStatus status, LocalDate from, LocalDate to,
                                                           Pageable pageable) {
        Specification<DispenserMovement> spec = (r, q, cb) -> cb.conjunction();
        if (type      != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("type"), type));
        if (routeCode != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("routeCode"), routeCode));
        if (status    != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (from      != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.<LocalDate>get("movementDate"), from));
        if (to        != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.<LocalDate>get("movementDate"), to));
        return dispenserMovementRepository.findAll(spec, pageable).map(dispenserMovementMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public DispenserMovementResponse getMovementById(UUID id) {
        return dispenserMovementMapper.toResponse(findMovementById(id));
    }


    @Override
    @Transactional
    public DispenserMovementResponse cancelMovement(UUID id) {
        var movement = findMovementById(id);
        ensureNotCancelled(movement);
        deleteInAguasIfSent(movement);
        movement.setStatus(DispenserMovementStatus.CANCELLED);
        var saved    = dispenserMovementRepository.save(movement);
        var response = dispenserMovementMapper.toResponse(saved);
        auditService.register("CANCEL_DISPENSER_MOVEMENT", "DispenserMovement",
        saved.getId(), null, response);
        return response;
    }

   
    @Override
    @Transactional
    public DispenserMovementResponse updateMovement(UUID id, CreateDispenserMovementRequest request) {
        cancelMovement(id);
        return createMovement(request);
    }
    

    

    //Validaciones
    private DispenserMovement findMovementById(UUID id) {
        return dispenserMovementRepository.findById(id)
                .orElseThrow(() -> new DispenserMovementNotFoundException(id));
    }

    
    private void validateNoDuplicate(CreateDispenserMovementRequest request, LocalDate date) {
        if (dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                request.getType(), request.getRouteCode(), date, DispenserMovementStatus.CANCELLED)) {
            throw new DuplicateDispenserMovementException(request.getType(), request.getRouteCode(), date);
        }
    }


    private DispenserMovement buildMovement(CreateDispenserMovementRequest request, LocalDate date) {
        var movement = dispenserMovementMapper.toEntity(request, date);
        movement.setLocationId(resolveLocationId(request.getType(), request.getLocationId()));
        movement.setStateId(resolveStateId(request.getType(), request.getStateId()));
        currentUserProvider.current().ifPresent(user -> {
            movement.setRegisteredBy(user.id());
            movement.setRegisteredByUsername(user.username());
        });
        return movement;
    }

    /**
     * On UNLOAD, serials that jMobile reports as "dispenser no registrado" for the movement date are
     * kept on the movement but never forwarded to Aguas/Odoo. If none is left the movement is closed
     * as SKIPPED_UNREGISTERED, so the caller already gets the final outcome in the create response.
     */
    private void excludeUnregisteredSerials(DispenserMovement movement) {
        if (movement.getType() != DispenserMovementType.UNLOAD) return;

        var unregistered = unregisteredDispenserService.findUnregisteredSerials(movement.getMovementDate());
        if (unregistered.isEmpty()) return;

        var excluded = movement.getSerials().stream()
                .filter(serial -> unregistered.contains(unregisteredDispenserService.normalize(serial)))
                .toList();
        if (excluded.isEmpty()) return;

        movement.getExcludedSerials().addAll(excluded);
        log.warn("Movement on route {}: {} of {} serial(s) excluded as unregistered in jMobile: {}",
                movement.getRouteCode(), excluded.size(), movement.getSerials().size(), excluded);
        dispenserMovementMetrics.recordUnregisteredSerialsExcluded(excluded.size());

        if (movement.serialsToSend().isEmpty()) {
            movement.setStatus(DispenserMovementStatus.SKIPPED_UNREGISTERED);
            log.warn("Movement on route {} not sent to Aguas: every scanned serial is unregistered in jMobile",
                    movement.getRouteCode());
        }
    }

    /**
     * On LOAD, serials that Odoo reports as not available in expedición (unknown serial, wrong location or
     * no stock) are kept on the movement but never forwarded to Aguas/Odoo. If none is left the movement is
     * closed as SKIPPED_UNREGISTERED so the caller already gets the final outcome in the create response.
     * A validation failure excludes nothing, so Odoo being unreachable never blocks a load.
     */
    private void excludeUnavailableInOdoo(DispenserMovement movement) {
        if (movement.getType() != DispenserMovementType.LOAD) return;

        var unavailable = odooDispatchService.findUnavailableEquipos(movement.getSerials());
        if (unavailable.isEmpty()) return;

        var excluded = movement.getSerials().stream()
                .filter(unavailable::contains)
                .toList();
        if (excluded.isEmpty()) return;

        movement.getExcludedSerials().addAll(excluded);
        log.warn("Movement on route {}: {} of {} serial(s) excluded as unavailable in Odoo: {}",
                movement.getRouteCode(), excluded.size(), movement.getSerials().size(), excluded);
        dispenserMovementMetrics.recordUnavailableInOdooExcluded(excluded.size());

        if (movement.serialsToSend().isEmpty()) {
            movement.setStatus(DispenserMovementStatus.SKIPPED_UNREGISTERED);
            log.warn("Movement on route {} not sent to Aguas: every scanned serial is unavailable in Odoo",
                    movement.getRouteCode());
        }
    }

    private Integer resolveLocationId(DispenserMovementType type, Integer provided) {
        if (provided != null) return provided;
        return type == DispenserMovementType.LOAD ? LOAD_LOCATION_EN_CAMIONETA : UNLOAD_LOCATION_PLANTA_BAJA;
    }

    private Integer resolveStateId(DispenserMovementType type, Integer provided) {
        if (provided != null) return provided;
        return type == DispenserMovementType.LOAD ? LOAD_STATE_OPERATIVO : UNLOAD_STATE_EN_REPARACION;
    }

     private void ensureNotCancelled(DispenserMovement movement) {
        if (movement.getStatus() == DispenserMovementStatus.CANCELLED) {
            throw new DispenserMovementAlreadyCancelledException(movement.getId());
        }
    }

    private void deleteInAguasIfSent(DispenserMovement movement) {
        if (movement.getAguasMovementId() != null && !aguasEquipmentService.deleteInAguas(movement.getId())) {
            throw new AguasDeleteFailedException(movement.getId());
        }
    }


    
}
