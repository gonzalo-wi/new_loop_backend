package com.loop.new_loop_api.dispensers.mapper;

import com.loop.new_loop_api.dispensers.dto.SerialExclusionReason;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DispenserMovementMapperTest {

    private final DispenserMovementMapper mapper = new DispenserMovementMapper();

    private static DispenserMovement movement(DispenserMovementType type, List<String> excluded) {
        return DispenserMovement.builder()
                .type(type)
                .serials(new ArrayList<>(List.of("SN-1", "SN-2")))
                .excludedSerials(new ArrayList<>(excluded))
                .odooSkippedSerials(new ArrayList<>(List.of("SN-2")))
                .build();
    }

    @Test
    void shouldExplainLoadExclusionsAsUnavailableInOdoo() {
        var response = mapper.toResponse(movement(DispenserMovementType.LOAD, List.of("SN-1")));

        assertThat(response.getExclusionReason()).isEqualTo(SerialExclusionReason.UNAVAILABLE_IN_ODOO);
        assertThat(response.getOdooSkippedSerials()).containsExactly("SN-2");
    }

    @Test
    void shouldExplainUnloadExclusionsAsUnregisteredInJmobile() {
        var response = mapper.toResponse(movement(DispenserMovementType.UNLOAD, List.of("SN-1")));

        assertThat(response.getExclusionReason()).isEqualTo(SerialExclusionReason.UNREGISTERED_IN_JMOBILE);
    }

    @Test
    void shouldGiveNoExclusionReason_when_nothingWasExcluded() {
        var response = mapper.toResponse(movement(DispenserMovementType.LOAD, List.of()));

        assertThat(response.getExclusionReason()).isNull();
    }
}
