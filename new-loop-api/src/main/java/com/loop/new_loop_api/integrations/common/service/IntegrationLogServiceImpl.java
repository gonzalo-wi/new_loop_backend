package com.loop.new_loop_api.integrations.common.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.integrations.common.dto.IntegrationLogResponse;
import com.loop.new_loop_api.integrations.common.entity.IntegrationLog;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.integrations.common.exception.IntegrationLogNotFoundException;
import com.loop.new_loop_api.integrations.common.exception.NothingToRetryException;
import com.loop.new_loop_api.integrations.common.mapper.IntegrationLogMapper;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogSpecification;
import com.loop.new_loop_api.integrations.common.service.iService.IntegrationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IntegrationLogServiceImpl implements IntegrationLogService {

    private static final String RETRY_ACTION = "RETRY_INTEGRATION";
    private static final String AUDIT_ENTITY = "IntegrationLog";

    private final IntegrationLogRepository integrationLogRepository;
    private final IntegrationLogMapper     integrationLogMapper;
    private final IntegrationRetryDispatcher integrationRetryDispatcher;
    private final AuditService             auditService;

    @Override
    @Transactional(readOnly = true)
    public Page<IntegrationLogResponse> getAll(IntegrationName integrationName, IntegrationStatus status,
                                               UUID entityId, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        var spec = IntegrationLogSpecification.withFilters(integrationName, status, entityId, from, to);
        return integrationLogRepository.findAll(spec, pageable).map(integrationLogMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public IntegrationLogResponse getById(UUID id) {
        return integrationLogRepository.findById(id)
                .map(integrationLogMapper::toResponse)
                .orElseThrow(() -> new IntegrationLogNotFoundException(id));
    }

    @Override
    public IntegrationLogResponse retryManually(UUID id) {
        var retriesBefore = findLog(id).getRetryCount();
        integrationRetryDispatcher.retry(id);
        var after = findLog(id);
        // Every retry handler bumps retry_count right before it actually calls Aguas/Odoo; when it returns
        // quietly instead (already sent, record gone, nothing to send) the count doesn't move.
        if (after.getRetryCount() <= retriesBefore) {
            throw new NothingToRetryException(id);
        }
        var response = integrationLogMapper.toResponse(after);
        auditService.register(RETRY_ACTION, AUDIT_ENTITY, id, null, response);
        return response;
    }

    private IntegrationLog findLog(UUID id) {
        return integrationLogRepository.findById(id).orElseThrow(() -> new IntegrationLogNotFoundException(id));
    }
}
