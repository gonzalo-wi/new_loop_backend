package com.loop.new_loop_api.integrations.odoo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.dispensers.repository.DispenserMovementRepository;
import com.loop.new_loop_api.integrations.common.entity.IntegrationLog;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.integrations.common.metrics.IntegrationCallMetrics;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import com.loop.new_loop_api.integrations.odoo.mapper.OdooRepairMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Same RestClient-injection approach as OdooDispatchServiceImplTest (the client is a hardcoded final field). */
@ExtendWith(MockitoExtension.class)
class OdooNoNormalizadoServiceImplTest {

    private static final String BASE_URL   = "https://odoo.test";
    private static final String API_KEY    = "secret-key";
    private static final String CREATE_URL = BASE_URL + "/api/v1/equipos/no-normalizados";

    @Mock private DispenserMovementRepository dispenserMovementRepository;
    @Mock private IntegrationLogRepository    integrationLogRepository;
    @Mock private AuditService                auditService;
    @Mock private IntegrationCallMetrics      integrationCallMetrics;

    private OdooNoNormalizadoServiceImpl service;
    private MockRestServiceServer        server;

    private UUID              movementId;
    private DispenserMovement movement;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        service = new OdooNoNormalizadoServiceImpl(
                dispenserMovementRepository, integrationLogRepository,
                new OdooRepairMapper(), new ObjectMapper(), auditService, integrationCallMetrics);

        ReflectionTestUtils.setField(service, "restClient", builder.build());
        ReflectionTestUtils.setField(service, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(service, "apiKey", API_KEY);

        movementId = UUID.randomUUID();
        movement = DispenserMovement.builder()
                .id(movementId)
                .type(DispenserMovementType.UNLOAD)
                .status(DispenserMovementStatus.REGISTERED)
                .routeCode("199")
                .technician("Tech 1")
                .registeredByUsername("user1")
                .movementDate(LocalDate.of(2026, 9, 21))
                .serials(new ArrayList<>(List.of("SN-OK", "SN-NN")))
                .excludedSerials(new ArrayList<>(List.of("SN-NN")))
                .build();
    }

    @Test
    void should_sendOnlyExcludedSerialsAndMarkSent_when_odooReturnsSuccess() {
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        server.expect(requestTo(CREATE_URL))
                .andExpect(method(POST))
                .andExpect(header("X-API-Key", API_KEY))
                .andExpect(jsonPath("$.params.equipos.length()").value(1))
                .andExpect(jsonPath("$.params.equipos[0]").value("SN-NN"))
                .andExpect(jsonPath("$.params.idreparto").value("199"))
                .andExpect(jsonPath("$.params.fecha").value("2026-09-21"))
                .andRespond(withSuccess(
                        "{\"jsonrpc\":\"2.0\",\"id\":null,\"result\":{\"success\":true,\"picking_id\":123,"
                                + "\"picking_name\":\"AC/FCNN/00001\",\"seriales_procesados\":1}}",
                        MediaType.APPLICATION_JSON));

        service.send(movementId);
        server.verify();

        assertThat(movement.getOdooNoNormalizadoStatus()).isEqualTo("SENT");
        assertThat(movement.getOdooNoNormalizadoPickingId()).isEqualTo(123);
        assertThat(movement.getOdooNoNormalizadoPickingName()).isEqualTo("AC/FCNN/00001");
        assertThat(movement.getOdooStatus()).isNull(); // repair tracking untouched

        var logCaptor = ArgumentCaptor.forClass(IntegrationLog.class);
        verify(integrationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(IntegrationStatus.SENT);
        assertThat(logCaptor.getValue().getOperationType()).isEqualTo(OdooNoNormalizadoServiceImpl.OPERATION);
        verify(integrationCallMetrics).recordSuccess(IntegrationName.ODOO);
    }

    @Test
    void should_markErrorWithOdooMessage_when_odooReturnsSuccessFalse() {
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        server.expect(requestTo(CREATE_URL))
                .andRespond(withSuccess(
                        "{\"result\":{\"success\":false,\"error\":\"Falta configurar la ubicacion de no normalizados\"}}",
                        MediaType.APPLICATION_JSON));

        service.send(movementId);

        assertThat(movement.getOdooNoNormalizadoStatus()).isEqualTo("ERROR");
        var logCaptor = ArgumentCaptor.forClass(IntegrationLog.class);
        verify(integrationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(IntegrationStatus.ERROR);
        assertThat(logCaptor.getValue().getErrorMessage()).contains("Falta configurar la ubicacion");
        verify(integrationCallMetrics).recordError(IntegrationName.ODOO);
    }

    @Test
    void should_markError_when_odooRespondsServerError() {
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        server.expect(requestTo(CREATE_URL)).andRespond(withServerError());

        service.send(movementId);

        assertThat(movement.getOdooNoNormalizadoStatus()).isEqualTo("ERROR");
        verify(integrationCallMetrics).recordError(IntegrationName.ODOO);
    }

    @Test
    void should_skip_when_movementHasNoExcludedSerials() {
        movement.getExcludedSerials().clear();
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));

        service.send(movementId);

        server.verify();
        verifyNoInteractions(integrationLogRepository, integrationCallMetrics);
    }

    @Test
    void should_skip_when_movementIsLoad() {
        movement.setType(DispenserMovementType.LOAD);
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));

        service.send(movementId);

        verifyNoInteractions(integrationLogRepository, integrationCallMetrics);
    }

    @Test
    void should_skip_when_movementWasCancelled() {
        movement.setStatus(DispenserMovementStatus.CANCELLED);
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));

        service.send(movementId);

        verifyNoInteractions(integrationLogRepository, integrationCallMetrics);
    }

    @Test
    void should_skip_when_alreadySent() {
        movement.setOdooNoNormalizadoStatus("SENT");
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));

        service.send(movementId);

        verifyNoInteractions(integrationLogRepository, integrationCallMetrics);
    }

    @Test
    void should_retryAndIncrementCount_when_logIsInError() {
        var logId = UUID.randomUUID();
        var integrationLog = IntegrationLog.builder()
                .id(logId)
                .integrationName(IntegrationName.ODOO)
                .operationType(OdooNoNormalizadoServiceImpl.OPERATION)
                .entityName("DispenserMovement")
                .entityId(movementId)
                .status(IntegrationStatus.ERROR)
                .retryCount(1)
                .build();
        when(integrationLogRepository.findById(logId)).thenReturn(Optional.of(integrationLog));
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        server.expect(requestTo(CREATE_URL))
                .andRespond(withSuccess("{\"result\":{\"success\":true,\"picking_id\":7,\"picking_name\":\"AC/FCNN/00007\"}}",
                        MediaType.APPLICATION_JSON));

        service.retry(logId);

        assertThat(integrationLog.getRetryCount()).isEqualTo(2);
        assertThat(integrationLog.getStatus()).isEqualTo(IntegrationStatus.SENT);
        assertThat(movement.getOdooNoNormalizadoStatus()).isEqualTo("SENT");
    }

    @Test
    void should_notRetry_when_logAlreadySent() {
        var logId = UUID.randomUUID();
        var integrationLog = IntegrationLog.builder()
                .id(logId)
                .entityId(movementId)
                .status(IntegrationStatus.SENT)
                .retryCount(0)
                .build();
        when(integrationLogRepository.findById(logId)).thenReturn(Optional.of(integrationLog));

        service.retry(logId);

        verify(dispenserMovementRepository, never()).findById(any());
    }
}
