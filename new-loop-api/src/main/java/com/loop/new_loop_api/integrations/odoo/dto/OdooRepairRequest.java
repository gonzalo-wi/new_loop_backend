package com.loop.new_loop_api.integrations.odoo.dto;

import lombok.Builder;
import lombok.Getter;

/** Body JSON-RPC para Odoo POST /api/v1/repair/create. */
@Getter
@Builder
public class OdooRepairRequest {

    @Builder.Default
    private String jsonrpc = "2.0";

    @Builder.Default
    private String method = "call";

    private OdooRepairParams params;
}
