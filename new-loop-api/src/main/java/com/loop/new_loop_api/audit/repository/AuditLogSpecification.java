package com.loop.new_loop_api.audit.repository;

import com.loop.new_loop_api.audit.entity.AuditLog;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.UUID;

public class AuditLogSpecification {

    public static Specification<AuditLog> withFilters(String entityName, String action,
                                                       UUID entityId, LocalDateTime from, LocalDateTime to) {
        Specification<AuditLog> spec = (r, q, cb) -> cb.conjunction();

        if (entityName != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("entityName"), entityName));
        if (action     != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("action"), action));
        if (entityId   != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("entityId"), entityId));
        if (from       != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.<LocalDateTime>get("createdAt"), from));
        if (to         != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.<LocalDateTime>get("createdAt"), to));

        return spec;
    }
}
