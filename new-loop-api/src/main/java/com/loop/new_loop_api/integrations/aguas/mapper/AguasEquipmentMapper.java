package com.loop.new_loop_api.integrations.aguas.mapper;

import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.integrations.aguas.dto.AguasEquipmentMovementRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class AguasEquipmentMapper {

    // Salida al reparto (LOAD): Aguas exige esrecarga=1 y accion=3.
    private static final int LOAD_ES_RECARGA = 1;
    private static final int LOAD_ACCION     = 3;
    // Vuelta a planta (UNLOAD): no es recarga y no lleva accion.
    private static final int UNLOAD_ES_RECARGA = 0;

    public AguasEquipmentMovementRequest toRequest(DispenserMovement movement) {
        var isLoad = movement.getType() == DispenserMovementType.LOAD;
        return AguasEquipmentMovementRequest.builder()
                .fecha(movement.getMovementDate().toString())
                .idReparto(parseInteger(movement.getRouteCode()))
                .tecnico(movement.getTechnician())
                .usuario(resolveUsuario(movement))
                .equipos(new ArrayList<>(movement.serialsToSend()))
                .idUbicacionDestino(movement.getLocationId())
                .idEstadoDestino(movement.getStateId())
                .esRecarga(isLoad ? LOAD_ES_RECARGA : UNLOAD_ES_RECARGA)
                .accion(isLoad ? LOAD_ACCION : null)
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
