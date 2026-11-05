package com.loop.new_loop_api.settlements.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * What treasury (PAC) has for one route and day. {@code difference} and {@code differenceType} are null until
 * the cashier processed the route; expected amounts are null until Aguas' value is loaded. {@code cashDifference}
 * is the live one for the driver (deposited minus expected cash), null until there is a deposit.
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
        OffsetDateTime lastDepositAt,
        BigDecimal cashDifference,
        OffsetDateTime depositsCheckedAt,
        boolean depositsLive) {
}
