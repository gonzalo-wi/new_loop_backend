package com.loop.new_loop_api.integrations.pac.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The example body of PAC's `specs/015-loop-driver-settlement/contract.md`, word for word: the only automatic
 * proof that LOOP reads what PAC sends while both sides are deployed apart.
 */
class PacSettlementResponseContractTest {

    private static final String CONTRACT_EXAMPLE = """
            {
              "routeNumber": 179,
              "date": "2026-11-05",
              "expectedCash": 90000.00,
              "expectedChecks": 0.00,
              "expectedWithholdings": 0.00,
              "expectedTotal": 90000.00,
              "depositedCash": 91500.00,
              "confirmedChecks": 0.00,
              "confirmedWithholdings": 0.00,
              "totalReceived": 91500.00,
              "difference": null,
              "differenceType": null,
              "statusGroup": "PENDING",
              "expectedUpdatedAt": "2026-11-05T13:10:00-03:00",
              "lastDepositAt": "2026-11-05T15:42:00-03:00"
            }
            """;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldReadTheContractExample() throws Exception {
        var response = mapper.readValue(CONTRACT_EXAMPLE, PacSettlementResponse.class);

        assertThat(response.routeNumber()).isEqualTo(179L);
        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(response.expectedTotal()).isEqualByComparingTo("90000.00");
        assertThat(response.depositedCash()).isEqualByComparingTo("91500.00");
        assertThat(response.difference()).isNull();
        assertThat(response.differenceType()).isNull();
        assertThat(response.statusGroup()).isEqualTo("PENDING");
        // Same instant as PAC's -03:00 time, whatever offset Jackson normalizes it to.
        assertThat(response.lastDepositAt().toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-11-05T15:42:00-03:00").toInstant());
    }

    @Test
    void shouldReadAFinalDifference() throws Exception {
        var body = CONTRACT_EXAMPLE.replace("\"difference\": null", "\"difference\": -1500.00")
                .replace("\"differenceType\": null", "\"differenceType\": \"SHORTAGE\"");

        var response = mapper.readValue(body, PacSettlementResponse.class);

        assertThat(response.difference()).isEqualByComparingTo("-1500.00");
        assertThat(response.differenceType()).isEqualTo("SHORTAGE");
    }
}
