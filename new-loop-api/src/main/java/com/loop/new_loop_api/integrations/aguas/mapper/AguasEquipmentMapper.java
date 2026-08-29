package com.loop.new_loop_api.integrations.aguas.mapper;

import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.integrations.aguas.dto.AguasEquipmentMovementRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class AguasEquipmentMapper {

    // La salida/vuelta de camión de dispensers nunca es una recarga; Aguas exige el flag igual.
    private static final boolean ES_RECARGA = false;

    public AguasEquipmentMovementRequest toRequest(DispenserMovement movement) {
        return AguasEquipmentMovementRequest.builder()
                .fecha(movement.getMovementDate().toString())
                .idReparto(parseInteger(movement.getRouteCode()))
                .tecnico(movement.getTechnician())
                .usuario(resolveUsuario(movement))
                .equipos(new ArrayList<>(movement.serialsToSend()))
                .idUbicacionDestino(movement.getLocationId())
                .idEstadoDestino(movement.getStateId())
                .esRecarga(ES_RECARGA)
                .build();
    }

    /** Aguas exige usuario no nulo; si el movimiento se registró sin sesión, se usa el técnico. */
    private String resolveUsuario(DispenserMovement movement) {
        var username = movement.getRegisteredByUsername();
        return (username != null && !username.isBlank()) ? username : movement.getTechnician();
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
