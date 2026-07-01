package com.loop.new_loop_api.integrations.aguas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.dispensers.repository.DispenserMovementRepository;
import com.loop.new_loop_api.integrations.aguas.client.AguasEquipmentClient;
import com.loop.new_loop_api.integrations.aguas.dto.AguasDeleteEquipmentRequest;
import com.loop.new_loop_api.integrations.aguas.mapper.AguasEquipmentMapper;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentService;
import com.loop.new_loop_api.integrations.common.entity.IntegrationLog;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.integrations.common.exception.IntegrationLogNotFoundException;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import feign.Response;
import feign.Util;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AguasEquipmentServiceImpl implements AguasEquipmentService {

    private static final Logger log = LoggerFactory.getLogger(AguasEquipmentServiceImpl.class);

    private static final String OPERATION_LOAD   = "EQUIPMENT_OUT"; // registrar-salida-camion
    private static final String OPERATION_UNLOAD = "EQUIPMENT_IN";  // registrar-vuelta-camion
    private static final String ENTITY_NAME      = "DispenserMovement";

    private final DispenserMovementRepository dispenserMovementRepository;
    private final IntegrationLogRepository    integrationLogRepository;
    private final AguasEquipmentClient        aguasEquipmentClient;
    private final AguasEquipmentMapper        aguasEquipmentMapper;
    private final ObjectMapper                objectMapper;
    private final AuditService                auditService;

    @Override
    @Transactional
    public void send(UUID movementId) {
        var movement = dispenserMovementRepository.findById(movementId).orElse(null);
        if (movement == null) {
            log.warn("Aguas equipment send skipped: dispenser movement {} no longer exists", movementId);
            return;
        }
        if (movement.getStatus() == DispenserMovementStatus.SENT_TO_AGUAS) {
            log.warn("Aguas equipment send skipped: dispenser movement {} was already sent", movementId);
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
        if (movement == null) {
            log.warn("Aguas equipment retry skipped: dispenser movement {} no longer exists", integrationLog.getEntityId());
            return;
        }
        if (movement.getStatus() == DispenserMovementStatus.SENT_TO_AGUAS) {
            log.warn("Aguas equipment retry skipped: dispenser movement {} was already sent", movement.getId());
            return;
        }
        integrationLog.setRetryCount(integrationLog.getRetryCount() + 1);
        attempt(movement, integrationLog);
    }

    @Override
    public Object getDestinationLocations() {
        return readCatalog(aguasEquipmentClient.getDestinationLocations());
    }

    @Override
    public Object getDestinationStates() {
        return readCatalog(aguasEquipmentClient.getDestinationStates());
    }

    /**
     * Deletes the movement in Aguas. Runs in its own transaction so the delete log is
     * persisted even if the caller rolls back. Returns true on success.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteInAguas(UUID movementId) {
        var movement = dispenserMovementRepository.findById(movementId).orElse(null);
        if (movement == null || movement.getAguasMovementId() == null) {
            return true; // nothing to delete on Aguas side
        }

        var integrationLog = IntegrationLog.builder()
                .integrationName(IntegrationName.AGUAS)
                .operationType("EQUIPMENT_DELETE")
                .entityName(ENTITY_NAME)
                .entityId(movement.getId())
                .status(IntegrationStatus.PENDING)
                .retryCount(0)
                .build();

        var request = new AguasDeleteEquipmentRequest(movement.getAguasMovementId(), movement.getRegisteredByUsername());
        integrationLog.setRequestPayload(serialize(request));

        try (Response response = aguasEquipmentClient.deleteMovement(request)) {
            var body = readBody(response);
            if (isSuccessful(response.status(), body)) {
                integrationLog.setStatus(IntegrationStatus.SENT);
                integrationLog.setResponsePayload(body);
                integrationLog.setSentAt(LocalDateTime.now());
                integrationLogRepository.save(integrationLog);
                log.info("Aguas delete succeeded for movement {} (idMovimiento {})",
                        movement.getId(), movement.getAguasMovementId());
                return true;
            }
            integrationLog.setStatus(IntegrationStatus.ERROR);
            integrationLog.setErrorMessage(extractError(response.status(), body));
            integrationLogRepository.save(integrationLog);
            log.error("Aguas delete failed for movement {}: {}", movement.getId(), integrationLog.getErrorMessage());
            return false;
        } catch (Exception e) {
            integrationLog.setStatus(IntegrationStatus.ERROR);
            integrationLog.setErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
            integrationLogRepository.save(integrationLog);
            log.error("Aguas delete errored for movement {}: {}", movement.getId(), e.getMessage());
            return false;
        }
    }

    private String extractAguasMovementId(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            var data = objectMapper.readTree(body).get("data");
            if (data == null) return null;

            // Aguas returns "data" as an array of { "idmovimiento": <n> }
            var node = data.isArray() ? (data.isEmpty() ? null : data.get(0)) : data;
            if (node == null) return null;

            for (var key : List.of("idmovimiento", "IdMovimiento", "idMovimiento", "id")) {
                if (node.has(key) && !node.get(key).isNull()) {
                    return node.get(key).asText();
                }
            }
        } catch (Exception ignored) {
            // no id available
        }
        return null;
    }

    private IntegrationLog newLog(DispenserMovement movement) {
        var operation = movement.getType() == DispenserMovementType.LOAD ? OPERATION_LOAD : OPERATION_UNLOAD;
        return IntegrationLog.builder()
                .integrationName(IntegrationName.AGUAS)
                .operationType(operation)
                .entityName(ENTITY_NAME)
                .entityId(movement.getId())
                .status(IntegrationStatus.PENDING)
                .retryCount(0)
                .build();
    }

    private void attempt(DispenserMovement movement, IntegrationLog integrationLog) {
        var request        = aguasEquipmentMapper.toRequest(movement);
        var requestPayload = serialize(request);
        integrationLog.setRequestPayload(requestPayload);
        log.info("Aguas equipment {} request for movement {}: {}",
                integrationLog.getOperationType(), movement.getId(), requestPayload);

        try (Response response = movement.getType() == DispenserMovementType.LOAD
                ? aguasEquipmentClient.registerTruckDeparture(request)
                : aguasEquipmentClient.registerTruckReturn(request)) {

            var body = readBody(response);
            if (isSuccessful(response.status(), body)) {
                markSent(integrationLog, movement, body);
            } else {
                markError(integrationLog, movement, extractError(response.status(), body));
            }
        } catch (Exception e) {
            markError(integrationLog, movement, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void markSent(IntegrationLog integrationLog, DispenserMovement movement, String responseBody) {
        integrationLog.setStatus(IntegrationStatus.SENT);
        integrationLog.setResponsePayload(responseBody);
        integrationLog.setErrorMessage(null);
        integrationLog.setSentAt(LocalDateTime.now());

        movement.setStatus(DispenserMovementStatus.SENT_TO_AGUAS);
        var aguasId = extractAguasMovementId(responseBody);
        if (aguasId != null) {
            movement.setAguasMovementId(aguasId);
        }
        persist(integrationLog, movement);
        auditService.register("SEND_DISPENSER_TO_AGUAS", ENTITY_NAME, movement.getId(), null,
                Map.of("operation", integrationLog.getOperationType(), "status", IntegrationStatus.SENT));
        log.info("Aguas equipment {} sent successfully for movement {}. Response: {}",
                integrationLog.getOperationType(), movement.getId(), responseBody);
    }

    private void markError(IntegrationLog integrationLog, DispenserMovement movement, String errorMessage) {
        integrationLog.setStatus(IntegrationStatus.ERROR);
        integrationLog.setErrorMessage(errorMessage);

        movement.setStatus(DispenserMovementStatus.AGUAS_ERROR);
        persist(integrationLog, movement);
        auditService.register("DISPENSER_AGUAS_ERROR", ENTITY_NAME, movement.getId(), null,
                Map.of("operation", integrationLog.getOperationType(), "error", errorMessage));
        log.error("Aguas equipment {} failed for movement {}: {}",
                integrationLog.getOperationType(), movement.getId(), errorMessage);
    }

    private void persist(IntegrationLog integrationLog, DispenserMovement movement) {
        integrationLogRepository.save(integrationLog);
        dispenserMovementRepository.save(movement);
    }

    /** Aguas returns HTTP 4xx/5xx on error, but also {"success": false} — check both. */
    private boolean isSuccessful(int status, String body) {
        if (status < 200 || status >= 300) return false;
        if (body == null || body.isBlank()) return true;
        try {
            var node = objectMapper.readTree(body);
            return !node.has("success") || node.get("success").asBoolean();
        } catch (Exception e) {
            return true;
        }
    }

    private String extractError(int status, String body) {
        if (body != null && !body.isBlank()) {
            try {
                var node = objectMapper.readTree(body);
                if (node.has("message")) {
                    return "HTTP " + status + " - " + node.get("message").asText();
                }
            } catch (Exception ignored) {
                // fall through to raw body
            }
        }
        return "HTTP " + status + " - " + body;
    }

    private Object readCatalog(Response response) {
        try (response) {
            var body = readBody(response);
            return body == null ? null : objectMapper.readTree(body);
        } catch (Exception e) {
            log.error("Failed reading Aguas catalog: {}", e.getMessage());
            return null;
        }
    }

    private String readBody(Response response) {
        if (response.body() == null) return null;
        try {
            return Util.toString(response.body().asReader(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
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
