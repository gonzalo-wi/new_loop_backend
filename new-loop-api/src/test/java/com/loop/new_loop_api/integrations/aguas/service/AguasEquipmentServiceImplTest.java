package com.loop.new_loop_api.integrations.aguas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.dispensers.metrics.DispenserMovementMetrics;
import com.loop.new_loop_api.dispensers.repository.DispenserMovementRepository;
import com.loop.new_loop_api.integrations.aguas.client.AguasEquipmentClient;
import com.loop.new_loop_api.integrations.aguas.dto.AguasEquipmentMovementRequest;
import com.loop.new_loop_api.integrations.aguas.mapper.AguasEquipmentMapper;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.metrics.IntegrationCallMetrics;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AguasEquipmentServiceImplTest {

    @Mock private DispenserMovementRepository dispenserMovementRepository;
    @Mock private IntegrationLogRepository    integrationLogRepository;
    @Mock private AguasEquipmentClient        aguasEquipmentClient;
    @Mock private AguasEquipmentMapper        aguasEquipmentMapper;
    @Mock private AuditService                auditService;
    @Mock private ApplicationEventPublisher   eventPublisher;
    @Mock private DispenserMovementMetrics    dispenserMovementMetrics;
    @Mock private IntegrationCallMetrics      integrationCallMetrics;

    private AguasEquipmentServiceImpl aguasEquipmentService;

    private UUID movementId;
    private DispenserMovement movement;

    @BeforeEach
    void setUp() {
        aguasEquipmentService = new AguasEquipmentServiceImpl(
                dispenserMovementRepository, integrationLogRepository, aguasEquipmentClient,
                aguasEquipmentMapper, new ObjectMapper(), auditService, eventPublisher,
                dispenserMovementMetrics, integrationCallMetrics);

        movementId = UUID.randomUUID();
        movement = DispenserMovement.builder()
                .id(movementId)
                .type(DispenserMovementType.LOAD)
                .status(DispenserMovementStatus.REGISTERED)
                .routeCode("1")
                .technician("Tech 1")
                .movementDate(LocalDate.now())
                .serials(List.of("SN-1", "SN-2"))
                .build();
    }

    private Response successResponse() {
        return Response.builder()
                .status(200)
                .reason("OK")
                .request(Request.create(Request.HttpMethod.POST, "/api/aguas/registrar-salida-camion",
                        java.util.Map.of(), null, StandardCharsets.UTF_8, null))
                .body("{\"data\":[{\"idmovimiento\":789}]}", StandardCharsets.UTF_8)
                .build();
    }

    private Response errorResponse() {
        return Response.builder()
                .status(500)
                .reason("Internal Server Error")
                .request(Request.create(Request.HttpMethod.POST, "/api/aguas/registrar-salida-camion",
                        java.util.Map.of(), null, StandardCharsets.UTF_8, null))
                .body("{\"message\":\"boom\"}", StandardCharsets.UTF_8)
                .build();
    }

    @Test
    void should_recordSentMetricAndPublishEvent_when_sendSucceedsForLoad() {
        var request = AguasEquipmentMovementRequest.builder().build();

        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));
        when(aguasEquipmentMapper.toRequest(movement)).thenReturn(request);
        when(aguasEquipmentClient.registerTruckDeparture(request)).thenReturn(successResponse());
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        aguasEquipmentService.send(movementId);

        assertThat(movement.getStatus()).isEqualTo(DispenserMovementStatus.SENT_TO_AGUAS);
        assertThat(movement.getAguasMovementId()).isEqualTo("789");

        verify(dispenserMovementMetrics).recordSent(DispenserMovementType.LOAD);
        verify(integrationCallMetrics).recordSuccess(IntegrationName.AGUAS);
        verify(dispenserMovementMetrics, never()).recordRejected(any());
        // LOAD movements are not forwarded to Odoo.
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void should_publishSentToAguasEvent_when_sendSucceedsForUnload() {
        movement.setType(DispenserMovementType.UNLOAD);
        var request = AguasEquipmentMovementRequest.builder().build();

        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));
        when(aguasEquipmentMapper.toRequest(movement)).thenReturn(request);
        when(aguasEquipmentClient.registerTruckReturn(request)).thenReturn(successResponse());
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        aguasEquipmentService.send(movementId);

        verify(dispenserMovementMetrics).recordSent(DispenserMovementType.UNLOAD);
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void should_recordRejectedMetric_when_aguasReturnsErrorResponse() {
        var request = AguasEquipmentMovementRequest.builder().build();

        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));
        when(aguasEquipmentMapper.toRequest(movement)).thenReturn(request);
        when(aguasEquipmentClient.registerTruckDeparture(request)).thenReturn(errorResponse());
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        aguasEquipmentService.send(movementId);

        assertThat(movement.getStatus()).isEqualTo(DispenserMovementStatus.AGUAS_ERROR);
        verify(dispenserMovementMetrics).recordRejected(DispenserMovementType.LOAD);
        verify(integrationCallMetrics).recordError(IntegrationName.AGUAS);
        verify(dispenserMovementMetrics, never()).recordSent(any());
    }

    @Test
    void should_notRecordAnyMetric_when_movementAlreadySentToAguas() {
        movement.setStatus(DispenserMovementStatus.SENT_TO_AGUAS);
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));

        aguasEquipmentService.send(movementId);

        verify(aguasEquipmentClient, never()).registerTruckDeparture(any());
        verify(dispenserMovementMetrics, never()).recordSent(any());
        verify(dispenserMovementMetrics, never()).recordRejected(any());
        verify(integrationCallMetrics, never()).recordSuccess(any());
        verify(integrationCallMetrics, never()).recordError(any());
    }
}
