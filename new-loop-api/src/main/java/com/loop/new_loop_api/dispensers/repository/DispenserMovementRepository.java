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
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface DispenserMovementRepository
        extends JpaRepository<DispenserMovement, UUID>, JpaSpecificationExecutor<DispenserMovement> {

    boolean existsByTypeAndRouteCodeAndMovementDateAndStatusNot(
            DispenserMovementType type, String routeCode, LocalDate movementDate, DispenserMovementStatus status);

    List<DispenserMovement> findByTypeAndRouteCodeAndMovementDateAndStatusNot(
            DispenserMovementType type, String routeCode, LocalDate movementDate, DispenserMovementStatus status);

    boolean existsByTypeAndRouteCodeAndMovementDateAndStatusNotAndCreatedAtAfter(
            DispenserMovementType type, String routeCode, LocalDate movementDate, DispenserMovementStatus status,
            LocalDateTime createdAt);

    /**
     * Transaction-scoped PostgreSQL advisory lock on {@code key}, released on commit/rollback. LOAD has no
     * unique index (a route gets recargas), so this is what serializes concurrent loads of one route and day.
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
    Integer lockLoadsOfRouteDay(@Param("key") String key);

    /** Ids among {@code ids} that are reloads: an earlier active movement of that type exists for its route and date. */
    @Query("""
            SELECT m.id FROM DispenserMovement m
            WHERE m.id IN :ids AND m.type = :type AND EXISTS (
                SELECT 1 FROM DispenserMovement p
                WHERE p.type = :type AND p.status <> :excluded
                  AND p.routeCode = m.routeCode AND p.movementDate = m.movementDate
                  AND p.createdAt < m.createdAt)
            """)
    Set<UUID> findReloadIds(@Param("ids") Collection<UUID> ids,
                            @Param("type") DispenserMovementType type,
                            @Param("excluded") DispenserMovementStatus excluded);

    /** Movements that scanned the serial, newest first; matched case-insensitively (index from V33). */
    @Query("""
            SELECT DISTINCT m FROM DispenserMovement m JOIN m.serials s
            WHERE UPPER(s) = UPPER(:serial)
            ORDER BY m.movementDate DESC, m.createdAt DESC
            """)
    List<DispenserMovement> findBySerial(@Param("serial") String serial, Pageable pageable);
}
