package com.loop.new_loop_api.stockcontrols.repository;

import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface StockControlRepository extends JpaRepository<StockControl, UUID>, JpaSpecificationExecutor<StockControl> {

    boolean existsByTypeAndRouteIdAndControlDateAndStatusNot(
            ControlType type, UUID routeId, LocalDate controlDate, ControlStatus status);

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
