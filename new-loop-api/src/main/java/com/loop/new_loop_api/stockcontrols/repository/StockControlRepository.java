package com.loop.new_loop_api.stockcontrols.repository;

import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface StockControlRepository extends JpaRepository<StockControl, UUID>, JpaSpecificationExecutor<StockControl> {

    /**
     * Locks the row until the transaction ends, so the controller's edit and the driver's approve/reject
     * run one after the other: otherwise an edit could overwrite an approval already sent to Aguas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sc from StockControl sc where sc.id = :id")
    Optional<StockControl> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByTypeAndRouteIdAndControlDateAndStatusNot(
            ControlType type, UUID routeId, LocalDate controlDate, ControlStatus status);

    boolean existsByTypeAndRouteIdAndControlDateAndStatusNotAndIdNot(
            ControlType type, UUID routeId, LocalDate controlDate, ControlStatus status, UUID excludedId);

    Optional<StockControl> findByTypeAndRouteIdAndControlDateAndStatusNot(
            ControlType type, UUID routeId, LocalDate controlDate, ControlStatus status);

    @Query("""
            select sc from StockControl sc
            join fetch sc.route
            join fetch sc.branch
            where sc.type = :type and sc.controlDate = :date and sc.status <> :excludedStatus
              and (:branchId is null or sc.branch.id = :branchId)
            """)
    List<StockControl> findControlsForDate(@Param("type") ControlType type,
                                           @Param("date") LocalDate date,
                                           @Param("excludedStatus") ControlStatus excludedStatus,
                                           @Param("branchId") UUID branchId);

    @Query("""
            select sc.route.id from StockControl sc
            where sc.type = :type and sc.controlDate = :date and sc.status <> :excludedStatus
            """)
    Set<UUID> findRouteIdsForDate(@Param("type") ControlType type,
                                  @Param("date") LocalDate date,
                                  @Param("excludedStatus") ControlStatus excludedStatus);
}
