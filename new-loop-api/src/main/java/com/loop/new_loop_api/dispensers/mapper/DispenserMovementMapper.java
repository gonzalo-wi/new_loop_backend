package com.loop.new_loop_api.dispensers.mapper;

import com.loop.new_loop_api.dispensers.dto.CreateDispenserMovementRequest;
import com.loop.new_loop_api.dispensers.dto.DispenserMovementResponse;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;

@Component
public class DispenserMovementMapper {

    public DispenserMovement toEntity(CreateDispenserMovementRequest request, LocalDate movementDate) {
        return DispenserMovement.builder()
                .type(request.getType())
                .routeCode(request.getRouteCode())
                .technician(request.getTechnician())
                .locationId(request.getLocationId())
                .stateId(request.getStateId())
                .movementDate(movementDate)
                .serials(new ArrayList<>(request.getSerials()))
                .build();
    }

    public DispenserMovementResponse toResponse(DispenserMovement movement) {
        return DispenserMovementResponse.builder()
                .id(movement.getId())
                .type(movement.getType())
                .routeCode(movement.getRouteCode())
                .technician(movement.getTechnician())
                .locationId(movement.getLocationId())
                .stateId(movement.getStateId())
                .movementDate(movement.getMovementDate())
                .status(movement.getStatus())
                .serials(new ArrayList<>(movement.getSerials()))
                .aguasMovementId(movement.getAguasMovementId())
                .registeredBy(movement.getRegisteredBy())
                .registeredByUsername(movement.getRegisteredByUsername())
                .createdAt(movement.getCreatedAt())
                .updatedAt(movement.getUpdatedAt())
                .build();
    }
}
