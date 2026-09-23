package com.loop.new_loop_api.integrations.odoo.mapper;

import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.integrations.odoo.dto.OdooRepairParams;
import com.loop.new_loop_api.integrations.odoo.dto.OdooRepairRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OdooRepairMapper {

    /** Repair intake: the serials actually forwarded (scanned minus excluded). */
    public OdooRepairRequest toRequest(DispenserMovement movement) {
        return toRequest(movement, movement.serialsToSend());
    }

    /** "Equipos no normalizados" intake: same contract as repair, but only the excluded serials. */
    public OdooRepairRequest toNoNormalizadoRequest(DispenserMovement movement) {
        return toRequest(movement, movement.getExcludedSerials());
    }

    private OdooRepairRequest toRequest(DispenserMovement movement, List<String> equipos) {
        var params = OdooRepairParams.builder()
                .fecha(movement.getMovementDate().toString())
                .idreparto(movement.getRouteCode())
                .tecnico(movement.getTechnician())
                .usuario(movement.getRegisteredByUsername())
                .equipos(new ArrayList<>(equipos))
                .build();

        return OdooRepairRequest.builder()
                .params(params)
                .build();
    }
}
