package com.loop.new_loop_api.integrations.common.service.iService;

import com.loop.new_loop_api.integrations.common.dto.IntegrationLogResponse;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface IntegrationLogService {

    Page<IntegrationLogResponse> getAll(IntegrationName integrationName, IntegrationStatus status,
                                        UUID entityId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    IntegrationLogResponse getById(UUID id);

    /**
     * Retry requested by a person (not the scheduler): retried right away and recorded in the audit trail.
     * Throws NothingToRetryException (409) when the handler had nothing to do.
     */
    IntegrationLogResponse retryManually(UUID id);
}
