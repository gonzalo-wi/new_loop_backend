package com.loop.new_loop_api.integrations.odoo.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** params del payload JSON-RPC de Odoo /repair/create. */
@Getter
@Builder
public class OdooRepairParams {

    private String       fecha;
    private String       idreparto;
    private String       tecnico;
    private String       usuario;
    private List<String> equipos;
}
