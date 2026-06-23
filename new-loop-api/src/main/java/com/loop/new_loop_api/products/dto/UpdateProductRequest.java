package com.loop.new_loop_api.products.dto;

import com.loop.new_loop_api.products.entity.ProductType;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdateProductRequest {

    @Size(max = 150, message = "Name must not exceed 150 characters")
    private String name;

    @Positive(message = "Display order must be positive")
    private Integer displayOrder;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private ProductType type;

    @Size(max = 50, message = "Unit must not exceed 50 characters")
    private String unit;

    @PositiveOrZero(message = "Pack quantity must be zero or positive")
    private Integer packQuantity;
}
