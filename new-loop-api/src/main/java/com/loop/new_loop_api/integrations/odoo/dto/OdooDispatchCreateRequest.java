package com.loop.new_loop_api.integrations.odoo.dto;

import lombok.Builder;
import lombok.Getter;

/** Body JSON-RPC para Odoo POST /api/v1/salida/create. */
@Getter
@Builder
public class OdooDispatchCreateRequest {

    @Builder.Default
    private String jsonrpc = "2.0";

    @Builder.Default
    private String method = "call";

    private OdooDispatchCreateParams params;
}
