package com.loop.new_loop_api.products.service.iService;

import com.loop.new_loop_api.products.dto.CreateProductRequest;
import com.loop.new_loop_api.products.dto.ProductResponse;
import com.loop.new_loop_api.products.dto.UpdateProductRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface ProductService {

    ProductResponse createProduct(CreateProductRequest request);
    Page<ProductResponse> getAllProducts(Pageable pageable);
    ProductResponse getProductById(UUID id);
    ProductResponse updateProduct(UUID id, UpdateProductRequest request);
    void deactivateProduct(UUID id);
    void activateProduct(UUID id);
}
