package com.loop.new_loop_api.integrations.common.dto;

import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class IntegrationLogResponse {

    private UUID              id;
    private IntegrationName   integrationName;
    private String            operationType;
    private String            entityName;
    private UUID              entityId;
    private IntegrationStatus status;
    private String            requestPayload;
    private String            responsePayload;
    private String            errorMessage;
    private Integer           retryCount;
    private LocalDateTime     createdAt;
    private LocalDateTime     sentAt;
}
