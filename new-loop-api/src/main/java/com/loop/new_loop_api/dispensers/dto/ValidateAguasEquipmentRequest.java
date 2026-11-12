package com.loop.new_loop_api.dispensers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Request body for POST /dispenser-movements/aguas/validate-equipment. */
@Getter
@Setter
@NoArgsConstructor
public class ValidateAguasEquipmentRequest {

    @NotEmpty(message = "At least one dispenser serial is required")
    @Size(max = 200, message = "At most 200 dispenser serials per request")
    private List<@NotBlank String> equipos;
}
