package com.loop.new_loop_api.dispensers.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.common.exception.UniqueConstraintViolation;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.dispensers.dto.CreateDispenserMovementRequest;
import com.loop.new_loop_api.dispensers.dto.DispenserMovementResponse;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.dispensers.event.DispenserMovementReadyForAguasEvent;
import com.loop.new_loop_api.dispensers.event.DispenserMovementUnregisteredSerialsExcludedEvent;
import com.loop.new_loop_api.dispensers.exception.AguasDeleteFailedException;
import com.loop.new_loop_api.dispensers.exception.DispenserMovementAlreadyCancelledException;
import com.loop.new_loop_api.dispensers.exception.DispenserMovementNotFoundException;
import com.loop.new_loop_api.dispensers.exception.DuplicateDispenserMovementException;
import com.loop.new_loop_api.dispensers.exception.InvalidBaggedSerialsException;
import com.loop.new_loop_api.dispensers.exception.MovementHasLaterReloadException;
import com.loop.new_loop_api.dispensers.exception.SerialsAlreadyLoadedException;
import com.loop.new_loop_api.dispensers.mapper.DispenserMovementMapper;
import com.loop.new_loop_api.dispensers.metrics.DispenserMovementMetrics;
import com.loop.new_loop_api.dispensers.repository.DispenserMovementRepository;
import com.loop.new_loop_api.dispensers.service.iService.DispenserMovementService;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentLookupService;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentService;
import com.loop.new_loop_api.integrations.jmobile.service.iService.UnregisteredDispenserService;
import com.loop.new_loop_api.integrations.odoo.service.iService.OdooDispatchService;
import com.loop.new_loop_api.performance.domain.LoadDuration;
import com.loop.new_loop_api.performance.domain.RouteNumber;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DispenserMovementServiceImpl implements DispenserMovementService {

    private static final Logger log = LoggerFactory.getLogger(DispenserMovementServiceImpl.class);

    // Movimientos de aguas por deafault
    private static final int LOAD_LOCATION_EN_CAMIONETA    = 2;  
    private static final int LOAD_STATE_OPERATIVO          = 2;  
    private static final int UNLOAD_LOCATION_PLANTA_BAJA   = 49; 
    private static final int UNLOAD_STATE_EN_REPARACION    = 4;  

    // Partial unique index from V36 (UNLOAD only: a route may get several LOADs a day); must match the migration's name.
    private static final String ACTIVE_MOVEMENT_UNIQUE_INDEX = "uq_dispenser_movements_unload_route_date_active";

    private static final String LOAD_LOCK_PREFIX = "dispenser-load:";

    private final DispenserMovementRepository dispenserMovementRepository;
    private final DispenserMovementMapper     dispenserMovementMapper;
    private final CurrentUserProvider         currentUserProvider;
    private final AuditService                auditService;
    private final ApplicationEventPublisher   eventPublisher;
    private final AguasEquipmentService       aguasEquipmentService;
    private final OdooDispatchService         odooDispatchService;
    private final UnregisteredDispenserService unregisteredDispenserService;
    private final DispenserMovementMetrics    dispenserMovementMetrics;
    private final RouteRepository             routeRepository;
    private final AguasEquipmentLookupService aguasEquipmentLookupService;

    @Override
    @Transactional
    public DispenserMovementResponse createMovement(CreateDispenserMovementRequest request) {
        return create(request, null);
    }

    /** {@code replaced} is the movement an edit cancelled, or null for a new movement. */
    private DispenserMovementResponse create(CreateDispenserMovementRequest request, DispenserMovement replaced) {
        var date = request.getMovementDate() != null ? request.getMovementDate() : LocalDate.now();
        validateBaggedSerials(request);
        validateNoDuplicate(request, date);
        var earlierLoads = findActiveLoads(request, date);
        validateNoRepeatedSerials(request, date, earlierLoads);
        var movement = buildMovement(request, date, replaced);
        excludeUnregisteredSerials(movement);
        excludeUnavailableInOdoo(movement);
        excludeMissingInAguas(movement);
        var saved    = insertMovement(movement);
        var response = toResponse(saved, !earlierLoads.isEmpty());
        auditService.register("CREATE_DISPENSER_MOVEMENT", "DispenserMovement", saved.getId(), null, response);
        if (saved.getStatus() != DispenserMovementStatus.SKIPPED_UNREGISTERED) {
            eventPublisher.publishEvent(new DispenserMovementReadyForAguasEvent(saved.getId()));
        }
        publishNoNormalizadosIfAny(saved);
        return response;
    }

    /** UNLOAD serials excluded as unregistered still go to Odoo, to the "no normalizados" location. */
    private void publishNoNormalizadosIfAny(DispenserMovement movement) {
        if (movement.getType() != DispenserMovementType.UNLOAD || movement.getExcludedSerials().isEmpty()) return;
        eventPublisher.publishEvent(new DispenserMovementUnregisteredSerialsExcludedEvent(movement.getId()));
    }


    @Override
    @Transactional(readOnly = true)
    public Page<DispenserMovementResponse> getAllMovements(DispenserMovementType type, String routeCode,
                                                           DispenserMovementStatus status, LocalDate from, LocalDate to,
                                                           LocalDate createdFrom, LocalDate createdTo,
                                                           Pageable pageable) {
        Specification<DispenserMovement> spec = (r, q, cb) -> cb.conjunction();
        if (type      != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("type"), type));
        if (routeCode != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("routeCode"), routeCode));
        if (status    != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (from      != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.<LocalDate>get("movementDate"), from));
        if (to        != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.<LocalDate>get("movementDate"), to));
        // createdFrom/createdTo filtran por fecha de registro (createdAt es un timestamp): se abarca
        // el día completo, desde las 00:00 del from hasta antes de las 00:00 del día siguiente al to.
        if (createdFrom != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.<LocalDateTime>get("createdAt"), createdFrom.atStartOfDay()));
        if (createdTo   != null) spec = spec.and((r, q, cb) -> cb.lessThan(r.<LocalDateTime>get("createdAt"), createdTo.plusDays(1).atStartOfDay()));
        var page      = dispenserMovementRepository.findAll(spec, pageable);
        var reloadIds = findReloadIds(page.getContent().stream().map(DispenserMovement::getId).toList());
        return page.map(movement -> toResponse(movement, reloadIds.contains(movement.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public DispenserMovementResponse getMovementById(UUID id) {
        var movement = findMovementById(id);
        return toResponse(movement, isReload(movement));
    }


    @Override
    @Transactional
    public DispenserMovementResponse cancelMovement(UUID id) {
        var movement = findMovementById(id);
        ensureNotCancelled(movement);
        ensureNoLaterReload(movement);
        deleteInAguasIfSent(movement);
        movement.setStatus(DispenserMovementStatus.CANCELLED);
        var saved    = dispenserMovementRepository.save(movement);
        var response = toResponse(saved, isReload(saved));
        auditService.register("CANCEL_DISPENSER_MOVEMENT", "DispenserMovement",
        saved.getId(), null, response);
        return response;
    }

   
    @Override
    @Transactional
    public DispenserMovementResponse updateMovement(UUID id, CreateDispenserMovementRequest request) {
        // Before cancelling: the cancel deletes the movement in Aguas, which a later rollback cannot undo.
        validateBaggedSerials(request);
        cancelMovement(id);
        return create(request, findMovementById(id));
    }
    

    

    //Validaciones
    private DispenserMovement findMovementById(UUID id) {
        return dispenserMovementRepository.findById(id)
                .orElseThrow(() -> new DispenserMovementNotFoundException(id));
    }

    
    /** Flushes so a concurrent duplicate that slipped past validateNoDuplicate surfaces here as a 409. */
    private DispenserMovement insertMovement(DispenserMovement movement) {
        try {
            return dispenserMovementRepository.saveAndFlush(movement);
        } catch (DataIntegrityViolationException ex) {
            if (UniqueConstraintViolation.isCausedBy(ex, ACTIVE_MOVEMENT_UNIQUE_INDEX)) {
                throw new DuplicateDispenserMovementException(
                        movement.getType(), movement.getRouteCode(), movement.getMovementDate());
            }
            throw ex;
        }
    }

    private void validateBaggedSerials(CreateDispenserMovementRequest request) {
        var bagged = request.getBaggedSerials();
        if (bagged == null || bagged.isEmpty()) return;
        if (request.getType() != DispenserMovementType.UNLOAD) throw InvalidBaggedSerialsException.onlyForUnload();

        var notScanned = bagged.stream()
                .filter(serial -> request.getSerials().stream().noneMatch(serial::equalsIgnoreCase))
                .toList();
        if (!notScanned.isEmpty()) throw InvalidBaggedSerialsException.notScanned(notScanned);
    }

    /** Only UNLOAD is one per route and day; a LOAD on a route that already has one is a recarga. */
    private void validateNoDuplicate(CreateDispenserMovementRequest request, LocalDate date) {
        if (request.getType() != DispenserMovementType.UNLOAD) return;
        if (dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
                request.getType(), request.getRouteCode(), date, DispenserMovementStatus.CANCELLED)) {
            throw new DuplicateDispenserMovementException(request.getType(), request.getRouteCode(), date);
        }
    }


    /** Locks before reading, so two concurrent loads (a double tap, or two recargas) can't both pass the checks. */
    private List<DispenserMovement> findActiveLoads(CreateDispenserMovementRequest request, LocalDate date) {
        if (request.getType() != DispenserMovementType.LOAD) return List.of();
        lockLoadsOfRouteDay(request.getRouteCode(), date);
        return dispenserMovementRepository.findByTypeAndRouteCodeAndMovementDateAndStatusNot(
                DispenserMovementType.LOAD, request.getRouteCode(), date, DispenserMovementStatus.CANCELLED);
    }

    /**
     * Odoo adds each recarga to what the route already has, so it must only carry new dispensers. Serials
     * excluded from an earlier load never left, so they may go out now.
     */
    private void validateNoRepeatedSerials(CreateDispenserMovementRequest request, LocalDate date,
                                           List<DispenserMovement> earlierLoads) {
        if (earlierLoads.isEmpty()) return;
        var alreadyLoaded = earlierLoads.stream()
                .flatMap(load -> load.serialsToSend().stream())
                .map(DispenserMovementServiceImpl::normalizeSerial)
                .collect(Collectors.toSet());
        var repeated = request.getSerials().stream()
                .filter(serial -> alreadyLoaded.contains(normalizeSerial(serial)))
                .toList();
        if (!repeated.isEmpty()) {
            throw new SerialsAlreadyLoadedException(repeated, request.getRouteCode(), date);
        }
    }

    private static String normalizeSerial(String serial) {
        return serial.trim().toUpperCase(Locale.ROOT);
    }

    private DispenserMovement buildMovement(CreateDispenserMovementRequest request, LocalDate date,
                                            DispenserMovement replaced) {
        var movement = dispenserMovementMapper.toEntity(request, date);
        movement.setLocationId(resolveLocationId(request.getType(), request.getLocationId()));
        movement.setStateId(resolveStateId(request.getType(), request.getStateId()));
        currentUserProvider.current().ifPresent(user -> {
            movement.setRegisteredBy(user.id());
            movement.setRegisteredByUsername(user.username());
        });
        movement.setBranchId(findRouteBranchId(movement.getRouteCode()));
        applyMetricsLink(movement, request, replaced);
        return movement;
    }

    /** An edit is not a new load: it keeps the original load's duration and points back to it. */
    private void applyMetricsLink(DispenserMovement movement, CreateDispenserMovementRequest request,
                                  DispenserMovement replaced) {
        if (replaced == null) {
            movement.setLoadDurationSeconds(LoadDuration.accept(request.getLoadDurationSeconds()));
            return;
        }
        movement.setReplacesMovementId(replaced.getId());
        movement.setLoadDurationSeconds(replaced.getLoadDurationSeconds());
    }

    /** Matched by number ("179" → "Rto 179"); no branch when no route or more than one has that number. */
    private UUID findRouteBranchId(String routeCode) {
        var number = RouteNumber.of(routeCode);
        if (number == null) return null;
        var routes = routeRepository.findByRouteNumber(number);
        if (routes.size() != 1) return null;
        var branch = routes.get(0).getBranch();
        return branch != null ? branch.getId() : null;
    }

    /**
     * On UNLOAD, serials that jMobile reports as "dispenser no registrado" for the movement date are
     * kept on the movement but never forwarded to Aguas nor to Odoo repair (they go to Odoo's
     * "no normalizados" intake instead). If none is left the movement is closed as
     * SKIPPED_UNREGISTERED, so the caller already gets the final outcome in the create response.
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
                .filter(serial -> unavailable.contains(normalizeSerial(serial)))
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

    /**
     * On LOAD, serials Aguas reports as not existing are kept on the movement but never forwarded, the same way
     * as the Odoo check. Only the serials Odoo left to send are asked about. A failed lookup excludes nothing.
     */
    private void excludeMissingInAguas(DispenserMovement movement) {
        if (movement.getType() != DispenserMovementType.LOAD) return;
        var candidates = movement.serialsToSend();
        if (candidates.isEmpty()) return;

        var missing = aguasEquipmentLookupService.findMissingSerials(candidates);
        if (missing.isEmpty()) return;

        var excluded = candidates.stream()
                .filter(serial -> missing.contains(normalizeSerial(serial)))
                .toList();
        if (excluded.isEmpty()) return;

        movement.getExcludedSerials().addAll(excluded);
        log.warn("Movement on route {}: {} of {} serial(s) excluded as not existing in Aguas: {}",
                movement.getRouteCode(), excluded.size(), movement.getSerials().size(), excluded);
        dispenserMovementMetrics.recordMissingInAguasExcluded(excluded.size());

        if (movement.serialsToSend().isEmpty()) {
            movement.setStatus(DispenserMovementStatus.SKIPPED_UNREGISTERED);
            log.warn("Movement on route {} not sent to Aguas: no scanned serial is both available in Odoo and "
                    + "existing in Aguas", movement.getRouteCode());
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

    /** Cancelling an earlier load would leave Aguas and Odoo out of order: only the day's last load can change. */
    private void ensureNoLaterReload(DispenserMovement movement) {
        if (movement.getType() != DispenserMovementType.LOAD) return;
        // Same lock as createMovement: otherwise a recarga could be registered while this load is being cancelled.
        lockLoadsOfRouteDay(movement.getRouteCode(), movement.getMovementDate());
        if (dispenserMovementRepository.existsByTypeAndRouteCodeAndMovementDateAndStatusNotAndCreatedAtAfter(
                DispenserMovementType.LOAD, movement.getRouteCode(), movement.getMovementDate(),
                DispenserMovementStatus.CANCELLED, movement.getCreatedAt())) {
            throw new MovementHasLaterReloadException(movement.getId());
        }
    }

    private void lockLoadsOfRouteDay(String routeCode, LocalDate date) {
        dispenserMovementRepository.lockLoadsOfRouteDay(LOAD_LOCK_PREFIX + routeCode + ":" + date);
    }

    private boolean isReload(DispenserMovement movement) {
        if (movement.getType() != DispenserMovementType.LOAD) return false;
        return findReloadIds(List.of(movement.getId())).contains(movement.getId());
    }

    private Set<UUID> findReloadIds(List<UUID> ids) {
        if (ids.isEmpty()) return Set.of();
        return dispenserMovementRepository.findReloadIds(ids, DispenserMovementType.LOAD, DispenserMovementStatus.CANCELLED);
    }

    private DispenserMovementResponse toResponse(DispenserMovement movement, boolean reload) {
        return dispenserMovementMapper.toResponse(movement).toBuilder().reload(reload).build();
    }

    private void deleteInAguasIfSent(DispenserMovement movement) {
        if (movement.getAguasMovementId() != null && !aguasEquipmentService.deleteInAguas(movement.getId())) {
            throw new AguasDeleteFailedException(movement.getId());
        }
    }


    
}
