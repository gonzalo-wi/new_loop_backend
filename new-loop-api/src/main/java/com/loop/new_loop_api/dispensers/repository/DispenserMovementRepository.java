package com.loop.new_loop_api.dispensers.repository;

import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.UUID;

public interface DispenserMovementRepository
        extends JpaRepository<DispenserMovement, UUID>, JpaSpecificationExecutor<DispenserMovement> {

    boolean existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
            DispenserMovementType type, String routeCode, LocalDate movementDate, DispenserMovementStatus status);
}
