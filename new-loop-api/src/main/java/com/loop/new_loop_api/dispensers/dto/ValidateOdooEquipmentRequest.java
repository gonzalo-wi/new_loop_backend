package com.loop.new_loop_api.dispensers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Request body for POST /dispenser-movements/odoo/validate-equipment. */
@Getter
@Setter
@NoArgsConstructor
public class ValidateOdooEquipmentRequest {

    @NotEmpty(message = "At least one dispenser serial is required")
    private List<@NotBlank String> equipos;
}
