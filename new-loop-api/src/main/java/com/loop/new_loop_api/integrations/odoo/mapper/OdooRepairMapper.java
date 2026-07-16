package com.loop.new_loop_api.integrations.odoo.mapper;

import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.integrations.odoo.dto.OdooRepairParams;
import com.loop.new_loop_api.integrations.odoo.dto.OdooRepairRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class OdooRepairMapper {

    public OdooRepairRequest toRequest(DispenserMovement movement) {
        var params = OdooRepairParams.builder()
                .fecha(movement.getMovementDate().toString())
                .idreparto(movement.getRouteCode())
                .tecnico(movement.getTechnician())
                .usuario(movement.getRegisteredByUsername())
                .equipos(new ArrayList<>(movement.getSerials()))
                .build();

        return OdooRepairRequest.builder()
                .params(params)
                .build();
    }
}
