package com.loop.new_loop_api.common.security;

import com.loop.new_loop_api.users.entity.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOnlyRoleAccessPolicyTest {

    private static final String CONTROL_ID = "7f1c2b4e-0d6a-4c8e-9b1a-2f3e4d5c6b7a";

    @ParameterizedTest
    @CsvSource({
            "GET, /stock-controls",
            "GET, /stock-controls/pending-arrivals",
            "GET, /stock-controls/" + CONTROL_ID,
            "GET, /stock-controls/" + CONTROL_ID + "/remito",
            "POST, /stock-controls/remitos",
            "POST, /stock-controls/" + CONTROL_ID + "/remito/printed",
            "POST, /stock-controls/remitos/printed",
    })
    void shouldAllowViewerToReadStockControlsAndPrintRemitos(String method, String path) {
        assertThat(ReadOnlyRoleAccessPolicy.allows(Role.VISUALIZADOR, method, path)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "POST,  /stock-controls",
            "PATCH, /stock-controls/" + CONTROL_ID,
            "POST,  /stock-controls/" + CONTROL_ID + "/approve",
            "POST,  /stock-controls/" + CONTROL_ID + "/reject",
            "POST,  /stock-controls/" + CONTROL_ID + "/correct",
            "GET,   /stock-controls/remito",
            "GET,   /stock-controls/not-a-uuid",
            "GET,   /stock-controls/remitos",
            "GET,   /stock-controls/" + CONTROL_ID + "/remito/printed",
            "PATCH, /stock-controls/" + CONTROL_ID + "/remito",
            "GET,   /orders",
            "GET,   /users",
            "GET,   /load-forecast",
            "DELETE, /stock-controls/" + CONTROL_ID,
            "POST,  /settlements/me/receipts",
            "GET,   /settlements/me",
    })
    void shouldDenyViewerEverythingElse(String method, String path) {
        assertThat(ReadOnlyRoleAccessPolicy.allows(Role.VISUALIZADOR, method, path)).isFalse();
    }

    @Test
    void shouldAllowClarkistaToReadTheLoadForecast() {
        assertThat(ReadOnlyRoleAccessPolicy.allows(Role.CLARKISTA, "GET", "/load-forecast")).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /load-forecast",
            "GET,  /stock-controls",
            "GET,  /stock-controls/" + CONTROL_ID,
            "GET,  /orders",
            "GET,  /routes",
            "POST, /orders",
            "POST, /settlements/me/receipts",
    })
    void shouldDenyClarkistaEverythingElse(String method, String path) {
        assertThat(ReadOnlyRoleAccessPolicy.allows(Role.CLARKISTA, method, path)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "SUPERVISOR", "CONTROLADOR", "REPARTIDOR"})
    void shouldNotRestrictOperationalRoles(Role role) {
        assertThat(ReadOnlyRoleAccessPolicy.allows(role, "POST", "/stock-controls")).isTrue();
    }

    @Test
    void shouldIgnoreTrailingSlash() {
        assertThat(ReadOnlyRoleAccessPolicy.allows(Role.VISUALIZADOR, "GET", "/stock-controls/")).isTrue();
    }
}
