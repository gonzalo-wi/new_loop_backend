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
import com.loop.new_loop_api.integrations.common.metrics.IntegrationCallMetrics;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import com.loop.new_loop_api.integrations.odoo.mapper.OdooRepairMapper;
import com.loop.new_loop_api.integrations.odoo.service.iService.OdooNoNormalizadoService;
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

/**
 * Forwards UNLOAD serials flagged as unregistered in jMobile to Odoo POST /api/v1/equipos/no-normalizados.
 * Lives in the same Odoo module as repair intake (aguas_fc_integration), so it shares its base URL and API key.
 * Independent of Aguas: those serials never reach Aguas, so this runs right after the movement is created.
 */
@Service
@RequiredArgsConstructor
public class OdooNoNormalizadoServiceImpl implements OdooNoNormalizadoService {

    private static final Logger log = LoggerFactory.getLogger(OdooNoNormalizadoServiceImpl.class);

    public static final String OPERATION = "NO_NORMALIZADO_CREATE";

    private static final String ENTITY_NAME  = "DispenserMovement";
    private static final String STATUS_SENT  = "SENT";
    private static final String STATUS_ERROR = "ERROR";
    private static final String PATH_CREATE  = "/api/v1/equipos/no-normalizados";

    private final DispenserMovementRepository dispenserMovementRepository;
    private final IntegrationLogRepository    integrationLogRepository;
    private final OdooRepairMapper            odooRepairMapper;
    private final ObjectMapper                objectMapper;
    private final AuditService                auditService;
    private final IntegrationCallMetrics      integrationCallMetrics;

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
            log.warn("Odoo no-normalizados send skipped: dispenser movement {} no longer exists", movementId);
            return;
        }
        if (!isSendable(movement)) return;
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
        if (movement == null || !isSendable(movement)) {
            return;
        }
        integrationLog.setRetryCount(integrationLog.getRetryCount() + 1);
        attempt(movement, integrationLog);
    }

    private boolean isSendable(DispenserMovement movement) {
        if (movement.getType() != DispenserMovementType.UNLOAD || movement.getExcludedSerials().isEmpty()) {
            return false;
        }
        if (movement.getStatus() == DispenserMovementStatus.CANCELLED) {
            log.warn("Odoo no-normalizados send skipped: movement {} was cancelled", movement.getId());
            return false;
        }
        if (STATUS_SENT.equals(movement.getOdooNoNormalizadoStatus())) {
            log.warn("Odoo no-normalizados send skipped: movement {} was already sent", movement.getId());
            return false;
        }
        return true;
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
        var request        = odooRepairMapper.toNoNormalizadoRequest(movement);
        var requestPayload = serialize(request);
        integrationLog.setRequestPayload(requestPayload);
        log.info("Odoo {} request for movement {}: {}", OPERATION, movement.getId(), requestPayload);

        try {
            var http   = restClient.post()
                    .uri(baseUrl + PATH_CREATE)
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

        movement.setOdooNoNormalizadoStatus(STATUS_SENT);
        if (result.hasNonNull("picking_id"))   movement.setOdooNoNormalizadoPickingId(result.get("picking_id").asInt());
        if (result.hasNonNull("picking_name")) movement.setOdooNoNormalizadoPickingName(result.get("picking_name").asText());

        persist(integrationLog, movement);
        auditService.register("SEND_NO_NORMALIZADOS_TO_ODOO", ENTITY_NAME, movement.getId(), null,
                Map.of("picking", movement.getOdooNoNormalizadoPickingName() != null ? movement.getOdooNoNormalizadoPickingName() : "",
                        "seriales", movement.getExcludedSerials().size()));
        log.info("Odoo no-normalizados intake created for movement {} (picking {})",
                movement.getId(), movement.getOdooNoNormalizadoPickingName());
        integrationCallMetrics.recordSuccess(IntegrationName.ODOO);
    }

    private void markError(IntegrationLog integrationLog, DispenserMovement movement, String errorMessage) {
        integrationLog.setStatus(IntegrationStatus.ERROR);
        integrationLog.setErrorMessage(errorMessage);

        movement.setOdooNoNormalizadoStatus(STATUS_ERROR);
        persist(integrationLog, movement);
        auditService.register("ODOO_ERROR", ENTITY_NAME, movement.getId(), null,
                Map.of("error", errorMessage, "operation", OPERATION));
        log.error("Odoo {} failed for movement {}: {}", OPERATION, movement.getId(), errorMessage);
        integrationCallMetrics.recordError(IntegrationName.ODOO);
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
        // Message inside result ({"success": false, "error": "..."}), or JSON-RPC error object, or raw body
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
