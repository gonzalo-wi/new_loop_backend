package com.loop.new_loop_api.integrations.pac.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** PAC's answer, as fixed in PAC `specs/015-loop-driver-settlement/contract.md`. */
public record PacSettlementResponse(
        Long routeNumber,
        LocalDate date,
        BigDecimal expectedCash,
        BigDecimal expectedChecks,
        BigDecimal expectedWithholdings,
        BigDecimal expectedTotal,
        BigDecimal depositedCash,
        BigDecimal confirmedChecks,
        BigDecimal confirmedWithholdings,
        BigDecimal totalReceived,
        BigDecimal difference,
        String differenceType,
        String statusGroup,
        OffsetDateTime expectedUpdatedAt,
        OffsetDateTime lastDepositAt,
        BigDecimal cashDifference,
        OffsetDateTime depositsCheckedAt,
        Boolean depositsLive) {
}
