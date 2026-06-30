package com.loop.new_loop_api.orders.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateOrderableProductRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    @Size(max = 500)
    private String description;

    private Boolean allowsUnit = true;
    private Boolean allowsBulk = false;

    @Positive(message = "Units per bulk must be positive")
    private Integer unitsPerBulk;
}
