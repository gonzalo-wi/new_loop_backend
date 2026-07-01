package com.loop.new_loop_api.integrations.aguas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Body for Aguas DELETE /movimiento-equipo. */
@Getter
@AllArgsConstructor
public class AguasDeleteEquipmentRequest {

    @JsonProperty("idMovimiento")
    private String idMovimiento;

    private String usuario;
}
