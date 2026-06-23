package com.loop.new_loop_api.products.dto;

import com.loop.new_loop_api.products.entity.ProductType;
import jakarta.validation.constraints.*;
import lombok.Getter;

@Getter
public class CreateProductRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 50, message = "Code must not exceed 50 characters")
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    private String name;

    @NotNull(message = "Display order is required")
    @Positive(message = "Display order must be positive")
    private Integer displayOrder;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotNull(message = "Type is required")
    private ProductType type;

    @NotBlank(message = "Unit is required")
    @Size(max = 50, message = "Unit must not exceed 50 characters")
    private String unit;

    @PositiveOrZero(message = "Pack quantity must be zero or positive")
    private Integer packQuantity;
}
