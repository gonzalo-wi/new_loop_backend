package com.loop.new_loop_api.settlements.service;

import com.loop.new_loop_api.integrations.pac.client.PacSettlementClient;
import com.loop.new_loop_api.integrations.pac.dto.PacSettlementResponse;
import com.loop.new_loop_api.settlements.domain.PacLookup;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacSettlementGatewayTest {

    private static final LocalDate DAY   = LocalDate.of(2026, 11, 5);
    private static final Instant   START = Instant.parse("2026-11-05T18:00:00Z");
    private static final Request   REQUEST = Request.create(Request.HttpMethod.GET, "http://pac/api/integrations/loop/settlement",
            Map.of(), null, StandardCharsets.UTF_8, null);

    @Mock private PacSettlementClient client;

    private MutableClock clock;
    private PacSettlementGateway gateway;

    @BeforeEach
    void setUp() {
        clock   = new MutableClock(START);
        gateway = new PacSettlementGateway(client, clock, "loop", "s3cret");
    }

    @Test
    void shouldReturnPacFiguresSendingBasicCredentials() {
        when(client.getSettlement(anyString(), eq(179L), eq(DAY))).thenReturn(response());

        var lookup = gateway.lookup(179, DAY);

        assertThat(lookup).isInstanceOf(PacLookup.Found.class);
        var figures = ((PacLookup.Found) lookup).figures();
        assertThat(figures.expectedTotal()).isEqualByComparingTo("90000.00");
        assertThat(figures.depositedCash()).isEqualByComparingTo("91500.00");
        assertThat(figures.statusGroup()).isEqualTo("PENDING");
        assertThat(figures.cashDifference()).isEqualByComparingTo("1500.00");
        assertThat(figures.depositsLive()).isTrue();
        var expected = "Basic " + Base64.getEncoder().encodeToString("loop:s3cret".getBytes(StandardCharsets.UTF_8));
        verify(client).getSettlement(expected, 179L, DAY);
    }

    @Test
    void shouldKeepAnAnswerForTenSeconds() {
        when(client.getSettlement(anyString(), anyLong(), any())).thenReturn(response());

        gateway.lookup(179, DAY);
        clock.advanceSeconds(9);
        gateway.lookup(179, DAY);
        verify(client, times(1)).getSettlement(anyString(), anyLong(), any());

        clock.advanceSeconds(1);
        gateway.lookup(179, DAY);
        verify(client, times(2)).getSettlement(anyString(), anyLong(), any());
    }

    @Test
    void shouldCacheEachRouteAndDayApart() {
        when(client.getSettlement(anyString(), anyLong(), any())).thenReturn(response());

        gateway.lookup(179, DAY);
        gateway.lookup(180, DAY);
        gateway.lookup(179, DAY.plusDays(1));

        verify(client, times(3)).getSettlement(anyString(), anyLong(), any());
    }

    @Test
    void shouldTreatA404AsNotLoadedAndKeepIt() {
        when(client.getSettlement(anyString(), anyLong(), any()))
                .thenThrow(new FeignException.NotFound("not found", REQUEST, null, null));

        assertThat(gateway.lookup(179, DAY)).isInstanceOf(PacLookup.NotLoaded.class);
        assertThat(gateway.lookup(179, DAY)).isInstanceOf(PacLookup.NotLoaded.class);
        verify(client, times(1)).getSettlement(anyString(), anyLong(), any());
    }

    @Test
    void shouldNotKeepFailuresSoARefreshRetries() {
        when(client.getSettlement(anyString(), anyLong(), any()))
                .thenThrow(new FeignException.InternalServerError("boom", REQUEST, null, null))
                .thenThrow(new RuntimeException("connection refused"))
                .thenReturn(response());

        assertThat(gateway.lookup(179, DAY)).isInstanceOf(PacLookup.Unavailable.class);
        assertThat(gateway.lookup(179, DAY)).isInstanceOf(PacLookup.Unavailable.class);
        assertThat(gateway.lookup(179, DAY)).isInstanceOf(PacLookup.Found.class);
    }

    @Test
    void shouldTreatAnEmptyBodyAsUnavailable() {
        when(client.getSettlement(anyString(), anyLong(), any())).thenReturn(null);

        assertThat(gateway.lookup(179, DAY)).isInstanceOf(PacLookup.Unavailable.class);
    }

    @Test
    void shouldNeverCallPacWithoutCredentials() {
        var unconfigured = new PacSettlementGateway(client, clock, " ", "");

        assertThat(unconfigured.lookup(179, DAY)).isInstanceOf(PacLookup.Unavailable.class);
        verifyNoInteractions(client);
    }

    private static PacSettlementResponse response() {
        var at = OffsetDateTime.of(2026, 11, 5, 15, 42, 0, 0, ZoneOffset.ofHours(-3));
        return new PacSettlementResponse(179L, DAY, new BigDecimal("90000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("90000.00"), new BigDecimal("91500.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("91500.00"), null, null, "PENDING", at, at, new BigDecimal("1500.00"), at, true);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override public ZoneId getZone() { return ZoneId.of("America/Argentina/Buenos_Aires"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
