package com.loop.new_loop_api.orders.mapper;

import com.loop.new_loop_api.orders.dto.CreateOrderableProductRequest;
import com.loop.new_loop_api.orders.dto.OrderableProductResponse;
import com.loop.new_loop_api.orders.dto.UpdateOrderableProductRequest;
import com.loop.new_loop_api.orders.entity.OrderableProduct;
import org.springframework.stereotype.Component;

@Component
public class OrderableProductMapper {

    public OrderableProduct toEntity(CreateOrderableProductRequest request) {
        return OrderableProduct.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .allowsUnit(request.getAllowsUnit() != null ? request.getAllowsUnit() : true)
                .allowsBulk(request.getAllowsBulk() != null ? request.getAllowsBulk() : false)
                .unitsPerBulk(request.getUnitsPerBulk())
                .build();
    }

    public void updateEntity(UpdateOrderableProductRequest request, OrderableProduct product) {
        if (request.getCode()         != null) product.setCode(request.getCode());
        if (request.getName()         != null) product.setName(request.getName());
        if (request.getDescription()  != null) product.setDescription(request.getDescription());
        if (request.getAllowsUnit()    != null) product.setAllowsUnit(request.getAllowsUnit());
        if (request.getAllowsBulk()    != null) product.setAllowsBulk(request.getAllowsBulk());
        if (request.getUnitsPerBulk() != null) product.setUnitsPerBulk(request.getUnitsPerBulk());
    }

    public OrderableProductResponse toResponse(OrderableProduct product) {
        return OrderableProductResponse.builder()
                .id(product.getId())
                .code(product.getCode())
                .name(product.getName())
                .description(product.getDescription())
                .allowsUnit(product.getAllowsUnit())
                .allowsBulk(product.getAllowsBulk())
                .unitsPerBulk(product.getUnitsPerBulk())
                .active(product.getActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
