package com.loop.new_loop_api.settlements.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * What treasury (PAC) has for one route and day. {@code difference} and {@code differenceType} are null until
 * the cashier processed the route; expected amounts are null until Aguas' value is loaded.
 */
public record SettlementFigures(
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
        OffsetDateTime lastDepositAt) {
}
