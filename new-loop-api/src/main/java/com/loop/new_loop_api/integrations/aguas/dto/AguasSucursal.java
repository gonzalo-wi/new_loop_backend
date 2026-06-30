package com.loop.new_loop_api.integrations.aguas.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AguasSucursal {

    private String  codigo;
    private String  direccion;
    private String  localidad;
    private Integer cndiva;
    private String  nrocuit;
}
