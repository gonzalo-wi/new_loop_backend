package com.loop.new_loop_api.integrations.aguas.mapper;

import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AguasEquipmentMapperTest {

    private final AguasEquipmentMapper mapper = new AguasEquipmentMapper();

    private DispenserMovement.DispenserMovementBuilder baseMovement() {
        return DispenserMovement.builder()
                .type(DispenserMovementType.LOAD)
                .routeCode("10")
                .technician("Gonzalo Wiñazki")
                .locationId(2)
                .stateId(2)
                .movementDate(LocalDate.of(2026, 8, 31))
                .serials(List.of("TP.P0131", "23989"));
    }

    @Test
    void should_alwaysSendEsRecargaFalse() {
        var request = mapper.toRequest(baseMovement().registeredByUsername("gwinazki").build());

        assertThat(request.getEsRecarga()).isFalse();
    }

    @Test
    void should_useRegisteredUsername_when_present() {
        var request = mapper.toRequest(baseMovement().registeredByUsername("gwinazki").build());

        assertThat(request.getUsuario()).isEqualTo("gwinazki");
    }

    @Test
    void should_fallBackToTechnician_when_usernameIsNull() {
        var request = mapper.toRequest(baseMovement().registeredByUsername(null).build());

        assertThat(request.getUsuario()).isEqualTo("Gonzalo Wiñazki");
    }

    @Test
    void should_fallBackToTechnician_when_usernameIsBlank() {
        var request = mapper.toRequest(baseMovement().registeredByUsername("   ").build());

        assertThat(request.getUsuario()).isEqualTo("Gonzalo Wiñazki");
    }
}
