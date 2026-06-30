package com.loop.new_loop_api.integrations.common.repository;

import com.loop.new_loop_api.integrations.common.entity.IntegrationLog;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface IntegrationLogRepository
        extends JpaRepository<IntegrationLog, UUID>, JpaSpecificationExecutor<IntegrationLog> {

    List<IntegrationLog> findByIntegrationNameAndStatusAndRetryCountLessThan(
            IntegrationName integrationName, IntegrationStatus status, Integer maxRetries);
}
