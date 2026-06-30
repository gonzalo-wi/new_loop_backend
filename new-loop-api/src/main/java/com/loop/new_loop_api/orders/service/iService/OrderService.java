package com.loop.new_loop_api.orders.service.iService;

import com.loop.new_loop_api.orders.dto.CreateOrderRequest;
import com.loop.new_loop_api.orders.dto.OrderResponse;
import com.loop.new_loop_api.orders.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request);
    Page<OrderResponse> getAllOrders(UUID routeId, OrderStatus status, LocalDate from, LocalDate to, Pageable pageable);
    OrderResponse getOrderById(UUID id);
    OrderResponse startOrder(UUID id);
    OrderResponse completeOrder(UUID id);
}
