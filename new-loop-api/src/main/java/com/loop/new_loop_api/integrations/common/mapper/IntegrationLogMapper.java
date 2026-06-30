package com.loop.new_loop_api.integrations.common.mapper;

import com.loop.new_loop_api.integrations.common.dto.IntegrationLogResponse;
import com.loop.new_loop_api.integrations.common.entity.IntegrationLog;
import org.springframework.stereotype.Component;

@Component
public class IntegrationLogMapper {

    public IntegrationLogResponse toResponse(IntegrationLog log) {
        return IntegrationLogResponse.builder()
                .id(log.getId())
                .integrationName(log.getIntegrationName())
                .operationType(log.getOperationType())
                .entityName(log.getEntityName())
                .entityId(log.getEntityId())
                .status(log.getStatus())
                .requestPayload(log.getRequestPayload())
                .responsePayload(log.getResponsePayload())
                .errorMessage(log.getErrorMessage())
                .retryCount(log.getRetryCount())
                .createdAt(log.getCreatedAt())
                .sentAt(log.getSentAt())
                .build();
    }
}
