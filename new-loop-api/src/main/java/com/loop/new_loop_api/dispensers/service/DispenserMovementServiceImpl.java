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
import com.loop.new_loop_api.dispensers.repository.DispenserMovementRepository;
import com.loop.new_loop_api.dispensers.service.iService.DispenserMovementService;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentService;
import lombok.RequiredArgsConstructor;
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

    // Aguas default destinations per movement type
    private static final int LOAD_LOCATION_EN_CAMIONETA   = 2;  // salida_camion -> EN CAMIONETA
    private static final int LOAD_STATE_OPERATIVO          = 2;  // salida_camion -> OPERATIVO
    private static final int UNLOAD_LOCATION_PLANTA_BAJA   = 49; // vuelta_camion -> PLANTA BAJA
    private static final int UNLOAD_STATE_EN_REPARACION    = 4;  // vuelta_camion -> EN REPARACION

    private final DispenserMovementRepository dispenserMovementRepository;
    private final DispenserMovementMapper     dispenserMovementMapper;
    private final CurrentUserProvider         currentUserProvider;
    private final AuditService                auditService;
    private final ApplicationEventPublisher   eventPublisher;
    private final AguasEquipmentService       aguasEquipmentService;

    @Override
    @Transactional
    public DispenserMovementResponse createMovement(CreateDispenserMovementRequest request) {
        var date = request.getMovementDate() != null ? request.getMovementDate() : LocalDate.now();

        // Only one active movement of each type (LOAD / UNLOAD) per route and date.
        if (dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                request.getType(), request.getRouteCode(), date, DispenserMovementStatus.CANCELLED)) {
            throw new DuplicateDispenserMovementException(request.getType(), request.getRouteCode(), date);
        }

        var movement = dispenserMovementMapper.toEntity(request, date);

        movement.setLocationId(resolveLocationId(request.getType(), request.getLocationId()));
        movement.setStateId(resolveStateId(request.getType(), request.getStateId()));

        currentUserProvider.current().ifPresent(user -> {
            movement.setRegisteredBy(user.id());
            movement.setRegisteredByUsername(user.username());
        });

        var saved    = dispenserMovementRepository.save(movement);
        var response = dispenserMovementMapper.toResponse(saved);
        auditService.register("CREATE_DISPENSER_MOVEMENT", "DispenserMovement", saved.getId(), null, response);

        eventPublisher.publishEvent(new DispenserMovementReadyForAguasEvent(saved.getId()));
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
        if (movement.getStatus() == DispenserMovementStatus.CANCELLED) {
            throw new DispenserMovementAlreadyCancelledException(id);
        }
        // If it already reached Aguas, delete it there first (throws if Aguas fails, keeping data consistent).
        if (movement.getAguasMovementId() != null && !aguasEquipmentService.deleteInAguas(id)) {
            throw new AguasDeleteFailedException(id);
        }
        movement.setStatus(DispenserMovementStatus.CANCELLED);
        var saved    = dispenserMovementRepository.save(movement);
        var response = dispenserMovementMapper.toResponse(saved);
        auditService.register("CANCEL_DISPENSER_MOVEMENT", "DispenserMovement", saved.getId(), null, response);
        return response;
    }

    @Override
    @Transactional
    public DispenserMovementResponse updateMovement(UUID id, CreateDispenserMovementRequest request) {
        // Aguas has no update endpoint: cancel the old movement (deleting it in Aguas) and create a new one.
        cancelMovement(id);
        return createMovement(request);
    }

    private DispenserMovement findMovementById(UUID id) {
        return dispenserMovementRepository.findById(id)
                .orElseThrow(() -> new DispenserMovementNotFoundException(id));
    }

    private Integer resolveLocationId(DispenserMovementType type, Integer provided) {
        if (provided != null) return provided;
        return type == DispenserMovementType.LOAD ? LOAD_LOCATION_EN_CAMIONETA : UNLOAD_LOCATION_PLANTA_BAJA;
    }

    private Integer resolveStateId(DispenserMovementType type, Integer provided) {
        if (provided != null) return provided;
        return type == DispenserMovementType.LOAD ? LOAD_STATE_OPERATIVO : UNLOAD_STATE_EN_REPARACION;
    }
}
