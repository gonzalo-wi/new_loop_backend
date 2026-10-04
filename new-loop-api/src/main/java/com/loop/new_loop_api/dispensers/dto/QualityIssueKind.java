package com.loop.new_loop_api.dispensers.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum QualityIssueKind {
    SKIPPED("skipped"),
    AGUAS_ERROR("aguas_error"),
    ODOO_ERROR("odoo_error"),
    EXCLUDED("excluded");

    private final String value;

    QualityIssueKind(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
