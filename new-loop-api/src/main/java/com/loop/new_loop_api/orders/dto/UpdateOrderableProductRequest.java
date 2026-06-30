package com.loop.new_loop_api.orders.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateOrderableProductRequest {

    @Size(max = 50)
    private String code;

    private String name;

    @Size(max = 500)
    private String description;

    private Boolean allowsUnit;
    private Boolean allowsBulk;

    @Positive(message = "Units per bulk must be positive")
    private Integer unitsPerBulk;
}
