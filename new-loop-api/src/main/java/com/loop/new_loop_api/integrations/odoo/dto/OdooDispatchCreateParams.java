package com.loop.new_loop_api.integrations.odoo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** params del payload JSON-RPC de Odoo /salida/create. */
@Getter
@Builder
public class OdooDispatchCreateParams {

    private String       fecha;
    private String       idreparto;
    private List<String> equipos;

    @JsonProperty("referencia_externa")
    private String referenciaExterna;
}
