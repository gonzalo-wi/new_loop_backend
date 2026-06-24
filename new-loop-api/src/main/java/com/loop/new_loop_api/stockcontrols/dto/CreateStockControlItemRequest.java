package com.loop.new_loop_api.stockcontrols.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateStockControlItemRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Total quantity is required")
    @PositiveOrZero(message = "Total quantity cannot be negative")
    private Integer totalQuantity;

    @NotNull(message = "Full quantity is required")
    @PositiveOrZero(message = "Full quantity cannot be negative")
    private Integer fullQuantity;

    @NotNull(message = "Exchange quantity is required")
    @PositiveOrZero(message = "Exchange quantity cannot be negative")
    private Integer exchangeQuantity;

    @Size(max = 500, message = "Observations cannot exceed 500 characters")
    private String observations;
}
