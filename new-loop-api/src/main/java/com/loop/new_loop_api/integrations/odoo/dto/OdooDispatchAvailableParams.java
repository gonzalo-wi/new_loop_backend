package com.loop.new_loop_api.integrations.odoo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/** params del payload JSON-RPC de Odoo /salida/disponibles. limite/offset son opcionales:
 *  si vienen null, no se serializan (Odoo no espera "limite": null). */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OdooDispatchAvailableParams {

    private Integer limite;
    private Integer offset;
}
