package com.loop.new_loop_api.integrations.aguas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.integrations.aguas.client.AguasClient;
import com.loop.new_loop_api.integrations.aguas.dto.AguasEntryRequest;
import com.loop.new_loop_api.integrations.aguas.mapper.AguasRequestMapper;
import com.loop.new_loop_api.integrations.common.entity.IntegrationLog;
import com.loop.new_loop_api.integrations.common.entity.IntegrationStatus;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.stockcontrols.entity.ControlStatus;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import com.loop.new_loop_api.stockcontrols.repository.StockControlRepository;
import com.loop.new_loop_api.users.repository.UserRepository;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AguasIntegrationServiceImplTest {

    @Mock private StockControlRepository   stockControlRepository;
    @Mock private UserRepository           userRepository;
    @Mock private IntegrationLogRepository integrationLogRepository;
    @Mock private AguasClient              aguasClient;
    @Mock private AguasRequestMapper       aguasRequestMapper;
    @Mock private AuditService             auditService;

    private AguasIntegrationServiceImpl aguasIntegrationService;

    private UUID controlId;
    private StockControl control;

    @BeforeEach
    void setUp() {
        aguasIntegrationService = new AguasIntegrationServiceImpl(
                stockControlRepository, userRepository, integrationLogRepository,
                aguasClient, aguasRequestMapper, new ObjectMapper(), auditService);

        controlId = UUID.randomUUID();
        control = StockControl.builder()
                .id(controlId)
                .type(ControlType.ENTRY)
                .status(ControlStatus.SENT_TO_AGUAS)
                .branch(Branch.builder().id(UUID.randomUUID()).name("Branch 1").code("B1").build())
                .route(Route.builder().id(UUID.randomUUID()).code("1").build())
                .controlDate(LocalDate.now())
                .items(Collections.emptyList())
                .build();
    }

    private Response successResponse() {
        var body = "{\"data\":{\"formulario\":\"R202\",\"nroremito\":356104}}";
        return Response.builder()
                .status(200)
                .reason("OK")
                .request(Request.create(Request.HttpMethod.POST, "/api/aguas/products/in",
                        java.util.Map.of(), null, StandardCharsets.UTF_8, null))
                .body(body, StandardCharsets.UTF_8)
                .build();
    }

    // 6a. send() skips (does NOT call the client) when the control is already SENT_TO_AGUAS
    @Test
    void should_skipSend_when_controlAlreadySentToAguas() {
        when(stockControlRepository.findById(controlId)).thenReturn(Optional.of(control));

        aguasIntegrationService.send(controlId);

        verify(aguasClient, never()).sendEntry(any());
        verify(integrationLogRepository, never()).save(any());
        verify(stockControlRepository, never()).save(any());
    }

    // 6b. resend() does NOT skip when the control is SENT_TO_AGUAS — it re-posts and re-applies the remito data
    @Test
    void should_resendAndReapplyRemitoData_when_controlAlreadySentToAguas() {
        var entryRequest = AguasEntryRequest.builder().build();

        when(stockControlRepository.findById(controlId)).thenReturn(Optional.of(control));
        when(aguasRequestMapper.toEntryRequest(any(), any())).thenReturn(entryRequest);
        when(aguasClient.sendEntry(entryRequest)).thenReturn(successResponse());
        when(stockControlRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        aguasIntegrationService.resend(controlId);

        verify(aguasClient).sendEntry(entryRequest);

        var controlCaptor = ArgumentCaptor.forClass(StockControl.class);
        verify(stockControlRepository).save(controlCaptor.capture());
        var savedControl = controlCaptor.getValue();
        assertThat(savedControl.getStatus()).isEqualTo(ControlStatus.SENT_TO_AGUAS);
        assertThat(savedControl.getAguasFormulario()).isEqualTo("R202");
        assertThat(savedControl.getAguasNroRemito()).isEqualTo(356104L);

        var logCaptor = ArgumentCaptor.forClass(IntegrationLog.class);
        verify(integrationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(IntegrationStatus.SENT);
        assertThat(logCaptor.getValue().getOperationType()).isEqualTo("IN");
    }

    @Test
    void should_doNothing_when_resendCalledForNonExistentControl() {
        when(stockControlRepository.findById(controlId)).thenReturn(Optional.empty());

        aguasIntegrationService.resend(controlId);

        verify(aguasClient, never()).sendEntry(any());
        verify(integrationLogRepository, never()).save(any());
    }

    @Test
    void should_markControlAsAguasError_when_resendReceivesErrorResponse() {
        var entryRequest = AguasEntryRequest.builder().build();
        var errorResponse = Response.builder()
                .status(500)
                .reason("Internal Server Error")
                .request(Request.create(Request.HttpMethod.POST, "/api/aguas/products/in",
                        java.util.Map.of(), null, StandardCharsets.UTF_8, null))
                .body("{\"error\":\"boom\"}", StandardCharsets.UTF_8)
                .build();

        when(stockControlRepository.findById(controlId)).thenReturn(Optional.of(control));
        when(aguasRequestMapper.toEntryRequest(any(), any())).thenReturn(entryRequest);
        when(aguasClient.sendEntry(entryRequest)).thenReturn(errorResponse);
        when(stockControlRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        aguasIntegrationService.resend(controlId);

        var controlCaptor = ArgumentCaptor.forClass(StockControl.class);
        verify(stockControlRepository).save(controlCaptor.capture());
        assertThat(controlCaptor.getValue().getStatus()).isEqualTo(ControlStatus.AGUAS_ERROR);
    }
}
