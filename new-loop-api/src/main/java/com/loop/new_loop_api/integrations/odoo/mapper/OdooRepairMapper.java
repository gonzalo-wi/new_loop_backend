package com.loop.new_loop_api.integrations.odoo.mapper;

import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.integrations.odoo.dto.OdooRepairParams;
import com.loop.new_loop_api.integrations.odoo.dto.OdooRepairRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OdooRepairMapper {

    /** Repair intake: the serials actually forwarded (scanned minus excluded), bagged ones apart. */
    public OdooRepairRequest toRequest(DispenserMovement movement) {
        return toRequest(movement, movement.repairSerials(), movement.baggedSerialsToSend());
    }

    /** "Equipos no normalizados" intake: same contract as repair, but only the excluded serials. */
    public OdooRepairRequest toNoNormalizadoRequest(DispenserMovement movement) {
        return toRequest(movement, movement.getExcludedSerials(), null);
    }

    private OdooRepairRequest toRequest(DispenserMovement movement, List<String> equipos, List<String> embolsados) {
        var params = OdooRepairParams.builder()
                .fecha(movement.getMovementDate().toString())
                .idreparto(movement.getRouteCode())
                .tecnico(movement.getTechnician())
                .usuario(movement.getRegisteredByUsername())
                .equipos(new ArrayList<>(equipos))
                .embolsados(embolsados == null ? null : new ArrayList<>(embolsados))
                .build();

        return OdooRepairRequest.builder()
                .params(params)
                .build();
    }
}
