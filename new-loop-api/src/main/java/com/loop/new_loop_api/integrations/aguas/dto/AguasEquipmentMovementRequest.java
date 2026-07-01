package com.loop.new_loop_api.integrations.aguas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Body for Aguas registrar-salida-camion / registrar-vuelta-camion (same shape for both). */
@Getter
@Builder
public class AguasEquipmentMovementRequest {

    private String fecha;

    @JsonProperty("idreparto")
    private Integer idReparto;

    private String tecnico;
    private String usuario;

    private List<String> equipos;

    @JsonProperty("idubicaciondestino")
    private Integer idUbicacionDestino;

    @JsonProperty("idestadodestino")
    private Integer idEstadoDestino;
}
