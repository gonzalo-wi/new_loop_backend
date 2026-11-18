package com.loop.new_loop_api.users.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleTest {

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CONTROLADOR", "VISUALIZADOR", "CLARKISTA"})
    void shouldBeBranchScopedForPlantRoles(Role role) {
        assertThat(role.isBranchScoped()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "SUPERVISOR", "REPARTIDOR"})
    void shouldNotBeBranchScopedForOtherRoles(Role role) {
        assertThat(role.isBranchScoped()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"VISUALIZADOR", "CLARKISTA"})
    void shouldBeReadOnlyForViewerAndClarkista(Role role) {
        assertThat(role.isReadOnly()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "SUPERVISOR", "REPARTIDOR", "CONTROLADOR"})
    void shouldNotBeReadOnlyForOperationalRoles(Role role) {
        assertThat(role.isReadOnly()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PICKER", "CARGADOR_DISPENSERS"})
    void shouldNotAcceptRolesMergedIntoControlador(String legacyRole) {
        assertThatThrownBy(() -> Role.valueOf(legacyRole)).isInstanceOf(IllegalArgumentException.class);
    }
}
