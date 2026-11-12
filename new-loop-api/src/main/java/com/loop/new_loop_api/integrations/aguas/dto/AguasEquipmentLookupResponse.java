package com.loop.new_loop_api.integrations.aguas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Body of Aguas GET /api/aguas/equipo-fc. A missing serial is a 200 with {@code existe=false} and no equipo. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AguasEquipmentLookupResponse(boolean success, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(Boolean existe, @JsonProperty("nro_serie") String nroSerie, Equipo equipo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Equipo(String ubicacion, String estado) {}
}
