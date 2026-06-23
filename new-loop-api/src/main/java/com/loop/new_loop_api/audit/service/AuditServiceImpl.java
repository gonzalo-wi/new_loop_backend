package com.loop.new_loop_api.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.audit.dto.AuditLogResponse;
import com.loop.new_loop_api.audit.entity.AuditLog;
import com.loop.new_loop_api.audit.mapper.AuditLogMapper;
import com.loop.new_loop_api.audit.repository.AuditLogRepository;
import com.loop.new_loop_api.audit.repository.AuditLogSpecification;
import com.loop.new_loop_api.audit.service.iService.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper     auditLogMapper;
    private final ObjectMapper       objectMapper;

    @Override
    public void register(String action, String entityName, UUID entityId, Object oldValue, Object newValue) {
        var log = AuditLog.builder()
                .userId(getCurrentUserId())
                .userRole(getCurrentUserRole())
                .action(action)
                .entityName(entityName)
                .entityId(entityId)
                .oldValue(serialize(oldValue))
                .newValue(serialize(newValue))
                .build();
        auditLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAll(String entityName, String action, UUID entityId,
                                         LocalDateTime from, LocalDateTime to, Pageable pageable) {
        var spec = AuditLogSpecification.withFilters(entityName, action, entityId, from, to);
        return auditLogRepository.findAll(spec, pageable)
                .map(auditLogMapper::toResponse);
    }

    // Runs every day at midnight — deletes records older than 7 days
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void purgeOldLogs() {
        auditLogRepository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(7));
    }

    private UUID getCurrentUserId() {
        // TODO: extract from SecurityContext once JWT filter is implemented
        return null;
    }

    private String getCurrentUserRole() {
        // TODO: extract from SecurityContext once JWT filter is implemented
        return null;
    }

    private String serialize(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value.toString();
        }
    }
}
