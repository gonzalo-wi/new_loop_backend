package com.loop.new_loop_api.settlements.domain;

import com.loop.new_loop_api.settlements.dto.SettlementFigures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementStateTest {

    private static final SettlementFigures FIGURES =
            new SettlementFigures(null, null, null, null, null, null, null, null, null, null, "PENDING", null, null, null, null, false);

    @Test
    void shouldWaitForTheEntryControlBeforeAnythingElse() {
        assertThat(SettlementState.resolve(false, null, null)).isEqualTo(SettlementState.WAITING_ENTRY);
        assertThat(SettlementState.resolve(false, 179L, null)).isEqualTo(SettlementState.WAITING_ENTRY);
    }

    @Test
    void shouldReportARouteCodeWithoutDigits() {
        assertThat(SettlementState.resolve(true, null, null)).isEqualTo(SettlementState.NO_ROUTE_NUMBER);
    }

    @Test
    void shouldMapEachPacOutcome() {
        assertThat(SettlementState.resolve(true, 179L, new PacLookup.Found(FIGURES))).isEqualTo(SettlementState.AVAILABLE);
        assertThat(SettlementState.resolve(true, 179L, new PacLookup.NotLoaded())).isEqualTo(SettlementState.NOT_LOADED);
        assertThat(SettlementState.resolve(true, 179L, new PacLookup.Unavailable("down"))).isEqualTo(SettlementState.UNAVAILABLE);
    }

    @Test
    void shouldRefuseARouteThatCameBackButWasNeverLookedUp() {
        assertThatThrownBy(() -> SettlementState.resolve(true, 179L, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
