package com.loop.new_loop_api.integrations.aguas.service;

import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentService;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasIntegrationService;
import com.loop.new_loop_api.integrations.common.exception.IntegrationLogNotFoundException;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Routes an integration-log retry to the handler that owns its entity. */
@Service
@RequiredArgsConstructor
public class AguasRetryDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AguasRetryDispatcher.class);

    private final IntegrationLogRepository integrationLogRepository;
    private final AguasIntegrationService  aguasIntegrationService;   // StockControl
    private final AguasEquipmentService    aguasEquipmentService;     // DispenserMovement

    public void retry(UUID logId) {
        var integrationLog = integrationLogRepository.findById(logId)
                .orElseThrow(() -> new IntegrationLogNotFoundException(logId));

        switch (integrationLog.getEntityName()) {
            case "StockControl"      -> aguasIntegrationService.retry(logId);
            case "DispenserMovement" -> aguasEquipmentService.retry(logId);
            default -> log.warn("No Aguas retry handler for entity {} (log {})",
                    integrationLog.getEntityName(), logId);
        }
    }
}
