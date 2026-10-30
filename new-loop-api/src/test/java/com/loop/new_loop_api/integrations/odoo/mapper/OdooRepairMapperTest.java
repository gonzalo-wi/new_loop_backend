package com.loop.new_loop_api.integrations.odoo.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.integrations.odoo.dto.OdooRepairRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OdooRepairMapperTest {

    private final OdooRepairMapper mapper = new OdooRepairMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static DispenserMovement unload(List<String> serials, List<String> bagged, List<String> excluded) {
        return DispenserMovement.builder()
                .id(UUID.randomUUID())
                .type(DispenserMovementType.UNLOAD)
                .routeCode("10")
                .technician("lperalta")
                .registeredByUsername("lperalta")
                .movementDate(LocalDate.of(2026, 10, 28))
                .serials(new ArrayList<>(serials))
                .baggedSerials(new ArrayList<>(bagged))
                .excludedSerials(new ArrayList<>(excluded))
                .build();
    }

    private JsonNode params(OdooRepairRequest request) {
        return objectMapper.valueToTree(request).get("params");
    }

    private static List<String> texts(JsonNode array) {
        var values = new ArrayList<String>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    @Test
    void shouldSendBaggedSerialsOnlyAsEmbolsados() {
        var movement = unload(List.of("SN-1", "SN-2", "SN-3"), List.of("SN-2"), List.of());

        var params = params(mapper.toRequest(movement));

        assertThat(texts(params.get("equipos"))).containsExactly("SN-1", "SN-3");
        assertThat(texts(params.get("embolsados"))).containsExactly("SN-2");
        assertThat(params.get("idreparto").asText()).isEqualTo("10");
        assertThat(params.get("fecha").asText()).isEqualTo("2026-10-28");
    }

    @Test
    void shouldSendEmbolsadosEmpty_when_noSerialIsBagged() {
        var movement = unload(List.of("SN-1", "SN-2"), List.of(), List.of());

        var params = params(mapper.toRequest(movement));

        assertThat(texts(params.get("equipos"))).containsExactly("SN-1", "SN-2");
        assertThat(params.get("embolsados").isArray()).isTrue();
        assertThat(params.get("embolsados")).isEmpty();
    }

    @Test
    void shouldSendEquiposEmpty_when_everySerialIsBagged() {
        var movement = unload(List.of("SN-1", "SN-2"), List.of("SN-1", "SN-2"), List.of());

        var params = params(mapper.toRequest(movement));

        assertThat(params.get("equipos")).isEmpty();
        assertThat(texts(params.get("embolsados"))).containsExactly("SN-1", "SN-2");
    }

    @Test
    void shouldLeaveAnExcludedBaggedSerialOutOfBothLists() {
        var movement = unload(List.of("SN-1", "SN-2", "SN-3"), List.of("SN-2", "SN-3"), List.of("SN-3"));

        var params = params(mapper.toRequest(movement));

        assertThat(texts(params.get("equipos"))).containsExactly("SN-1");
        assertThat(texts(params.get("embolsados"))).containsExactly("SN-2");
    }

    @Test
    void shouldMatchBaggedSerialsIgnoringCaseAndSendTheScannedSpelling() {
        var movement = unload(List.of("SN-a", "SN-b"), List.of("sn-A"), List.of());

        var params = params(mapper.toRequest(movement));

        assertThat(texts(params.get("equipos"))).containsExactly("SN-b");
        assertThat(texts(params.get("embolsados"))).containsExactly("SN-a");
    }

    @Test
    void shouldNotSendEmbolsadosToTheNoNormalizadosIntake() {
        var movement = unload(List.of("SN-1", "SN-2"), List.of("SN-2"), List.of("SN-2"));

        var params = params(mapper.toNoNormalizadoRequest(movement));

        assertThat(texts(params.get("equipos"))).containsExactly("SN-2");
        assertThat(params.has("embolsados")).isFalse();
    }
}
