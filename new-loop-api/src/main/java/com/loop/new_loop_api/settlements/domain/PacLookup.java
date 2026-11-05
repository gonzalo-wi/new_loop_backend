package com.loop.new_loop_api.settlements.domain;

import com.loop.new_loop_api.settlements.dto.SettlementFigures;

/** The outcome of asking PAC for one route and day. */
public sealed interface PacLookup {

    record Found(SettlementFigures figures) implements PacLookup {}

    /** PAC answered 404: the route is not loaded in treasury yet. */
    record NotLoaded() implements PacLookup {}

    /** PAC is not configured, unreachable, slow or answered an error. */
    record Unavailable(String reason) implements PacLookup {}
}
