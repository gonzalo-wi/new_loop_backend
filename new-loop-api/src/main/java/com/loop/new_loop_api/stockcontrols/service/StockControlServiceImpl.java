package com.loop.new_loop_api.stockcontrols.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
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
import com.loop.new_loop_api.stockcontrols.dto.CreateStockControlItemRequest;
import com.loop.new_loop_api.stockcontrols.dto.CreateStockControlRequest;
import com.loop.new_loop_api.stockcontrols.dto.StockControlResponse;
import com.loop.new_loop_api.stockcontrols.dto.UpdateStockControlRequest;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.entity.StockControlItem;
import com.loop.new_loop_api.stockcontrols.event.StockControlReadyForAguasEvent;
import com.loop.new_loop_api.stockcontrols.exception.DuplicateControlException;
import com.loop.new_loop_api.stockcontrols.exception.ExitControlNotFoundException;
import com.loop.new_loop_api.stockcontrols.exception.InactiveProductException;
import com.loop.new_loop_api.stockcontrols.exception.InvalidControlStatusException;
import com.loop.new_loop_api.stockcontrols.exception.RemitoNotAvailableException;
import com.loop.new_loop_api.stockcontrols.exception.StockControlNotFoundException;
import com.loop.new_loop_api.stockcontrols.exception.StockControlNotModifiableException;
import com.loop.new_loop_api.stockcontrols.mapper.StockControlMapper;
import com.loop.new_loop_api.stockcontrols.pdf.RemitoPdfGenerator;
import com.loop.new_loop_api.stockcontrols.repository.StockControlRepository;
import com.loop.new_loop_api.stockcontrols.service.iService.StockControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static com.loop.new_loop_api.stockcontrols.repository.StockControlSpecification.withFilters;

@Service
@RequiredArgsConstructor
public class StockControlServiceImpl implements StockControlService {

    private final StockControlRepository    stockControlRepository;
    private final StockControlMapper        stockControlMapper;
    private final BranchRepository          branchRepository;
    private final RouteRepository           routeRepository;
    private final ProductRepository         productRepository;
    private final AuditService              auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final RemitoPdfGenerator        remitoPdfGenerator;
    private final CurrentUserProvider       currentUserProvider;

    
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
        var saved    = stockControlRepository.save(control);
        var response = stockControlMapper.toResponse(saved);
        auditService.register("CREATE_STOCK_CONTROL", "StockControl", saved.getId(), null, response);
        if (saved.getType() == ControlType.EXIT) {
            eventPublisher.publishEvent(new StockControlReadyForAguasEvent(saved.getId()));
        }
        return response;
    }


    @Override
    @Transactional(readOnly = true)
    public Page<StockControlResponse> getAllControls(
            ControlType type, ControlStatus status, UUID routeId, UUID controllerId, UUID branchId,
            LocalDate from, LocalDate to, Pageable pageable) {
        var scopedBranchId = scopeBranchId(branchId);
        return stockControlRepository
                .findAll(withFilters(type, status, routeId, controllerId, scopedBranchId, from, to), pageable)
                .map(stockControlMapper::toResponse);
    }


    @Override
    @Transactional(readOnly = true)
    public StockControlResponse getControlById(UUID id) {
        return stockControlMapper.toResponse(findControlById(id));
    }

    @Override
    @Transactional
    public StockControlResponse updateControl(UUID id, UpdateStockControlRequest request) {
        var control = findControlById(id);
        if (control.getStatus() != ControlStatus.CONTROLLED
                && control.getStatus() != ControlStatus.PENDING_DRIVER_APPROVAL) {
            throw new StockControlNotModifiableException(id, control.getStatus());
        }
        var oldValue = stockControlMapper.toResponse(control);
        stockControlMapper.updateEntity(request, control);
        if (request.getItems() != null) {
            control.getItems().clear();
            stockControlRepository.saveAndFlush(control);
            control.getItems().addAll(buildItems(request.getItems(), control));
        }
        var saved    = stockControlRepository.save(control);
        var response = stockControlMapper.toResponse(saved);
        auditService.register("UPDATE_STOCK_CONTROL", "StockControl", saved.getId(), oldValue, response);
        return response;
    }

    @Override
    @Transactional
    public StockControlResponse approveControl(UUID id) {
        var control = findControlById(id);
        if (control.getStatus() != ControlStatus.PENDING_DRIVER_APPROVAL) {
            throw new InvalidControlStatusException(id, ControlStatus.PENDING_DRIVER_APPROVAL, control.getStatus());
        }
        control.setStatus(ControlStatus.ACCEPTED_BY_DRIVER);
        control.setApprovedAt(LocalDateTime.now());
        var saved    = stockControlRepository.save(control);
        var response = stockControlMapper.toResponse(saved);
        auditService.register("APPROVE_STOCK_CONTROL", "StockControl", saved.getId(), null, response);
        eventPublisher.publishEvent(new StockControlReadyForAguasEvent(saved.getId()));
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


    private byte[] buildRemito(StockControl control) {
        if (control.getType() != ControlType.EXIT) {
            throw new RemitoNotAvailableException(control.getId(), "remitos are only generated for EXIT controls");
        }
        if (control.getAguasFormulario() == null || control.getAguasNroRemito() == null) {
            throw new RemitoNotAvailableException(control.getId(), "the control has not been confirmed with Aguas yet");
        }
        return remitoPdfGenerator.generate(control);
    }

    /** CONTROLADOR/PICKER only see their own branch: their branch always wins over any requested filter. */
    private UUID scopeBranchId(UUID requestedBranchId) {
        return currentUserProvider.current()
                .filter(user -> user.role() == Role.CONTROLADOR || user.role() == Role.PICKER)
                .map(user -> user.branchId())
                .orElse(requestedBranchId);
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
