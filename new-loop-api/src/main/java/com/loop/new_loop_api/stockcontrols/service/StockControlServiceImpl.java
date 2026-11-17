package com.loop.new_loop_api.stockcontrols.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.common.exception.UniqueConstraintViolation;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.integrations.aguas.metrics.AguasControlMetrics;
import com.loop.new_loop_api.performance.service.OperationEventRecorder;
import com.loop.new_loop_api.users.entity.Role;
import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.branches.exception.BranchNotFoundException;
import com.loop.new_loop_api.branches.repository.BranchRepository;
import com.loop.new_loop_api.products.entity.Product;
import com.loop.new_loop_api.products.exception.ProductNotFoundException;
import com.loop.new_loop_api.products.repository.ProductRepository;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.exception.RouteNotFoundException;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import com.loop.new_loop_api.stockcontrols.dto.ArrivalsSummaryResponse;
import com.loop.new_loop_api.stockcontrols.dto.CorrectStockControlRequest;
import com.loop.new_loop_api.stockcontrols.dto.CreateStockControlItemRequest;
import com.loop.new_loop_api.stockcontrols.dto.CreateStockControlRequest;
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
import com.loop.new_loop_api.stockcontrols.exception.DuplicateControlException;
import com.loop.new_loop_api.stockcontrols.exception.ExitControlNotFoundException;
import com.loop.new_loop_api.stockcontrols.exception.InactiveProductException;
import com.loop.new_loop_api.stockcontrols.exception.InvalidControlStatusException;
import com.loop.new_loop_api.stockcontrols.exception.RemitoBatchNotAvailableException;
import com.loop.new_loop_api.stockcontrols.exception.RemitoNotAvailableException;
import com.loop.new_loop_api.stockcontrols.exception.StockControlNotCorrectableException;
import com.loop.new_loop_api.stockcontrols.exception.StockControlNotFoundException;
import com.loop.new_loop_api.stockcontrols.exception.StockControlNotModifiableException;
import com.loop.new_loop_api.stockcontrols.mapper.StockControlMapper;
import com.loop.new_loop_api.stockcontrols.pdf.RemitoPdfGenerator;
import com.loop.new_loop_api.stockcontrols.pdf.RemitoPdfGenerator.RemitoPage;
import com.loop.new_loop_api.holidays.service.iService.HolidayService;
import com.loop.new_loop_api.stockcontrols.repository.StockControlRepository;
import com.loop.new_loop_api.stockcontrols.service.iService.StockControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.loop.new_loop_api.stockcontrols.repository.StockControlSpecification.withFilters;

@Service
@RequiredArgsConstructor
public class StockControlServiceImpl implements StockControlService {

    private static final Set<ControlStatus> CORRECTABLE_STATUSES =
            Set.of(ControlStatus.SENT_TO_AGUAS, ControlStatus.AGUAS_ERROR);

    private static final Set<Role> CORRECTION_ROLES = Set.of(Role.SUPERVISOR, Role.ADMIN);

    // The driver is the one checking the control, so they must never be able to change its numbers.
    private static final Set<Role> EDIT_ROLES = Set.of(Role.CONTROLADOR, Role.SUPERVISOR, Role.ADMIN);

    // The controller can still change a control until the driver accepts it; after that it belongs to Aguas.
    private static final Set<ControlStatus> MODIFIABLE_STATUSES = Set.of(
            ControlStatus.CONTROLLED, ControlStatus.PENDING_DRIVER_APPROVAL, ControlStatus.REJECTED_BY_DRIVER);

    private static final String CORRECT_STOCK_CONTROL_ACTION = "CORRECT_STOCK_CONTROL";
    private static final String REJECT_STOCK_CONTROL_ACTION  = "REJECT_STOCK_CONTROL";
    // Partial unique index from V30; must match the migration's name.
    private static final String ACTIVE_CONTROL_UNIQUE_INDEX  = "uq_stock_controls_type_route_date_active";

    private final StockControlRepository    stockControlRepository;
    private final StockControlMapper        stockControlMapper;
    private final BranchRepository          branchRepository;
    private final RouteRepository           routeRepository;
    private final ProductRepository         productRepository;
    private final AuditService              auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final RemitoPdfGenerator        remitoPdfGenerator;
    private final CurrentUserProvider       currentUserProvider;
    private final AguasControlMetrics       aguasControlMetrics;
    private final OperationEventRecorder    operationEventRecorder;
    private final HolidayService            holidayService;

    
    @Override
    @Transactional
    public StockControlResponse createControl(CreateStockControlRequest request) {
        var branch   = findBranchById(request.getBranchId());
        var route    = findRouteById(request.getRouteId());
        var date     = resolveControlDate(request.getType(), request.getControlDate());
        if (stockControlRepository.existsByTypeAndRouteIdAndControlDateAndStatusNot(
                request.getType(), route.getId(), date, ControlStatus.CANCELLED)) {
            throw new DuplicateControlException(request.getType(), route.getId(), date);
        }
        var control  = stockControlMapper.toEntity(request, branch, route, date);
        control.getItems().addAll(buildItems(request.getItems(), control));
        if (request.getType() == ControlType.ENTRY) {
            control.setStatus(ControlStatus.PENDING_DRIVER_APPROVAL);
            control.setConfirmedAt(LocalDateTime.now());
        }
        var saved    = saveAndFlushControl(control);
        var response = stockControlMapper.toResponse(saved);
        auditService.register("CREATE_STOCK_CONTROL", "StockControl", saved.getId(), null, response);
        operationEventRecorder.controlSubmitted(saved);
        if (saved.getType() == ControlType.EXIT) {
            eventPublisher.publishEvent(new StockControlReadyForAguasEvent(saved.getId()));
        }
        return response;
    }


    @Override
    @Transactional(readOnly = true)
    public Page<StockControlResponse> getAllControls(
            ControlType type, ControlStatus status, UUID routeId, UUID controllerId, UUID branchId,
            LocalDate from, LocalDate to, LocalDate createdFrom, LocalDate createdTo, Pageable pageable) {
        var scopedBranchId     = scopeBranchId(branchId);
        var scopedControllerId = scopeControllerId(controllerId);
        return stockControlRepository
                .findAll(withFilters(type, status, routeId, scopedControllerId, scopedBranchId, from, to, createdFrom, createdTo), pageable)
                .map(stockControlMapper::toResponse);
    }


    @Override
    @Transactional(readOnly = true)
    public StockControlResponse getControlById(UUID id) {
        var control = findControlById(id);
        if (!isVisibleToReadOnlyUser(control)) {
            throw new StockControlNotFoundException(id);
        }
        return stockControlMapper.toResponse(control);
    }

    /** Read-only roles only see their own branch; "not found" so other branches' ids are not revealed. */
    private boolean isVisibleToReadOnlyUser(StockControl control) {
        return currentUserProvider.current()
                .filter(user -> user.role().isReadOnly())
                .map(user -> control.getBranch() != null && control.getBranch().getId().equals(user.branchId()))
                .orElse(true);
    }

    @Override
    @Transactional
    public StockControlResponse updateControl(UUID id, UpdateStockControlRequest request) {
        assertCurrentUserCanEdit();
        var control = findControlByIdForUpdate(id);
        if (!MODIFIABLE_STATUSES.contains(control.getStatus())) {
            throw new StockControlNotModifiableException(id, control.getStatus());
        }
        var oldValue = stockControlMapper.toResponse(control);
        validateNoOtherActiveControl(control, targetDate(request, control));
        stockControlMapper.updateEntity(request, control);
        if (request.getItems() != null) {
            replaceItems(control, request.getItems());
        }
        var resubmission = control.getStatus() == ControlStatus.REJECTED_BY_DRIVER;
        if (resubmission) {
            sendBackToDriver(control);
        }
        var saved    = saveAndFlushControl(control);
        var response = stockControlMapper.toResponse(saved);
        auditService.register("UPDATE_STOCK_CONTROL", "StockControl", saved.getId(), oldValue, response);
        operationEventRecorder.controlEdited(saved, resubmission);
        return response;
    }

    @Override
    @Transactional
    public StockControlResponse approveControl(UUID id) {
        var control = findControlByIdForUpdate(id);
        if (control.getStatus() != ControlStatus.PENDING_DRIVER_APPROVAL) {
            throw new InvalidControlStatusException(id, ControlStatus.PENDING_DRIVER_APPROVAL, control.getStatus());
        }
        control.setStatus(ControlStatus.ACCEPTED_BY_DRIVER);
        control.setApprovedAt(LocalDateTime.now());
        var saved    = stockControlRepository.save(control);
        var response = stockControlMapper.toResponse(saved);
        auditService.register("APPROVE_STOCK_CONTROL", "StockControl", saved.getId(), null, response);
        operationEventRecorder.controlApproved(saved);
        eventPublisher.publishEvent(new StockControlReadyForAguasEvent(saved.getId()));
        return response;
    }

    @Override
    @Transactional
    public StockControlResponse rejectControl(UUID id, RejectStockControlRequest request) {
        var control = findControlByIdForUpdate(id);
        if (control.getStatus() != ControlStatus.PENDING_DRIVER_APPROVAL) {
            throw new InvalidControlStatusException(id, ControlStatus.PENDING_DRIVER_APPROVAL, control.getStatus());
        }
        control.setStatus(ControlStatus.REJECTED_BY_DRIVER);
        control.setDriverRejectionReason(request.getReason().trim());
        var saved    = stockControlRepository.save(control);
        var response = stockControlMapper.toResponse(saved);
        auditService.register(REJECT_STOCK_CONTROL_ACTION, "StockControl", saved.getId(), null, response, control.getDriverRejectionReason());
        operationEventRecorder.controlRejected(saved);
        return response;
    }

    @Override
    @Transactional
    public StockControlResponse correctControl(UUID id, CorrectStockControlRequest request) {
        assertCurrentUserCanCorrect();
        var control = findControlById(id);
        assertControlIsCorrectable(control);

        var oldValue = stockControlMapper.toResponse(control);
        applyCorrection(control, request);
        var saved    = stockControlRepository.save(control);
        var response = stockControlMapper.toResponse(saved);

        auditService.register(CORRECT_STOCK_CONTROL_ACTION, "StockControl", saved.getId(), oldValue, response, request.getReason());
        operationEventRecorder.controlCorrected(saved, request.getReason());
        eventPublisher.publishEvent(new StockControlCorrectedEvent(saved.getId()));
        aguasControlMetrics.recordCorrected();
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ArrivalsSummaryResponse getPendingArrivals(LocalDate date, UUID branchId) {
        var targetDate      = date != null ? date : LocalDate.now();
        var scopedBranchId  = scopeBranchId(branchId);
        var exits           = stockControlRepository.findControlsForDate(ControlType.EXIT, targetDate, ControlStatus.CANCELLED, scopedBranchId);
        var arrivedRouteIds = stockControlRepository.findRouteIdsForDate(ControlType.ENTRY, targetDate, ControlStatus.CANCELLED);
        var pending         = exits.stream()
                .filter(exit -> !arrivedRouteIds.contains(exit.getRoute().getId()))
                .map(stockControlMapper::toPendingArrival)
                .toList();
        return ArrivalsSummaryResponse.builder()
                .date(targetDate)
                .totalExpected(exits.size())
                .arrived(exits.size() - pending.size())
                .pending(pending.size())
                .pendingRoutes(pending)
                .build();
    }


    @Override
    @Transactional(readOnly = true)
    public byte[] generateRemitoPdf(UUID id) {
        return buildRemito(findControlById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateRemitoPdfByRouteAndDate(UUID routeId, LocalDate date) {
        var control = stockControlRepository
                .findByTypeAndRouteIdAndControlDateAndStatusNot(ControlType.EXIT, routeId, date, ControlStatus.CANCELLED)
                .orElseThrow(() -> new ExitControlNotFoundException(routeId, date));
        return buildRemito(control);
    }


    @Override
    @Transactional
    public StockControlResponse markRemitoPrinted(UUID id) {
        var control = findControlById(id);
        assertRemitoAvailable(control);
        control.setRemitoPrinted(true);
        return stockControlMapper.toResponse(stockControlRepository.save(control));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateRemitosPdf(List<UUID> ids) {
        var controls = findRemitoBatch(ids);
        var holidays = holidayService.holidaysAmong(controls.stream().map(StockControl::getControlDate).distinct().toList());
        return remitoPdfGenerator.generate(controls.stream()
                .map(control -> new RemitoPage(control, holidays.contains(control.getControlDate())))
                .toList());
    }

    @Override
    @Transactional
    public List<StockControlResponse> markRemitosPrinted(List<UUID> ids) {
        var controls = findRemitoBatch(ids);
        controls.forEach(control -> control.setRemitoPrinted(true));
        return stockControlRepository.saveAll(controls).stream().map(stockControlMapper::toResponse).toList();
    }

    /** The batch in the requested order; one missing or unavailable remito fails the whole batch. */
    private List<StockControl> findRemitoBatch(List<UUID> ids) {
        var uniqueIds = new LinkedHashSet<>(ids);
        var byId = stockControlRepository.findAllById(uniqueIds).stream()
                .collect(Collectors.toMap(StockControl::getId, control -> control));
        var failures = new ArrayList<String>();
        var controls = new ArrayList<StockControl>();
        for (var id : uniqueIds) {
            var control = byId.get(id);
            if (control == null) {
                failures.add("control " + id + ": not found");
                continue;
            }
            var reason = remitoUnavailableReason(control);
            if (reason != null) {
                failures.add("route " + control.getRoute().getCode() + ": " + reason);
                continue;
            }
            controls.add(control);
        }
        if (!failures.isEmpty()) {
            throw new RemitoBatchNotAvailableException(failures);
        }
        return controls;
    }

    // The holiday is looked up at render time, so a holiday added late also changes reprints (spec 005, FR-7).
    private byte[] buildRemito(StockControl control) {
        assertRemitoAvailable(control);
        var date = control.getControlDate();
        var holiday = holidayService.holidaysAmong(List.of(date)).contains(date);
        return remitoPdfGenerator.generate(control, holiday);
    }

    private void assertRemitoAvailable(StockControl control) {
        var reason = remitoUnavailableReason(control);
        if (reason != null) {
            throw new RemitoNotAvailableException(control.getId(), reason);
        }
    }

    private String remitoUnavailableReason(StockControl control) {
        if (control.getType() != ControlType.EXIT) {
            return "remitos are only generated for EXIT controls";
        }
        if (control.getAguasFormulario() == null || control.getAguasNroRemito() == null) {
            return "the control has not been confirmed with Aguas yet";
        }
        return null;
    }

    /** A corrected rejection goes back to the driver's pending list; the reason lives on in the audit log. */
    private void sendBackToDriver(StockControl control) {
        control.setStatus(ControlStatus.PENDING_DRIVER_APPROVAL);
        control.setConfirmedAt(LocalDateTime.now());
        control.setDriverRejectionReason(null);
    }

    private void assertCurrentUserCanEdit() {
        var canEdit = currentUserProvider.current()
                .map(user -> EDIT_ROLES.contains(user.role()))
                .orElse(false);
        if (!canEdit) {
            throw new ControlEditNotAllowedException();
        }
    }

    private void assertCurrentUserCanCorrect() {
        var canCorrect = currentUserProvider.current()
                .map(user -> CORRECTION_ROLES.contains(user.role()))
                .orElse(false);
        if (!canCorrect) {
            throw new CorrectionNotAllowedException();
        }
    }

    private void assertControlIsCorrectable(StockControl control) {
        if (control.getType() != ControlType.ENTRY) {
            throw new StockControlNotCorrectableException(control.getId(), control.getType());
        }
        if (!CORRECTABLE_STATUSES.contains(control.getStatus())) {
            throw new StockControlNotCorrectableException(control.getId(), control.getStatus());
        }
    }

    private void applyCorrection(StockControl control, CorrectStockControlRequest request) {
        if (request.getObservations() != null) control.setObservations(request.getObservations());
        if (request.getTruckOrdered() != null) control.setTruckOrdered(request.getTruckOrdered());
        if (request.getItems() != null) {
            replaceItems(control, request.getItems());
        }
    }

    /** Rebuilds the control's items from scratch: clears and flushes before re-adding to avoid stale rows. */
    private void replaceItems(StockControl control, List<CreateStockControlItemRequest> items) {
        control.getItems().clear();
        saveAndFlushControl(control);
        control.getItems().addAll(buildItems(items, control));
    }

    /** Branch-scoped roles only see their own branch: their branch always wins over any requested filter. */
    private UUID scopeBranchId(UUID requestedBranchId) {
        return currentUserProvider.current()
                .filter(user -> user.role().isBranchScoped())
                .map(user -> user.branchId())
                .orElse(requestedBranchId);
    }

    /** Controllers only list the controls they created; the rest of the roles keep the requested filter. */
    private UUID scopeControllerId(UUID requestedControllerId) {
        return currentUserProvider.current()
                .filter(user -> user.role() == Role.CONTROLADOR)
                .map(AuthenticatedUser::id)
                .orElse(requestedControllerId);
    }

    /** Products not covered by the request are added with quantity 0, so the control (and Aguas) always sees the full catalog. */
    private List<StockControlItem> buildItems(List<CreateStockControlItemRequest> requests, StockControl control) {
        var items = new ArrayList<StockControlItem>();
        var coveredProductIds = new HashSet<UUID>();

        for (var req : requests) {
            var product = findProductById(req.getProductId());
            if (!product.getActive()) {
                throw new InactiveProductException(product.getCode());
            }
            items.add(stockControlMapper.toItem(req, product, control));
            coveredProductIds.add(product.getId());
        }

        productRepository.findByActiveTrue().stream()
                .filter(product -> !coveredProductIds.contains(product.getId()))
                .map(product -> stockControlMapper.toZeroItem(product, control))
                .forEach(items::add);

        return items;
    }

    private LocalDate resolveControlDate(ControlType type, LocalDate requested) {
        if (requested != null) return requested;
        if (type == ControlType.ENTRY) return LocalDate.now();
        var tomorrow = LocalDate.now().plusDays(1);
        return tomorrow.getDayOfWeek() == DayOfWeek.SUNDAY ? tomorrow.plusDays(1) : tomorrow;
    }

    private StockControl findControlById(UUID id) {
        return stockControlRepository.findById(id)
                .orElseThrow(() -> new StockControlNotFoundException(id));
    }

    private StockControl findControlByIdForUpdate(UUID id) {
        return stockControlRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new StockControlNotFoundException(id));
    }

    private static LocalDate targetDate(UpdateStockControlRequest request, StockControl control) {
        return request.getControlDate() != null ? request.getControlDate() : control.getControlDate();
    }

    /**
     * An edit can move the control to another date: it must not land on one that already has an active control.
     * Runs before the entity is modified, otherwise the query's auto-flush would hit the unique index first.
     */
    private void validateNoOtherActiveControl(StockControl control, LocalDate date) {
        if (stockControlRepository.existsByTypeAndRouteIdAndControlDateAndStatusNotAndIdNot(
                control.getType(), control.getRoute().getId(), date, ControlStatus.CANCELLED, control.getId())) {
            throw new DuplicateControlException(control.getType(), control.getRoute().getId(), date);
        }
    }

    /** Flushes so a concurrent duplicate that slipped past the exists() checks surfaces here as a 409. */
    private StockControl saveAndFlushControl(StockControl control) {
        try {
            return stockControlRepository.saveAndFlush(control);
        } catch (DataIntegrityViolationException ex) {
            if (UniqueConstraintViolation.isCausedBy(ex, ACTIVE_CONTROL_UNIQUE_INDEX)) {
                throw new DuplicateControlException(control.getType(), control.getRoute().getId(), control.getControlDate());
            }
            throw ex;
        }
    }

    private Branch findBranchById(UUID id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException(id));
    }

    private Route findRouteById(UUID id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new RouteNotFoundException(id));
    }

    private Product findProductById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
