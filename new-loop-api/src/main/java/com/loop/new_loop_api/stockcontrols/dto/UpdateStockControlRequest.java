package com.loop.new_loop_api.stockcontrols.dto;

import jakarta.validation.Valid;
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
public class UpdateStockControlRequest {

    private UUID controllerId;

    private LocalDate controlDate;

    @Size(max = 500, message = "Observations cannot exceed 500 characters")
    private String observations;

    @Valid
    private List<CreateStockControlItemRequest> items;
}
