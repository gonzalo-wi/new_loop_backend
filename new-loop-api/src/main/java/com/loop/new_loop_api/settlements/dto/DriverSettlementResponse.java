package com.loop.new_loop_api.settlements.dto;

import com.loop.new_loop_api.settlements.domain.SettlementState;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The logged-in driver's routes for today, each with its settlement state (spec 007). */
public record DriverSettlementResponse(LocalDate date, List<RouteSettlement> routes) {

    /** {@code figures} is only present when {@code state} is AVAILABLE. */
    public record RouteSettlement(UUID routeId, String routeCode, SettlementState state, SettlementFigures figures) {}
}
