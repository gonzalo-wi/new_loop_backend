package com.loop.new_loop_api.integrations.odoo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.dispensers.repository.DispenserMovementRepository;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.metrics.IntegrationCallMetrics;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import com.loop.new_loop_api.integrations.odoo.mapper.OdooRepairMapper;
import com.loop.new_loop_api.performance.service.OperationEventRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Same RestClient-injection approach as OdooNoNormalizadoServiceImplTest (the client is a hardcoded final field). */
@ExtendWith(MockitoExtension.class)
class OdooRepairServiceImplTest {

    private static final String BASE_URL   = "https://odoo.test";
    private static final String CREATE_URL = BASE_URL + "/api/v1/repair/create";

    @Mock private DispenserMovementRepository dispenserMovementRepository;
    @Mock private IntegrationLogRepository    integrationLogRepository;
    @Mock private AuditService                auditService;
    @Mock private IntegrationCallMetrics      integrationCallMetrics;
    @Mock private OperationEventRecorder      operationEventRecorder;

    private OdooRepairServiceImpl service;
    private MockRestServiceServer server;

    private DispenserMovement movement;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        service = new OdooRepairServiceImpl(
                dispenserMovementRepository, integrationLogRepository,
                new OdooRepairMapper(), new ObjectMapper(), auditService, integrationCallMetrics, operationEventRecorder);

        ReflectionTestUtils.setField(service, "restClient", builder.build());
        ReflectionTestUtils.setField(service, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(service, "apiKey", "secret-key");

        movement = DispenserMovement.builder()
                .id(UUID.randomUUID())
                .type(DispenserMovementType.UNLOAD)
                .status(DispenserMovementStatus.SENT_TO_AGUAS)
                .routeCode("199")
                .technician("Tech 1")
                .registeredByUsername("user1")
                .movementDate(LocalDate.of(2026, 10, 28))
                .serials(new ArrayList<>(List.of("SN-1")))
                .build();
        when(dispenserMovementRepository.findById(movement.getId())).thenReturn(Optional.of(movement));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldRecordIntegrationErrorEvent_when_odooRespondsServerError() {
        server.expect(requestTo(CREATE_URL)).andRespond(withServerError());

        service.send(movement.getId());

        assertThat(movement.getOdooStatus()).isEqualTo("ERROR");
        verify(integrationCallMetrics).recordError(IntegrationName.ODOO);
        verify(operationEventRecorder).integrationError(IntegrationName.ODOO, movement);
    }

    @Test
    void shouldNotRecordIntegrationErrorEvent_when_odooCreatesTheRepair() {
        server.expect(requestTo(CREATE_URL)).andRespond(withSuccess(
                "{\"result\":{\"success\":true,\"picking_id\":5,\"picking_name\":\"AC/REP/00005\"}}",
                MediaType.APPLICATION_JSON));

        service.send(movement.getId());

        assertThat(movement.getOdooStatus()).isEqualTo("SENT");
        verify(integrationCallMetrics).recordSuccess(IntegrationName.ODOO);
        verify(operationEventRecorder, never()).integrationError(any(), any(DispenserMovement.class));
    }
}
