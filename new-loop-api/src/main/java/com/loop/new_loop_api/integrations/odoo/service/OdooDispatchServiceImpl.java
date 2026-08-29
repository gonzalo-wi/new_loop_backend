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
import com.loop.new_loop_api.integrations.odoo.mapper.OdooDispatchMapper;
import com.loop.new_loop_api.integrations.odoo.service.iService.OdooDispatchService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class OdooDispatchServiceImpl implements OdooDispatchService {

    private static final Logger log = LoggerFactory.getLogger(OdooDispatchServiceImpl.class);

    private static final String OPERATION   = "DISPATCH_CREATE";
    private static final String ENTITY_NAME = "DispenserMovement";
    private static final String STATUS_SENT  = "SENT";
    private static final String STATUS_ERROR = "ERROR";
    private static final String REASON_NONE_AVAILABLE = "NINGUN_EQUIPO_DISPONIBLE";

    private static final String PATH_CREATE      = "/api/v1/salida/create";
    private static final String PATH_VALIDATE     = "/api/v1/salida/validar";
    private static final String PATH_AVAILABLE    = "/api/v1/salida/disponibles";

    private final DispenserMovementRepository dispenserMovementRepository;
    private final IntegrationLogRepository    integrationLogRepository;
    private final OdooDispatchMapper          odooDispatchMapper;
    private final ObjectMapper                objectMapper;
    private final AuditService                auditService;
    private final IntegrationCallMetrics      integrationCallMetrics;

    private final RestClient restClient = RestClient.create();

    @Value("${integrations.odoo.dispatch.base-url}")
    private String baseUrl;

    @Value("${integrations.odoo.dispatch.api-key}")
    private String apiKey;

    @Override
    @Transactional
    public void send(UUID movementId) {
        var movement = dispenserMovementRepository.findById(movementId).orElse(null);
        if (movement == null) {
            log.warn("Odoo dispatch send skipped: dispenser movement {} no longer exists", movementId);
            return;
        }
        if (movement.getType() != DispenserMovementType.LOAD) {
            return; // Odoo dispatch only handles LOAD (salida to reparto)
        }
        if (movement.getStatus() != DispenserMovementStatus.SENT_TO_AGUAS) {
            log.warn("Odoo dispatch send skipped: movement {} is not yet confirmed in Aguas", movementId);
            return;
        }
        if (STATUS_SENT.equals(movement.getOdooStatus())) {
            log.warn("Odoo dispatch send skipped: movement {} was already sent to Odoo", movementId);
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

    @Override
    public Object getAvailableEquipment(Integer limite, Integer offset) {
        var request = odooDispatchMapper.toAvailableRequest(limite, offset);
        return readCatalog(PATH_AVAILABLE, request);
    }

    @Override
    public Object validateEquipment(List<String> equipos) {
        var request = odooDispatchMapper.toValidateRequest(equipos);
        return readCatalog(PATH_VALIDATE, request);
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
        var referenciaExterna = odooDispatchMapper.buildExternalReference(movement);
        movement.setOdooReference(referenciaExterna);

        var equipos = new ArrayList<>(movement.serialsToSend());
        var outcome = dispatch(movement, equipos, referenciaExterna);
        integrationLog.setRequestPayload(outcome.requestPayload());

        if (outcome.success()) {
            markSent(integrationLog, movement, outcome.body(), outcome.result());
            return;
        }

        // "Todo o nada": Odoo rechaza el lote entero si algún equipo no está en expedición y devuelve
        // las series ofensivas en "detalle". Reintentamos una vez con solo las disponibles para que un
        // equipo mal ubicado no bloquee al resto. Aguas ya recibió la lista completa; solo se filtra Odoo.
        var unavailable = extractUnavailableSeries(outcome.result());
        if (!unavailable.isEmpty()) {
            var available = equipos.stream().filter(equipo -> !unavailable.contains(equipo)).toList();
            if (available.isEmpty()) {
                closeNoneAvailable(integrationLog, movement, outcome);
                return;
            }
            if (available.size() < equipos.size()) {
                var retry = dispatch(movement, new ArrayList<>(available), referenciaExterna);
                integrationLog.setRequestPayload(retry.requestPayload());
                if (retry.success()) {
                    log.info("Odoo dispatch retried for movement {} excluding unavailable equipos {}",
                            movement.getId(), unavailable);
                    markSent(integrationLog, movement, retry.body(), retry.result());
                } else {
                    markError(integrationLog, movement, describe(retry));
                }
                return;
            }
        }
        markError(integrationLog, movement, describe(outcome));
    }

    private DispatchOutcome dispatch(DispenserMovement movement, List<String> equipos, String referenciaExterna) {
        var request        = odooDispatchMapper.toCreateRequest(movement, equipos, referenciaExterna);
        var requestPayload = serialize(request);
        log.info("Odoo {} request for movement {}: {}", OPERATION, movement.getId(), requestPayload);
        try {
            var http = restClient.post()
                    .uri(baseUrl + PATH_CREATE)
                    .header("X-API-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((req, res) -> new OdooHttpResult(
                            res.getStatusCode().value(),
                            new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));

            var result  = extractResult(http.status(), http.body());
            var success = result != null && result.path("success").asBoolean(false);
            return new DispatchOutcome(http.status(), http.body(), result, success, null, requestPayload);
        } catch (Exception e) {
            return new DispatchOutcome(0, null, null, false,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), requestPayload);
        }
    }

    /** Series that Odoo flags as unavailable in the "detalle" array of a rejected batch. */
    private Set<String> extractUnavailableSeries(JsonNode result) {
        if (result == null) return Set.of();
        var detalle = result.get("detalle");
        if (detalle == null || !detalle.isArray()) return Set.of();
        return StreamSupport.stream(detalle.spliterator(), false)
                .map(item -> item.path("serie").asText(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String describe(DispatchOutcome outcome) {
        return outcome.exception() != null
                ? outcome.exception()
                : extractError(outcome.status(), outcome.body(), outcome.result());
    }

    private record OdooHttpResult(int status, String body) {}

    private record DispatchOutcome(int status, String body, JsonNode result, boolean success,
                                   String exception, String requestPayload) {}

    private void markSent(IntegrationLog integrationLog, DispenserMovement movement, String body, JsonNode result) {
        integrationLog.setStatus(IntegrationStatus.SENT);
        integrationLog.setResponsePayload(body);
        integrationLog.setErrorMessage(null);
        integrationLog.setSentAt(LocalDateTime.now());

        movement.setOdooStatus(STATUS_SENT);
        if (result.hasNonNull("picking_id"))   movement.setOdooPickingId(result.get("picking_id").asInt());
        if (result.hasNonNull("picking_name")) movement.setOdooPickingName(result.get("picking_name").asText());

        var yaRegistrado = result.path("ya_registrado").asBoolean(false);

        persist(integrationLog, movement);
        auditService.register("SEND_DISPENSER_TO_ODOO", ENTITY_NAME, movement.getId(), null,
                Map.of("picking", movement.getOdooPickingName() != null ? movement.getOdooPickingName() : "",
                        "yaRegistrado", yaRegistrado));
        log.info("Odoo dispatch created for movement {} (picking {}, yaRegistrado {})",
                movement.getId(), movement.getOdooPickingName(), yaRegistrado);
        integrationCallMetrics.recordSuccess(IntegrationName.ODOO);
    }

    private void markError(IntegrationLog integrationLog, DispenserMovement movement, String errorMessage) {
        integrationLog.setStatus(IntegrationStatus.ERROR);
        integrationLog.setErrorMessage(errorMessage);

        movement.setOdooStatus(STATUS_ERROR);
        persist(integrationLog, movement);
        auditService.register("ODOO_ERROR", ENTITY_NAME, movement.getId(), null, Map.of("error", errorMessage));
        log.error("Odoo {} failed for movement {}: {}", OPERATION, movement.getId(), errorMessage);
        integrationCallMetrics.recordError(IntegrationName.ODOO);
    }

    /**
     * None of the equipos is available in Odoo, so retrying can't succeed: the log is closed as
     * CANCELLED (terminal) to keep the retry scheduler from looping on it, and the movement is left
     * in Odoo error with the offending series for the operator to resolve.
     */
    private void closeNoneAvailable(IntegrationLog integrationLog, DispenserMovement movement, DispatchOutcome outcome) {
        var errorMessage = describe(outcome);
        integrationLog.setStatus(IntegrationStatus.CANCELLED);
        integrationLog.setErrorMessage(errorMessage);

        movement.setOdooStatus(STATUS_ERROR);
        persist(integrationLog, movement);
        auditService.register("ODOO_ERROR", ENTITY_NAME, movement.getId(), null,
                Map.of("error", errorMessage, "reason", REASON_NONE_AVAILABLE));
        log.error("Odoo {} closed for movement {}: no hay equipos disponibles en expedición. {}",
                OPERATION, movement.getId(), errorMessage);
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

    /** Formats the "todo o nada" detail array ({serie, motivo}) when Odoo rejects the whole batch. */
    private String extractError(int status, String body, JsonNode result) {
        try {
            if (result != null) {
                var detalle = result.get("detalle");
                if (detalle != null && detalle.isArray()) {
                    var summary = formatDetail(detalle);
                    var base    = result.hasNonNull("error") ? result.get("error").asText() : "Hay equipos no disponibles";
                    return "HTTP " + status + " - " + base + ": " + summary;
                }
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

    private String formatDetail(JsonNode detalle) {
        return StreamSupport.stream(detalle.spliterator(), false)
                .map(item -> item.path("serie").asText() + " (" + item.path("motivo").asText() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private Object readCatalog(String path, Object request) {
        try {
            var http = restClient.post()
                    .uri(baseUrl + path)
                    .header("X-API-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((req, res) -> new OdooHttpResult(
                            res.getStatusCode().value(),
                            new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
            return extractResult(http.status(), http.body());
        } catch (Exception e) {
            log.error("Failed reading Odoo catalog {}: {}", path, e.getMessage());
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
