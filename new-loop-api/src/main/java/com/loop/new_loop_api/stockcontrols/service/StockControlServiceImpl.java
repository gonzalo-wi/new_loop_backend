package com.loop.new_loop_api.stockcontrols.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
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
import com.loop.new_loop_api.stockcontrols.exception.InactiveProductException;
import com.loop.new_loop_api.stockcontrols.exception.InvalidControlStatusException;
import com.loop.new_loop_api.stockcontrols.exception.StockControlNotFoundException;
import com.loop.new_loop_api.stockcontrols.exception.StockControlNotModifiableException;
import com.loop.new_loop_api.stockcontrols.mapper.StockControlMapper;
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
import java.util.List;
import java.util.UUID;

import static com.loop.new_loop_api.stockcontrols.repository.StockControlSpecification.withFilters;

@Service
@RequiredArgsConstructor
public class StockControlServiceImpl implements StockControlService {

    private final StockControlRepository stockControlRepository;
    private final StockControlMapper     stockControlMapper;
    private final BranchRepository       branchRepository;
    private final RouteRepository        routeRepository;
    private final ProductRepository      productRepository;
    private final AuditService           auditService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public StockControlResponse createControl(CreateStockControlRequest request) {
        var branch   = findBranchById(request.getBranchId());
        var route    = findRouteById(request.getRouteId());
        var date     = resolveControlDate(request.getType(), request.getControlDate());

        // A route can only have one active control of each type (ENTRY / EXIT) per day.
        if (stockControlRepository.existsByTypeAndRouteIdAndControlDateAndStatusNot(
                request.getType(), route.getId(), date, ControlStatus.CANCELLED)) {
            throw new DuplicateControlException(request.getType(), route.getId(), date);
        }

        var control  = stockControlMapper.toEntity(request, branch, route, date);
        control.getItems().addAll(buildItems(request.getItems(), control));

        // ENTRY controls are sent to the driver for approval as soon as they are created;
        // EXIT controls stay as CONTROLLED and are not shown to the driver.
        if (request.getType() == ControlType.ENTRY) {
            control.setStatus(ControlStatus.PENDING_DRIVER_APPROVAL);
            control.setConfirmedAt(LocalDateTime.now());
        }

        var saved    = stockControlRepository.save(control);
        var response = stockControlMapper.toResponse(saved);
        auditService.register("CREATE_STOCK_CONTROL", "StockControl", saved.getId(), null, response);

        // EXIT controls are sent to Aguas as soon as they are created (no driver approval needed).
        if (saved.getType() == ControlType.EXIT) {
            eventPublisher.publishEvent(new StockControlReadyForAguasEvent(saved.getId()));
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockControlResponse> getAllControls(
            ControlType type, ControlStatus status, UUID routeId, UUID controllerId,
            LocalDate from, LocalDate to, Pageable pageable) {
        return stockControlRepository
                .findAll(withFilters(type, status, routeId, controllerId, from, to), pageable)
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

        // Once the driver approves an ENTRY control it is sent to Aguas.
        eventPublisher.publishEvent(new StockControlReadyForAguasEvent(saved.getId()));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ArrivalsSummaryResponse getPendingArrivals(LocalDate date) {
        var targetDate = date != null ? date : LocalDate.now();

        var exits           = stockControlRepository.findControlsForDate(ControlType.EXIT, targetDate, ControlStatus.CANCELLED);
        var arrivedRouteIds = stockControlRepository.findRouteIdsForDate(ControlType.ENTRY, targetDate, ControlStatus.CANCELLED);

        var pending = exits.stream()
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

    private List<StockControlItem> buildItems(List<CreateStockControlItemRequest> requests, StockControl control) {
        return requests.stream()
                .map(req -> {
                    var product = findProductById(req.getProductId());
                    if (!product.getActive()) {
                        throw new InactiveProductException(product.getCode());
                    }
                    return stockControlMapper.toItem(req, product, control);
                })
                .toList();
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
