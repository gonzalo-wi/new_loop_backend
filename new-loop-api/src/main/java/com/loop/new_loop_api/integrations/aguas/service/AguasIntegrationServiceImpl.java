package com.loop.new_loop_api.integrations.aguas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.integrations.aguas.client.AguasClient;
import com.loop.new_loop_api.integrations.aguas.mapper.AguasRequestMapper;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasIntegrationService;
import com.loop.new_loop_api.integrations.common.entity.IntegrationLog;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.integrations.common.exception.IntegrationLogNotFoundException;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.repository.StockControlRepository;
import com.loop.new_loop_api.users.entity.User;
import com.loop.new_loop_api.users.repository.UserRepository;
import feign.FeignException;
import feign.Response;
import feign.Util;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AguasIntegrationServiceImpl implements AguasIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(AguasIntegrationServiceImpl.class);

    private static final String OPERATION_IN  = "IN";
    private static final String OPERATION_OUT = "OUT";
    private static final String ENTITY_NAME   = "StockControl";

    private final StockControlRepository   stockControlRepository;
    private final UserRepository           userRepository;
    private final IntegrationLogRepository integrationLogRepository;
    private final AguasClient              aguasClient;
    private final AguasRequestMapper       aguasRequestMapper;
    private final ObjectMapper             objectMapper;
    private final AuditService             auditService;

    @Override
    @Transactional
    public void send(UUID controlId) {
        var control = stockControlRepository.findById(controlId).orElse(null);
        if (control == null) {
            log.warn("Aguas send skipped: stock control {} no longer exists", controlId);
            return;
        }
        if (control.getStatus() == ControlStatus.SENT_TO_AGUAS) {
            log.warn("Aguas send skipped: stock control {} was already sent", controlId);
            return;
        }
        var integrationLog = newLog(control);
        attempt(control, integrationLog);
    }

    @Override
    @Transactional
    public void retry(UUID logId) {
        var integrationLog = integrationLogRepository.findById(logId)
                .orElseThrow(() -> new IntegrationLogNotFoundException(logId));

        if (integrationLog.getStatus() == IntegrationStatus.SENT) {
            return;
        }
        var control = stockControlRepository.findById(integrationLog.getEntityId()).orElse(null);
        if (control == null) {
            log.warn("Aguas retry skipped: stock control {} no longer exists", integrationLog.getEntityId());
            return;
        }
        if (control.getStatus() == ControlStatus.SENT_TO_AGUAS) {
            log.warn("Aguas retry skipped: stock control {} was already sent", control.getId());
            return;
        }
        integrationLog.setRetryCount(integrationLog.getRetryCount() + 1);
        attempt(control, integrationLog);
    }

    private IntegrationLog newLog(StockControl control) {
        var operation = control.getType() == ControlType.ENTRY ? OPERATION_IN : OPERATION_OUT;
        return IntegrationLog.builder()
                .integrationName(IntegrationName.AGUAS)
                .operationType(operation)
                .entityName(ENTITY_NAME)
                .entityId(control.getId())
                .status(IntegrationStatus.PENDING)
                .retryCount(0)
                .build();
    }

    /** Builds the request, calls Aguas and records the outcome on both the log and the control. */
    private void attempt(StockControl control, IntegrationLog integrationLog) {
        Object request;
        Supplier<Response> call;

        if (control.getType() == ControlType.ENTRY) {
            var entryRequest = aguasRequestMapper.toEntryRequest(control, resolveSupervisor(control.getControllerId()));
            request = entryRequest;
            call    = () -> aguasClient.sendEntry(entryRequest);
        } else {
            var exitRequest = aguasRequestMapper.toExitRequest(control);
            request = exitRequest;
            call    = () -> aguasClient.sendExit(exitRequest);
        }

        var requestPayload = serialize(request);
        integrationLog.setRequestPayload(requestPayload);
        log.info("Aguas {} request for control {}: {}",
                integrationLog.getOperationType(), control.getId(), requestPayload);

        try (Response response = call.get()) {
            var body = readBody(response);
            if (isSuccessful(response.status())) {
                markSent(integrationLog, control, body);
            } else {
                markError(integrationLog, control, "HTTP " + response.status() + " - " + body);
            }
        } catch (Exception e) {
            markError(integrationLog, control, extractError(e));
        }
    }

    private void markSent(IntegrationLog integrationLog, StockControl control, String responseBody) {
        integrationLog.setStatus(IntegrationStatus.SENT);
        integrationLog.setResponsePayload(responseBody);
        integrationLog.setErrorMessage(null);
        integrationLog.setSentAt(LocalDateTime.now());

        control.setStatus(ControlStatus.SENT_TO_AGUAS);
        applyRemitoData(control, responseBody);
        persist(integrationLog, control);
        auditService.register("SEND_TO_AGUAS", ENTITY_NAME, control.getId(), null,
                Map.of("operation", integrationLog.getOperationType(), "status", IntegrationStatus.SENT));
        log.info("Aguas {} sent successfully for control {}. Response: {}",
                integrationLog.getOperationType(), control.getId(), responseBody);
    }

    private void markError(IntegrationLog integrationLog, StockControl control, String errorMessage) {
        integrationLog.setStatus(IntegrationStatus.ERROR);
        integrationLog.setErrorMessage(errorMessage);

        control.setStatus(ControlStatus.AGUAS_ERROR);
        persist(integrationLog, control);
        auditService.register("AGUAS_ERROR", ENTITY_NAME, control.getId(), null,
                Map.of("operation", integrationLog.getOperationType(), "error", errorMessage));
        log.error("Aguas {} failed for control {}: {}",
                integrationLog.getOperationType(), control.getId(), errorMessage);
    }

    /** Aguas returns { "data": { "formulario": "R202", "nroremito": 356104 } } on a successful send. */
    private void applyRemitoData(StockControl control, String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return;
        try {
            var data = objectMapper.readTree(responseBody).get("data");
            if (data == null) return;
            if (data.hasNonNull("formulario")) control.setAguasFormulario(data.get("formulario").asText());
            if (data.hasNonNull("nroremito"))   control.setAguasNroRemito(data.get("nroremito").asLong());
        } catch (Exception e) {
            log.warn("Could not parse Aguas remito data for control {}: {}", control.getId(), e.getMessage());
        }
    }

    private boolean isSuccessful(int status) {
        return status >= 200 && status < 300;
    }

    private String readBody(Response response) {
        if (response.body() == null) return null;
        try {
            return Util.toString(response.body().asReader(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private void persist(IntegrationLog integrationLog, StockControl control) {
        integrationLogRepository.save(integrationLog);
        stockControlRepository.save(control);
    }

    private String resolveSupervisor(UUID controllerId) {
        if (controllerId == null) return null;
        return userRepository.findById(controllerId)
                .map(User::getUsername)
                .orElse(null);
    }

    private String serialize(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    private String extractError(Exception e) {
        if (e instanceof FeignException feignException) {
            return "HTTP " + feignException.status() + " - " + feignException.contentUTF8();
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
