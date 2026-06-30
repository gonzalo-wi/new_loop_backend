package com.loop.new_loop_api.orders.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class CreateOrderItemRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @PositiveOrZero
    private Integer unitQuantity;

    @PositiveOrZero
    private Integer bulkQuantity;
}
