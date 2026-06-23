package com.loop.new_loop_api.products.mapper;

import com.loop.new_loop_api.products.dto.CreateProductRequest;
import com.loop.new_loop_api.products.dto.ProductResponse;
import com.loop.new_loop_api.products.dto.UpdateProductRequest;
import com.loop.new_loop_api.products.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public Product toEntity(CreateProductRequest request) {
        return Product.builder()
                .code(request.getCode())
                .name(request.getName())
                .displayOrder(request.getDisplayOrder())
                .description(request.getDescription())
                .type(request.getType())
                .unit(request.getUnit())
                .packQuantity(request.getPackQuantity())
                .active(true)
                .build();
    }

    public ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .code(product.getCode())
                .name(product.getName())
                .displayOrder(product.getDisplayOrder())
                .description(product.getDescription())
                .type(product.getType())
                .unit(product.getUnit())
                .packQuantity(product.getPackQuantity())
                .active(product.getActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public void updateEntity(UpdateProductRequest request, Product product) {
        if (request.getName() != null)         product.setName(request.getName());
        if (request.getDisplayOrder() != null) product.setDisplayOrder(request.getDisplayOrder());
        if (request.getDescription() != null)  product.setDescription(request.getDescription());
        if (request.getType() != null)         product.setType(request.getType());
        if (request.getUnit() != null)         product.setUnit(request.getUnit());
        if (request.getPackQuantity() != null) product.setPackQuantity(request.getPackQuantity());
    }
}
