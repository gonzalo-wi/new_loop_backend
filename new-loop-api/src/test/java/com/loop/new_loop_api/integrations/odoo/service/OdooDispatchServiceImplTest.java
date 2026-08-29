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
import com.loop.new_loop_api.integrations.odoo.mapper.OdooDispatchMapper;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * Unit tests for OdooDispatchServiceImpl. The service builds its RestClient with a hardcoded
 * {@code RestClient.create()} field, so we bind a {@link MockRestServiceServer} to a builder,
 * build a client and inject it into the (final) {@code restClient} field via reflection. This lets
 * us exercise the real {@code .exchange(...)} fluent chain and JSON parsing without changing logic.
 */
@ExtendWith(MockitoExtension.class)
class OdooDispatchServiceImplTest {

    private static final String BASE_URL = "https://odoo.test";
    private static final String API_KEY  = "secret-key";
    private static final String CREATE_URL    = BASE_URL + "/api/v1/salida/create";
    private static final String AVAILABLE_URL  = BASE_URL + "/api/v1/salida/disponibles";
    private static final String VALIDATE_URL   = BASE_URL + "/api/v1/salida/validar";

    @Mock private DispenserMovementRepository dispenserMovementRepository;
    @Mock private IntegrationLogRepository    integrationLogRepository;
    @Mock private AuditService                auditService;
    @Mock private IntegrationCallMetrics      integrationCallMetrics;

    private OdooDispatchServiceImpl service;
    private MockRestServiceServer   server;

    private UUID movementId;
    private DispenserMovement movement;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient boundClient = builder.build();

        service = new OdooDispatchServiceImpl(
                dispenserMovementRepository, integrationLogRepository,
                new OdooDispatchMapper(), new ObjectMapper(), auditService, integrationCallMetrics);

        ReflectionTestUtils.setField(service, "restClient", boundClient);
        ReflectionTestUtils.setField(service, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(service, "apiKey", API_KEY);

        movementId = UUID.randomUUID();
        movement = DispenserMovement.builder()
                .id(movementId)
                .type(DispenserMovementType.LOAD)
                .status(DispenserMovementStatus.SENT_TO_AGUAS)
                .routeCode("R1")
                .technician("Tech 1")
                .movementDate(LocalDate.of(2026, 8, 20))
                .serials(List.of("SN-1", "SN-2"))
                .build();
    }

    // ---------- send() happy path ----------

    @Test
    void should_markSentAndRecordSuccess_when_odooReturnsSuccess() {
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        server.expect(requestTo(CREATE_URL))
                .andExpect(method(POST))
                .andExpect(header("X-API-Key", API_KEY))
                .andRespond(withSuccess(
                        "{\"result\":{\"success\":true,\"picking_id\":42,\"picking_name\":\"WH/OUT/0001\"}}",
                        MediaType.APPLICATION_JSON));

        service.send(movementId);
        server.verify();

        assertThat(movement.getOdooStatus()).isEqualTo("SENT");
        assertThat(movement.getOdooPickingId()).isEqualTo(42);
        assertThat(movement.getOdooPickingName()).isEqualTo("WH/OUT/0001");
        assertThat(movement.getOdooReference()).isNotBlank();

        var logCaptor = ArgumentCaptor.forClass(IntegrationLog.class);
        verify(integrationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(IntegrationStatus.SENT);
        assertThat(logCaptor.getValue().getErrorMessage()).isNull();

        verify(integrationCallMetrics).recordSuccess(IntegrationName.ODOO);
        verify(integrationCallMetrics, never()).recordError(any());
        verify(auditService).register(any(), any(), any(), any(), any());
    }

    @Test
    void should_persistDeterministicReference_when_send() {
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        server.expect(requestTo(CREATE_URL))
                .andRespond(withSuccess("{\"result\":{\"success\":true}}", MediaType.APPLICATION_JSON));

        service.send(movementId);

        var expectedRef = new OdooDispatchMapper().buildExternalReference(movement);
        assertThat(movement.getOdooReference()).isEqualTo(expectedRef);
    }

    @Test
    void should_markSentWithoutError_when_odooReturnsAlreadyRegistered() {
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        server.expect(requestTo(CREATE_URL))
                .andRespond(withSuccess(
                        "{\"result\":{\"success\":true,\"ya_registrado\":true,\"picking_id\":42,\"picking_name\":\"WH/OUT/0001\"}}",
                        MediaType.APPLICATION_JSON));

        service.send(movementId);
        server.verify();

        assertThat(movement.getOdooStatus()).isEqualTo("SENT");

        var logCaptor = ArgumentCaptor.forClass(IntegrationLog.class);
        verify(integrationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(IntegrationStatus.SENT);
        assertThat(logCaptor.getValue().getErrorMessage()).isNull();
        verify(integrationCallMetrics).recordSuccess(IntegrationName.ODOO);
        verify(integrationCallMetrics, never()).recordError(any());
    }

    // ---------- send() error paths ----------

    @Test
    void should_closeAsCancelledWithDetail_when_noneOfTheEquiposAreAvailable() {
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        server.expect(requestTo(CREATE_URL))
                .andRespond(withSuccess(
                        "{\"result\":{\"success\":false,\"error\":\"Hay equipos no disponibles\","
                                + "\"detalle\":[{\"serie\":\"SN-1\",\"motivo\":\"no en expedicion\"},"
                                + "{\"serie\":\"SN-2\",\"motivo\":\"ya despachado\"}]}}",
                        MediaType.APPLICATION_JSON));

        service.send(movementId);
        server.verify();

        assertThat(movement.getOdooStatus()).isEqualTo("ERROR");

        var logCaptor = ArgumentCaptor.forClass(IntegrationLog.class);
        verify(integrationLogRepository).save(logCaptor.capture());
        var errorMessage = logCaptor.getValue().getErrorMessage();
        // Ningún equipo disponible: se cierra terminal (CANCELLED) para que el scheduler no reintente.
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(IntegrationStatus.CANCELLED);
        assertThat(errorMessage)
                .contains("Hay equipos no disponibles")
                .contains("SN-1 (no en expedicion)")
                .contains("SN-2 (ya despachado)");

        verify(integrationCallMetrics).recordError(IntegrationName.ODOO);
        verify(integrationCallMetrics, never()).recordSuccess(any());
    }

    @Test
    void should_retryWithAvailableEquipos_when_odooRejectsOnlySome() {
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Primera llamada: Odoo rechaza el lote porque SN-1 no está disponible.
        server.expect(requestTo(CREATE_URL))
                .andRespond(withSuccess(
                        "{\"result\":{\"success\":false,\"error\":\"Hay equipos no disponibles\","
                                + "\"detalle\":[{\"serie\":\"SN-1\",\"motivo\":\"no en expedicion\"}]}}",
                        MediaType.APPLICATION_JSON));
        // Segunda llamada: se reintenta solo con SN-2 y Odoo lo acepta.
        server.expect(requestTo(CREATE_URL))
                .andRespond(withSuccess(
                        "{\"result\":{\"success\":true,\"picking_id\":7,\"picking_name\":\"WH/OUT/0007\"}}",
                        MediaType.APPLICATION_JSON));

        service.send(movementId);
        server.verify();

        assertThat(movement.getOdooStatus()).isEqualTo("SENT");
        assertThat(movement.getOdooPickingId()).isEqualTo(7);

        var logCaptor = ArgumentCaptor.forClass(IntegrationLog.class);
        verify(integrationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(IntegrationStatus.SENT);
        // El request final guardado es el reintento: solo SN-2, sin SN-1.
        assertThat(logCaptor.getValue().getRequestPayload())
                .contains("SN-2")
                .doesNotContain("SN-1");

        verify(integrationCallMetrics).recordSuccess(IntegrationName.ODOO);
        verify(integrationCallMetrics, never()).recordError(any());
    }

    @Test
    void should_markError_when_odooReturnsHttp500() {
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));
        when(integrationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dispenserMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        server.expect(requestTo(CREATE_URL)).andRespond(withServerError());

        service.send(movementId);
        server.verify();

        assertThat(movement.getOdooStatus()).isEqualTo("ERROR");
        verify(integrationCallMetrics).recordError(IntegrationName.ODOO);
    }

    // ---------- send() guards ----------

    @Test
    void should_doNothing_when_movementDoesNotExist() {
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.empty());

        service.send(movementId);

        verifyNoInteractions(integrationCallMetrics);
        verify(integrationLogRepository, never()).save(any());
        verify(dispenserMovementRepository, never()).save(any());
    }

    @Test
    void should_skip_when_movementTypeIsUnload() {
        movement.setType(DispenserMovementType.UNLOAD);
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));

        service.send(movementId);

        verifyNoInteractions(integrationCallMetrics);
        verify(integrationLogRepository, never()).save(any());
        verify(dispenserMovementRepository, never()).save(any());
        assertThat(movement.getOdooStatus()).isNull();
    }

    @Test
    void should_skip_when_movementNotYetSentToAguas() {
        movement.setStatus(DispenserMovementStatus.REGISTERED);
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));

        service.send(movementId);

        verifyNoInteractions(integrationCallMetrics);
        verify(integrationLogRepository, never()).save(any());
        assertThat(movement.getOdooStatus()).isNull();
    }

    @Test
    void should_skip_when_movementAlreadySentToOdoo() {
        movement.setOdooStatus("SENT");
        when(dispenserMovementRepository.findById(movementId)).thenReturn(Optional.of(movement));

        service.send(movementId);

        verifyNoInteractions(integrationCallMetrics);
        verify(integrationLogRepository, never()).save(any());
    }

    // ---------- getAvailableEquipment() / validateEquipment() (read-only) ----------

    @Test
    void should_returnParsedResult_when_getAvailableEquipmentSucceeds() {
        server.expect(requestTo(AVAILABLE_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"result\":{\"equipos\":[\"SN-9\"],\"total\":1}}", MediaType.APPLICATION_JSON));

        var result = service.getAvailableEquipment(null, null);
        server.verify();

        assertThat(result).isNotNull();
        assertThat(result.toString()).contains("SN-9");
    }

    @Test
    void should_returnNull_when_getAvailableEquipmentFailsWithNetworkError() {
        server.expect(requestTo(AVAILABLE_URL)).andRespond(withServerError());

        var result = service.getAvailableEquipment(null, null);
        server.verify();

        assertThat(result).isNull();
    }

    @Test
    void should_returnParsedResult_when_validateEquipmentSucceeds() {
        server.expect(requestTo(VALIDATE_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"result\":{\"valido\":true}}", MediaType.APPLICATION_JSON));

        var result = service.validateEquipment(List.of("SN-1"));
        server.verify();

        assertThat(result).isNotNull();
        assertThat(result.toString()).contains("valido");
    }

    @Test
    void should_returnNull_when_validateEquipmentReturnsEmptyBody() {
        server.expect(requestTo(VALIDATE_URL))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        var result = service.validateEquipment(List.of("SN-1"));
        server.verify();

        assertThat(result).isNull();
    }

    // ---------- findUnavailableEquipos() ----------

    @Test
    void should_returnOnlyUnavailableSeries_when_findUnavailableEquipos() {
        server.expect(requestTo(VALIDATE_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"result\":{\"equipos\":["
                                + "{\"serie\":\"SN-1\",\"disponible\":true},"
                                + "{\"serie\":\"SN-2\",\"disponible\":false,\"motivo\":\"no existe en Odoo\"}]}}",
                        MediaType.APPLICATION_JSON));

        var unavailable = service.findUnavailableEquipos(List.of("SN-1", "SN-2"));
        server.verify();

        assertThat(unavailable).containsExactly("SN-2");
    }

    @Test
    void should_returnEmpty_when_allEquiposAvailable() {
        server.expect(requestTo(VALIDATE_URL))
                .andRespond(withSuccess(
                        "{\"result\":{\"equipos\":[{\"serie\":\"SN-1\",\"disponible\":true}]}}",
                        MediaType.APPLICATION_JSON));

        var unavailable = service.findUnavailableEquipos(List.of("SN-1"));
        server.verify();

        assertThat(unavailable).isEmpty();
    }

    @Test
    void should_returnEmptyWithoutCallingOdoo_when_equiposListIsEmpty() {
        var unavailable = service.findUnavailableEquipos(List.of());

        assertThat(unavailable).isEmpty();
    }

    @Test
    void should_returnEmpty_when_validationFailsSoLoadIsNotBlocked() {
        server.expect(requestTo(VALIDATE_URL)).andRespond(withServerError());

        var unavailable = service.findUnavailableEquipos(List.of("SN-1"));
        server.verify();

        assertThat(unavailable).isEmpty();
    }
}
