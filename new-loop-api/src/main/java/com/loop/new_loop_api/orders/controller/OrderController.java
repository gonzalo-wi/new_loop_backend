package com.loop.new_loop_api.orders.controller;

import com.loop.new_loop_api.common.response.ApiResponse;
import com.loop.new_loop_api.orders.dto.CreateOrderRequest;
import com.loop.new_loop_api.orders.dto.OrderResponse;
import com.loop.new_loop_api.orders.entity.OrderStatus;
import com.loop.new_loop_api.orders.service.iService.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        var response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Order created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getAllOrders(
            @RequestParam(required = false) UUID routeId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = orderService.getAllOrders(routeId, status, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrderById(id)));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<OrderResponse>> startOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.startOrder(id), "Order started successfully"));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<OrderResponse>> completeOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.completeOrder(id), "Order completed successfully"));
    }
}
