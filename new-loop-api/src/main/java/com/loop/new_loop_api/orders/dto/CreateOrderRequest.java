package com.loop.new_loop_api.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "Route ID is required")
    private UUID routeId;

    private LocalDate orderDate;

    @Size(max = 500)
    private String observations;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<CreateOrderItemRequest> items;
}
