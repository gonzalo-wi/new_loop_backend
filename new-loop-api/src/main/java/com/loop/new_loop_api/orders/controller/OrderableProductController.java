package com.loop.new_loop_api.orders.controller;

import com.loop.new_loop_api.common.response.ApiResponse;
import com.loop.new_loop_api.orders.dto.CreateOrderableProductRequest;
import com.loop.new_loop_api.orders.dto.OrderableProductResponse;
import com.loop.new_loop_api.orders.dto.UpdateOrderableProductRequest;
import com.loop.new_loop_api.orders.service.iService.OrderableProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orderable-products")
@RequiredArgsConstructor
public class OrderableProductController {

    private final OrderableProductService orderableProductService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderableProductResponse>> createProduct(
            @Valid @RequestBody CreateOrderableProductRequest request) {
        var response = orderableProductService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Orderable product created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderableProductResponse>>> getAllProducts(
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(orderableProductService.getAllProducts(active, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderableProductResponse>> getProductById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderableProductService.getProductById(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderableProductResponse>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderableProductRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(orderableProductService.updateProduct(id, request)));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateProduct(@PathVariable UUID id) {
        orderableProductService.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateProduct(@PathVariable UUID id) {
        orderableProductService.activateProduct(id);
        return ResponseEntity.noContent().build();
    }
}
