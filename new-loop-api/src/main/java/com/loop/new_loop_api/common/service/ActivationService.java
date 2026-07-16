package com.loop.new_loop_api.common.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.common.entity.Activatable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ActivationService {

    private final AuditService auditService;

    public <T extends Activatable> void setActive(
            JpaRepository<T, UUID> repository, T entity, UUID id, boolean active, String entityName, String action) {
        entity.setActive(active);
        repository.save(entity);
        auditService.register(action, entityName, id, null, null);
    }
}
