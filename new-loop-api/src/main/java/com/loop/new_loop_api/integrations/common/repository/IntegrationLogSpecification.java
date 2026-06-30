package com.loop.new_loop_api.integrations.common.repository;

import com.loop.new_loop_api.integrations.common.entity.IntegrationLog;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.UUID;

public class IntegrationLogSpecification {

    public static Specification<IntegrationLog> withFilters(
            IntegrationName integrationName, IntegrationStatus status, UUID entityId,
            LocalDateTime from, LocalDateTime to) {

        Specification<IntegrationLog> spec = (r, q, cb) -> cb.conjunction();

        if (integrationName != null) spec = spec.and(hasName(integrationName));
        if (status          != null) spec = spec.and(hasStatus(status));
        if (entityId        != null) spec = spec.and(hasEntity(entityId));
        if (from            != null) spec = spec.and(createdFrom(from));
        if (to              != null) spec = spec.and(createdTo(to));

        return spec;
    }

    private static Specification<IntegrationLog> hasName(IntegrationName name) {
        return (r, q, cb) -> cb.equal(r.get("integrationName"), name);
    }

    private static Specification<IntegrationLog> hasStatus(IntegrationStatus status) {
        return (r, q, cb) -> cb.equal(r.get("status"), status);
    }

    private static Specification<IntegrationLog> hasEntity(UUID entityId) {
        return (r, q, cb) -> cb.equal(r.get("entityId"), entityId);
    }

    private static Specification<IntegrationLog> createdFrom(LocalDateTime from) {
        return (r, q, cb) -> cb.greaterThanOrEqualTo(r.<LocalDateTime>get("createdAt"), from);
    }

    private static Specification<IntegrationLog> createdTo(LocalDateTime to) {
        return (r, q, cb) -> cb.lessThanOrEqualTo(r.<LocalDateTime>get("createdAt"), to);
    }
}
