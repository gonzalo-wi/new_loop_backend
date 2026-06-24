package com.loop.new_loop_api.stockcontrols.repository;

import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

public class StockControlSpecification {

    public static Specification<StockControl> withFilters(
            ControlType type, ControlStatus status, UUID routeId, UUID controllerId, LocalDate from, LocalDate to) {

        Specification<StockControl> spec = (r, q, cb) -> cb.conjunction();

        if (type         != null) spec = spec.and(hasType(type));
        if (status       != null) spec = spec.and(hasStatus(status));
        if (routeId      != null) spec = spec.and(hasRoute(routeId));
        if (controllerId != null) spec = spec.and(hasController(controllerId));
        if (from         != null) spec = spec.and(dateFrom(from));
        if (to           != null) spec = spec.and(dateTo(to));

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

    private static Specification<StockControl> dateFrom(LocalDate from) {
        return (r, q, cb) -> cb.greaterThanOrEqualTo(r.<LocalDate>get("controlDate"), from);
    }

    private static Specification<StockControl> dateTo(LocalDate to) {
        return (r, q, cb) -> cb.lessThanOrEqualTo(r.<LocalDate>get("controlDate"), to);
    }
}
