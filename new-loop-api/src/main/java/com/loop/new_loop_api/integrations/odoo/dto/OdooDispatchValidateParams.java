package com.loop.new_loop_api.integrations.odoo.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** params del payload JSON-RPC de Odoo /salida/validar. */
@Getter
@Builder
public class OdooDispatchValidateParams {

    private List<String> equipos;
}
