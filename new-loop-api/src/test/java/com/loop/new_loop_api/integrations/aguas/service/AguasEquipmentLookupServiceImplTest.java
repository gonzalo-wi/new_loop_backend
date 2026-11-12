package com.loop.new_loop_api.integrations.aguas.service;

import com.loop.new_loop_api.integrations.aguas.client.AguasEquipmentLookupClient;
import com.loop.new_loop_api.integrations.aguas.dto.AguasEquipmentCheck;
import com.loop.new_loop_api.integrations.aguas.dto.AguasEquipmentLookupResponse;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.metrics.IntegrationCallMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AguasEquipmentLookupServiceImplTest {

    @Mock private AguasEquipmentLookupClient client;
    @Mock private IntegrationCallMetrics     integrationCallMetrics;

    private AguasEquipmentLookupServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AguasEquipmentLookupServiceImpl(client, integrationCallMetrics, Runnable::run, Duration.ofSeconds(5));
    }

    @Test
    void shouldReportExistingAndMissingSerialsWithTheirAguasData() {
        when(client.findBySerial("SN-1")).thenReturn(found("SN-1", "EN CAMIONETA", "OPERATIVO"));
        when(client.findBySerial("SN-2")).thenReturn(missing("SN-2"));

        var checks = service.check(List.of("SN-1", "SN-2"));

        assertThat(checks).containsExactly(
                new AguasEquipmentCheck("SN-1", true, "SN-1", "EN CAMIONETA", "OPERATIVO"),
                new AguasEquipmentCheck("SN-2", false, "SN-2", null, null));
        verify(integrationCallMetrics, times(2)).recordSuccess(IntegrationName.AGUAS);
    }

    @Test
    void shouldReportUnknown_when_theLookupFailsForASerial() {
        when(client.findBySerial("SN-1")).thenThrow(new RuntimeException("connect timed out"));
        when(client.findBySerial("SN-2")).thenReturn(missing("SN-2"));

        var checks = service.check(List.of("SN-1", "SN-2"));

        assertThat(checks).containsExactly(
                new AguasEquipmentCheck("SN-1", null, null, null, null),
                new AguasEquipmentCheck("SN-2", false, "SN-2", null, null));
        verify(integrationCallMetrics).recordError(IntegrationName.AGUAS);
    }

    @Test
    void shouldReportUnknown_when_aguasAnswersWithoutTheExisteFlag() {
        when(client.findBySerial("SN-1")).thenReturn(new AguasEquipmentLookupResponse(false, null));

        assertThat(service.check(List.of("SN-1")))
                .containsExactly(new AguasEquipmentCheck("SN-1", null, null, null, null));
    }

    @Test
    void shouldLookUpEachSerialOnceTrimmed_when_theListRepeatsIt() {
        when(client.findBySerial("SN-1")).thenReturn(found("SN-1", "EN CLIENTE", "OPERATIVO"));

        var checks = service.check(List.of(" SN-1 ", "SN-1"));

        assertThat(checks).extracting(AguasEquipmentCheck::serie).containsExactly("SN-1");
        verify(client, times(1)).findBySerial("SN-1");
    }

    @Test
    void shouldReturnOnlyMissingSerialsNormalized_when_findingMissingOnes() {
        when(client.findBySerial("sn-1")).thenReturn(missing("sn-1"));
        when(client.findBySerial("SN-2")).thenReturn(found("SN-2", "EN CLIENTE", "OPERATIVO"));
        when(client.findBySerial("SN-3")).thenThrow(new RuntimeException("HTTP 500"));

        assertThat(service.findMissingSerials(List.of("sn-1", "SN-2", "SN-3"))).containsExactly("SN-1");
    }

    @Test
    void shouldReportUnknown_when_aLookupOutlivesTheDeadline() throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        try {
            var slow = new AguasEquipmentLookupServiceImpl(client, integrationCallMetrics, pool, Duration.ofMillis(50));
            when(client.findBySerial("SN-1")).thenAnswer(inv -> {
                Thread.sleep(1_000);
                return missing("SN-1");
            });

            assertThat(slow.check(List.of("SN-1")))
                    .containsExactly(new AguasEquipmentCheck("SN-1", null, null, null, null));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void shouldNotCallAguas_when_thereAreNoSerials() {
        assertThat(service.check(List.of())).isEmpty();
        assertThat(service.findMissingSerials(List.of())).isEmpty();
    }

    private static AguasEquipmentLookupResponse found(String serial, String ubicacion, String estado) {
        var equipo = new AguasEquipmentLookupResponse.Equipo(ubicacion, estado);
        return new AguasEquipmentLookupResponse(true, new AguasEquipmentLookupResponse.Data(true, serial, equipo));
    }

    private static AguasEquipmentLookupResponse missing(String serial) {
        return new AguasEquipmentLookupResponse(true, new AguasEquipmentLookupResponse.Data(false, serial, null));
    }
}
