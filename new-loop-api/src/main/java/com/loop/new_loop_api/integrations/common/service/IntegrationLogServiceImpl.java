package com.loop.new_loop_api.integrations.common.service;

import com.loop.new_loop_api.integrations.common.dto.IntegrationLogResponse;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.integrations.common.exception.IntegrationLogNotFoundException;
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

    private final IntegrationLogRepository integrationLogRepository;
    private final IntegrationLogMapper     integrationLogMapper;

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
}
