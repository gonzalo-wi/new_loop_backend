package com.loop.new_loop_api.dispensers.service.iService;

import com.loop.new_loop_api.dispensers.dto.CreateDispenserMovementRequest;
import com.loop.new_loop_api.dispensers.dto.DispenserMovementResponse;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface DispenserMovementService {

    DispenserMovementResponse createMovement(CreateDispenserMovementRequest request);
    Page<DispenserMovementResponse> getAllMovements(DispenserMovementType type, String routeCode,
                                                    DispenserMovementStatus status, LocalDate from, LocalDate to,
                                                    Pageable pageable);
    DispenserMovementResponse getMovementById(UUID id);
    DispenserMovementResponse cancelMovement(UUID id);
    DispenserMovementResponse updateMovement(UUID id, CreateDispenserMovementRequest request);
}
