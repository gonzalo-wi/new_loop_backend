package com.loop.new_loop_api.dispensers.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityIssueKindTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serialize_usesLowercaseValue_forEveryKind() {
        assertThat(mapper.writeValueAsString(QualityIssueKind.SKIPPED)).isEqualTo("\"skipped\"");
        assertThat(mapper.writeValueAsString(QualityIssueKind.AGUAS_ERROR)).isEqualTo("\"aguas_error\"");
        assertThat(mapper.writeValueAsString(QualityIssueKind.ODOO_ERROR)).isEqualTo("\"odoo_error\"");
        assertThat(mapper.writeValueAsString(QualityIssueKind.EXCLUDED)).isEqualTo("\"excluded\"");
    }

    @Test
    void serialize_usesLowercaseValues_insideList() {
        String json = mapper.writeValueAsString(List.of(QualityIssueKind.SKIPPED, QualityIssueKind.EXCLUDED));

        assertThat(json).isEqualTo("[\"skipped\",\"excluded\"]");
    }
}
