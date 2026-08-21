package com.loop.new_loop_api.integrations.odoo.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OdooDispatchMapperTest {

    private final OdooDispatchMapper mapper = new OdooDispatchMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private DispenserMovement movement;

    @BeforeEach
    void setUp() {
        movement = DispenserMovement.builder()
                .id(UUID.fromString("abcd1234-5678-90ab-cdef-1234567890ab"))
                .type(DispenserMovementType.LOAD)
                .routeCode("R7")
                .technician("Tech")
                .movementDate(LocalDate.of(2026, 8, 20))
                .serials(List.of("SN-1", "SN-2", "SN-3"))
                .build();
    }

    // ---------- buildExternalReference ----------

    @Test
    void should_returnSameReference_when_calledTwiceForSameMovement() {
        var first  = mapper.buildExternalReference(movement);
        var second = mapper.buildExternalReference(movement);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void should_buildReferenceWithExpectedFormat_when_buildExternalReference() {
        var reference = mapper.buildExternalReference(movement);

        // LOOP-<fecha sin guiones>-<routeCode>-<8 primeros chars del UUID sin guiones, mayus>
        assertThat(reference).isEqualTo("LOOP-20260820-R7-ABCD1234");
    }

    @Test
    void should_uppercaseUuidSuffixAndStripDashesFromDate_when_buildExternalReference() {
        var reference = mapper.buildExternalReference(movement);
        var parts = reference.split("-");

        assertThat(parts).hasSize(4);
        assertThat(parts[0]).isEqualTo("LOOP");
        assertThat(parts[1]).isEqualTo("20260820").doesNotContain("-");
        assertThat(parts[2]).isEqualTo("R7");
        assertThat(parts[3]).hasSize(8).isEqualTo(parts[3].toUpperCase());
    }

    // ---------- toCreateRequest ----------

    @Test
    void should_includeReferenceDateRouteAndEquipos_when_toCreateRequest() {
        var reference = mapper.buildExternalReference(movement);

        var request = mapper.toCreateRequest(movement, reference);
        var params  = request.getParams();

        assertThat(params.getFecha()).isEqualTo("20260820");
        assertThat(params.getIdreparto()).isEqualTo("R7");
        assertThat(params.getEquipos()).containsExactly("SN-1", "SN-2", "SN-3");
        assertThat(params.getReferenciaExterna()).isEqualTo(reference);
    }

    @Test
    void should_excludeExcludedSerials_when_toCreateRequest() {
        movement.setExcludedSerials(List.of("SN-2"));

        var request = mapper.toCreateRequest(movement, "REF");

        assertThat(request.getParams().getEquipos()).containsExactly("SN-1", "SN-3");
    }

    @Test
    void should_serializeReferenceInSnakeCase_when_toCreateRequestSerialized() throws Exception {
        var request = mapper.toCreateRequest(movement, "LOOP-20260820-R7-ABCD1234");

        var json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"referencia_externa\":\"LOOP-20260820-R7-ABCD1234\"");
        assertThat(json).doesNotContain("referenciaExterna");
    }

    // ---------- available params @JsonInclude(NON_NULL) ----------

    @Test
    void should_omitLimiteAndOffset_when_nullOnAvailableRequest() throws Exception {
        var request = mapper.toAvailableRequest(null, null);

        var json = objectMapper.writeValueAsString(request);

        assertThat(json).doesNotContain("limite");
        assertThat(json).doesNotContain("offset");
    }

    @Test
    void should_includeLimiteAndOffset_when_presentOnAvailableRequest() throws Exception {
        var request = mapper.toAvailableRequest(50, 10);

        var json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"limite\":50");
        assertThat(json).contains("\"offset\":10");
    }

    // ---------- validate request ----------

    @Test
    void should_carryEquipos_when_toValidateRequest() {
        var request = mapper.toValidateRequest(List.of("SN-1", "SN-2"));

        assertThat(request.getParams().getEquipos()).containsExactly("SN-1", "SN-2");
    }
}
