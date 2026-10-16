package com.loop.new_loop_api.stockcontrols.repository;

import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class StockControlSpecification {

    public static Specification<StockControl> withFilters(
            ControlType type, ControlStatus status, UUID routeId, UUID controllerId, UUID branchId,
            LocalDate from, LocalDate to, LocalDate createdFrom, LocalDate createdTo) {

        Specification<StockControl> spec = (r, q, cb) -> cb.conjunction();

        if (type         != null) spec = spec.and(hasType(type));
        if (status       != null) spec = spec.and(hasStatus(status));
        if (routeId      != null) spec = spec.and(hasRoute(routeId));
        if (controllerId != null) spec = spec.and(hasController(controllerId));
        if (branchId     != null) spec = spec.and(hasBranch(branchId));
        if (from         != null) spec = spec.and(dateFrom(from));
        if (to           != null) spec = spec.and(dateTo(to));
        if (createdFrom  != null) spec = spec.and(createdFrom(createdFrom));
        if (createdTo    != null) spec = spec.and(createdTo(createdTo));

        return spec;
    }

    private static Specification<StockControl> hasType(ControlType type) {
        return (r, q, cb) -> cb.equal(r.get("type"), type);
    }

    private static Specification<StockControl> hasStatus(ControlStatus status) {
        return (r, q, cb) -> cb.equal(r.get("status"), status);
    }

    private static Specification<StockControl> hasRoute(UUID routeId) {
        return (r, q, cb) -> cb.equal(r.get("route").get("id"), routeId);
    }

    private static Specification<StockControl> hasController(UUID controllerId) {
        return (r, q, cb) -> cb.equal(r.get("controllerId"), controllerId);
    }

    private static Specification<StockControl> hasBranch(UUID branchId) {
        return (r, q, cb) -> cb.equal(r.get("branch").get("id"), branchId);
    }

    private static Specification<StockControl> dateFrom(LocalDate from) {
        return (r, q, cb) -> cb.greaterThanOrEqualTo(r.<LocalDate>get("controlDate"), from);
    }

    private static Specification<StockControl> dateTo(LocalDate to) {
        return (r, q, cb) -> cb.lessThanOrEqualTo(r.<LocalDate>get("controlDate"), to);
    }

    private static Specification<StockControl> createdFrom(LocalDate from) {
        return (r, q, cb) -> cb.greaterThanOrEqualTo(r.<LocalDateTime>get("createdAt"), from.atStartOfDay());
    }

    // Exclusive upper bound at the next midnight so the whole "to" day is included.
    private static Specification<StockControl> createdTo(LocalDate to) {
        return (r, q, cb) -> cb.lessThan(r.<LocalDateTime>get("createdAt"), to.plusDays(1).atStartOfDay());
    }
}
