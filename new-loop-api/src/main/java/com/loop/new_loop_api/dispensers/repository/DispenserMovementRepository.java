package com.loop.new_loop_api.dispensers.repository;

import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DispenserMovementRepository
        extends JpaRepository<DispenserMovement, UUID>, JpaSpecificationExecutor<DispenserMovement> {

    boolean existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
            DispenserMovementType type, String routeCode, LocalDate movementDate, DispenserMovementStatus status);

    /** Movements that scanned the serial, newest first; matched case-insensitively (index from V33). */
    @Query("""
            SELECT DISTINCT m FROM DispenserMovement m JOIN m.serials s
            WHERE UPPER(s) = UPPER(:serial)
            ORDER BY m.movementDate DESC, m.createdAt DESC
            """)
    List<DispenserMovement> findBySerial(@Param("serial") String serial, Pageable pageable);
}
