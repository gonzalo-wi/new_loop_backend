package com.loop.new_loop_api.stockcontrols.dto;

import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateStockControlRequest {

    @NotNull(message = "Type is required (EXIT or ENTRY)")
    private ControlType type;

    @NotNull(message = "Branch ID is required")
    private UUID branchId;

    @NotNull(message = "Route ID is required")
    private UUID routeId;

    private UUID controllerId;

    private LocalDate controlDate;

    @Size(max = 500, message = "Observations cannot exceed 500 characters")
    private String observations;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<CreateStockControlItemRequest> items;
}
