package com.loop.new_loop_api.orders.service.iService;

import com.loop.new_loop_api.orders.dto.CreateOrderableProductRequest;
import com.loop.new_loop_api.orders.dto.OrderableProductResponse;
import com.loop.new_loop_api.orders.dto.UpdateOrderableProductRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderableProductService {

    OrderableProductResponse createProduct(CreateOrderableProductRequest request);
    Page<OrderableProductResponse> getAllProducts(Boolean active, Pageable pageable);
    OrderableProductResponse getProductById(UUID id);
    OrderableProductResponse updateProduct(UUID id, UpdateOrderableProductRequest request);
    void deactivateProduct(UUID id);
    void activateProduct(UUID id);
}
