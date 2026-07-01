package com.loop.new_loop_api.dispensers.dto;

import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CreateDispenserMovementRequest {

    @NotNull(message = "Type is required (LOAD or UNLOAD)")
    private DispenserMovementType type;

    @NotBlank(message = "Route code is required")
    private String routeCode;

    @NotBlank(message = "Technician is required")
    private String technician;

    // Optional: if not provided, a default is applied based on the movement type
    // (LOAD -> EN CAMIONETA / OPERATIVO, UNLOAD -> PLANTA BAJA / EN REPARACION).
    private Integer locationId;

    private Integer stateId;

    private LocalDate movementDate;

    @NotEmpty(message = "At least one dispenser serial is required")
    private List<@NotBlank String> serials;
}
