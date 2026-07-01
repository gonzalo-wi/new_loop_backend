package com.loop.new_loop_api.integrations.aguas.mapper;

import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.integrations.aguas.dto.AguasEquipmentMovementRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class AguasEquipmentMapper {

    public AguasEquipmentMovementRequest toRequest(DispenserMovement movement) {
        return AguasEquipmentMovementRequest.builder()
                .fecha(movement.getMovementDate().toString())
                .idReparto(parseInteger(movement.getRouteCode()))
                .tecnico(movement.getTechnician())
                .usuario(movement.getRegisteredByUsername())
                .equipos(new ArrayList<>(movement.getSerials()))
                .idUbicacionDestino(movement.getLocationId())
                .idEstadoDestino(movement.getStateId())
                .build();
    }

    private Integer parseInteger(String value) {
        if (value == null) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
