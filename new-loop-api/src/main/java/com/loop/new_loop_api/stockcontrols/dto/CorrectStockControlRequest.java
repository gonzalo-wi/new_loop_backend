package com.loop.new_loop_api.stockcontrols.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CorrectStockControlRequest {

    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;

    @Valid
    @NotEmpty(message = "Items are required for a correction")
    private List<CreateStockControlItemRequest> items;

    @Size(max = 500, message = "Observations cannot exceed 500 characters")
    private String observations;

    private Boolean truckOrdered;
}
