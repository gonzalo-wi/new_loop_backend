package com.loop.new_loop_api.integrations.common.service;

import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentService;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasIntegrationService;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.exception.IntegrationLogNotFoundException;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import com.loop.new_loop_api.integrations.odoo.service.iService.OdooRepairService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Routes an integration-log retry to the handler that owns it (by integration + entity). */
@Service
@RequiredArgsConstructor
public class IntegrationRetryDispatcher {

    private static final Logger log = LoggerFactory.getLogger(IntegrationRetryDispatcher.class);

    private final IntegrationLogRepository integrationLogRepository;
    private final AguasIntegrationService  aguasIntegrationService;   // Aguas / StockControl
    private final AguasEquipmentService    aguasEquipmentService;     // Aguas / DispenserMovement
    private final OdooRepairService        odooRepairService;         // Odoo  / DispenserMovement

    public void retry(UUID logId) {
        var integrationLog = integrationLogRepository.findById(logId)
                .orElseThrow(() -> new IntegrationLogNotFoundException(logId));

        if (integrationLog.getIntegrationName() == IntegrationName.ODOO) {
            odooRepairService.retry(logId);
            return;
        }

        // Aguas: route by the entity the log belongs to
        switch (integrationLog.getEntityName()) {
            case "StockControl"      -> aguasIntegrationService.retry(logId);
            case "DispenserMovement" -> aguasEquipmentService.retry(logId);
            default -> log.warn("No retry handler for {}/{} (log {})",
                    integrationLog.getIntegrationName(), integrationLog.getEntityName(), logId);
        }
    }
}
