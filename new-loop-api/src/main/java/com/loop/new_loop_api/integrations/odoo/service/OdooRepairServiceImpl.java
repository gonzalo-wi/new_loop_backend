package com.loop.new_loop_api.integrations.odoo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.dispensers.repository.DispenserMovementRepository;
import com.loop.new_loop_api.integrations.common.entity.IntegrationLog;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.integrations.common.exception.IntegrationLogNotFoundException;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import com.loop.new_loop_api.integrations.odoo.mapper.OdooRepairMapper;
import com.loop.new_loop_api.integrations.odoo.service.iService.OdooRepairService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OdooRepairServiceImpl implements OdooRepairService {

    private static final Logger log = LoggerFactory.getLogger(OdooRepairServiceImpl.class);

    private static final String OPERATION   = "REPAIR_CREATE";
    private static final String ENTITY_NAME = "DispenserMovement";
    private static final String STATUS_SENT  = "SENT";
    private static final String STATUS_ERROR = "ERROR";

    private final DispenserMovementRepository dispenserMovementRepository;
    private final IntegrationLogRepository    integrationLogRepository;
    private final OdooRepairMapper            odooRepairMapper;
    private final ObjectMapper                objectMapper;
    private final AuditService                auditService;

    private final RestClient restClient = RestClient.create();

    @Value("${integrations.odoo.base-url}")
    private String baseUrl;

    @Value("${integrations.odoo.api-key}")
    private String apiKey;

    @Override
    @Transactional
    public void send(UUID movementId) {
        var movement = dispenserMovementRepository.findById(movementId).orElse(null);
        if (movement == null) {
            log.warn("Odoo send skipped: dispenser movement {} no longer exists", movementId);
            return;
        }
        if (movement.getType() != DispenserMovementType.UNLOAD) {
            return; // Odoo only handles UNLOAD (repair intake)
        }
        if (movement.getStatus() != DispenserMovementStatus.SENT_TO_AGUAS) {
            log.warn("Odoo send skipped: movement {} is not yet confirmed in Aguas", movementId);
            return;
        }
        if (STATUS_SENT.equals(movement.getOdooStatus())) {
            log.warn("Odoo send skipped: movement {} was already sent to Odoo", movementId);
            return;
        }
        attempt(movement, newLog(movement));
    }

    @Override
    @Transactional
    public void retry(UUID logId) {
        var integrationLog = integrationLogRepository.findById(logId)
                .orElseThrow(() -> new IntegrationLogNotFoundException(logId));

        if (integrationLog.getStatus() == IntegrationStatus.SENT) {
            return;
        }
        var movement = dispenserMovementRepository.findById(integrationLog.getEntityId()).orElse(null);
        if (movement == null || STATUS_SENT.equals(movement.getOdooStatus())) {
            return;
        }
        integrationLog.setRetryCount(integrationLog.getRetryCount() + 1);
        attempt(movement, integrationLog);
    }

    private IntegrationLog newLog(DispenserMovement movement) {
        return IntegrationLog.builder()
                .integrationName(IntegrationName.ODOO)
                .operationType(OPERATION)
                .entityName(ENTITY_NAME)
                .entityId(movement.getId())
                .status(IntegrationStatus.PENDING)
                .retryCount(0)
                .build();
    }

    private void attempt(DispenserMovement movement, IntegrationLog integrationLog) {
        var request        = odooRepairMapper.toRequest(movement);
        var requestPayload = serialize(request);
        integrationLog.setRequestPayload(requestPayload);
        log.info("Odoo {} request for movement {}: {}", OPERATION, movement.getId(), requestPayload);

        try {
            var http   = restClient.post()
                    .uri(baseUrl + "/api/v1/repair/create")
                    .header("X-API-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((req, res) -> new OdooHttpResult(
                            res.getStatusCode().value(),
                            new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));

            var result = extractResult(http.status(), http.body());
            if (result != null && result.path("success").asBoolean(false)) {
                markSent(integrationLog, movement, http.body(), result);
            } else {
                markError(integrationLog, movement, extractError(http.status(), http.body(), result));
            }
        } catch (Exception e) {
            markError(integrationLog, movement, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private record OdooHttpResult(int status, String body) {}

    private void markSent(IntegrationLog integrationLog, DispenserMovement movement, String body, JsonNode result) {
        integrationLog.setStatus(IntegrationStatus.SENT);
        integrationLog.setResponsePayload(body);
        integrationLog.setErrorMessage(null);
        integrationLog.setSentAt(LocalDateTime.now());

        movement.setOdooStatus(STATUS_SENT);
        if (result.hasNonNull("picking_id"))   movement.setOdooPickingId(result.get("picking_id").asInt());
        if (result.hasNonNull("picking_name")) movement.setOdooPickingName(result.get("picking_name").asText());

        persist(integrationLog, movement);
        auditService.register("SEND_DISPENSER_TO_ODOO", ENTITY_NAME, movement.getId(), null,
                Map.of("picking", movement.getOdooPickingName() != null ? movement.getOdooPickingName() : ""));
        log.info("Odoo repair created for movement {} (picking {})", movement.getId(), movement.getOdooPickingName());
    }

    private void markError(IntegrationLog integrationLog, DispenserMovement movement, String errorMessage) {
        integrationLog.setStatus(IntegrationStatus.ERROR);
        integrationLog.setErrorMessage(errorMessage);

        movement.setOdooStatus(STATUS_ERROR);
        persist(integrationLog, movement);
        auditService.register("ODOO_ERROR", ENTITY_NAME, movement.getId(), null, Map.of("error", errorMessage));
        log.error("Odoo {} failed for movement {}: {}", OPERATION, movement.getId(), errorMessage);
    }

    private void persist(IntegrationLog integrationLog, DispenserMovement movement) {
        integrationLogRepository.save(integrationLog);
        dispenserMovementRepository.save(movement);
    }

    /** Odoo JSON-RPC returns HTTP 200 with the outcome inside "result". */
    private JsonNode extractResult(int status, String body) {
        if (status < 200 || status >= 300 || body == null || body.isBlank()) return null;
        try {
            var result = objectMapper.readTree(body).get("result");
            return (result != null && !result.isNull()) ? result : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractError(int status, String body, JsonNode result) {
        // Message inside result, or JSON-RPC error object, or raw body
        try {
            if (result != null) {
                for (var key : new String[]{"error", "message", "mensaje"}) {
                    if (result.hasNonNull(key)) return "HTTP " + status + " - " + result.get(key).asText();
                }
            }
            if (body != null && !body.isBlank()) {
                var error = objectMapper.readTree(body).get("error");
                if (error != null && !error.isNull()) {
                    return "HTTP " + status + " - " + error.toString();
                }
            }
        } catch (Exception ignored) {
            // fall through to raw body
        }
        return "HTTP " + status + " - " + body;
    }

    private String serialize(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }
}
