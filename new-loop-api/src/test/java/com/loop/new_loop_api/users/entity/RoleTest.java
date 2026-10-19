package com.loop.new_loop_api.users.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleTest {

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CONTROLADOR"})
    void shouldBeBranchScopedForWarehouseOperator(Role role) {
        assertThat(role.isBranchScoped()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "SUPERVISOR", "REPARTIDOR"})
    void shouldNotBeBranchScopedForOtherRoles(Role role) {
        assertThat(role.isBranchScoped()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PICKER", "CARGADOR_DISPENSERS"})
    void shouldNotAcceptRolesMergedIntoControlador(String legacyRole) {
        assertThatThrownBy(() -> Role.valueOf(legacyRole)).isInstanceOf(IllegalArgumentException.class);
    }
}
