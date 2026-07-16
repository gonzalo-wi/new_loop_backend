package com.loop.new_loop_api.orders.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.orders.dto.CreateOrderItemRequest;
import com.loop.new_loop_api.orders.dto.CreateOrderRequest;
import com.loop.new_loop_api.orders.dto.OrderResponse;
import com.loop.new_loop_api.orders.entity.Order;
import com.loop.new_loop_api.orders.entity.OrderItem;
import com.loop.new_loop_api.orders.entity.OrderStatus;
import com.loop.new_loop_api.orders.entity.OrderableProduct;
import com.loop.new_loop_api.orders.exception.InvalidOrderStatusException;
import com.loop.new_loop_api.orders.exception.OrderNotFoundException;
import com.loop.new_loop_api.orders.exception.OrderQuantityNotAllowedException;
import com.loop.new_loop_api.orders.exception.OrderableProductNotFoundException;
import com.loop.new_loop_api.orders.mapper.OrderMapper;
import com.loop.new_loop_api.orders.repository.OrderRepository;
import com.loop.new_loop_api.orders.repository.OrderableProductRepository;
import com.loop.new_loop_api.orders.service.iService.OrderService;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.exception.RouteNotFoundException;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository            orderRepository;
    private final OrderMapper                orderMapper;
    private final RouteRepository            routeRepository;
    private final OrderableProductRepository orderableProductRepository;
    private final AuditService               auditService;
    private final CurrentUserProvider        currentUserProvider;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        var route = findRouteById(request.getRouteId());
        var date  = request.getOrderDate() != null ? request.getOrderDate() : LocalDate.now();
        var order = orderMapper.toEntity(request, route, date);
        order.getItems().addAll(buildItems(request.getItems(), order));
        return saveAndAudit(order, "CREATE_ORDER");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(UUID routeId, OrderStatus status, LocalDate from, LocalDate to, Pageable pageable) {
        Specification<Order> spec = (r, q, cb) -> cb.conjunction();
        if (routeId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("route").get("id"), routeId));
        if (status  != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (from    != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.<LocalDate>get("orderDate"), from));
        if (to      != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.<LocalDate>get("orderDate"), to));
        return orderRepository.findAll(spec, pageable).map(orderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        return orderMapper.toResponse(findOrderById(id));
    }

    @Override
    @Transactional
    public OrderResponse startOrder(UUID id) {
        var order = findOrderById(id);
        ensureStatus(order, OrderStatus.PENDING);
        order.setStatus(OrderStatus.IN_PROGRESS);
        currentUserProvider.current().ifPresent(user -> {
            order.setStartedBy(user.id());
            order.setStartedByName(user.name());
        });
        order.setStartedAt(LocalDateTime.now());
        return saveAndAudit(order, "START_ORDER");
    }

    @Override
    @Transactional
    public OrderResponse completeOrder(UUID id) {
        var order = findOrderById(id);
        ensureStatus(order, OrderStatus.IN_PROGRESS);
        order.setStatus(OrderStatus.COMPLETED);
        currentUserProvider.current().ifPresent(user -> {
            order.setCompletedBy(user.id());
            order.setCompletedByName(user.name());
        });
        order.setCompletedAt(LocalDateTime.now());
        return saveAndAudit(order, "COMPLETE_ORDER");
    }



    
    private void ensureStatus(Order order, OrderStatus expected) {
        if (order.getStatus() != expected) {
            throw new InvalidOrderStatusException(order.getId(), expected, order.getStatus());
        }
    }

    private OrderResponse saveAndAudit(Order order, String action) {
        var saved    = orderRepository.save(order);
        var response = orderMapper.toResponse(saved);
        auditService.register(action, "Order", saved.getId(), null, response);
        return response;
    }

    private List<OrderItem> buildItems(List<CreateOrderItemRequest> requests, Order order) {
        return requests.stream()
                .map(req -> {
                    var product = findProductById(req.getProductId());
                    validateQuantities(req, product);
                    return orderMapper.toItem(req, product, order);
                })
                .toList();
    }

    private void validateQuantities(CreateOrderItemRequest req, OrderableProduct product) {
        boolean hasUnit = req.getUnitQuantity() != null && req.getUnitQuantity() > 0;
        boolean hasBulk = req.getBulkQuantity() != null && req.getBulkQuantity() > 0;
        if (!hasUnit && !hasBulk) {
            throw new OrderQuantityNotAllowedException(product.getCode(),
                    "at least one of unitQuantity or bulkQuantity must be greater than 0");
        }
        if (hasUnit && !product.getAllowsUnit()) {
            throw new OrderQuantityNotAllowedException(product.getCode(),
                    "does not allow ordering by unit");
        }
        if (hasBulk && !product.getAllowsBulk()) {
            throw new OrderQuantityNotAllowedException(product.getCode(),
                    "does not allow ordering by bulk");
        }
    }

    private Order findOrderById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private Route findRouteById(UUID id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new RouteNotFoundException(id));
    }

    private OrderableProduct findProductById(UUID id) {
        return orderableProductRepository.findById(id)
                .orElseThrow(() -> new OrderableProductNotFoundException(id));
    }
}
