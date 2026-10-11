package com.loop.new_loop_api.users.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CONTROLADOR", "PICKER", "CARGADOR_DISPENSERS"})
    void shouldBeBranchScopedForWarehouseOperators(Role role) {
        assertThat(role.isBranchScoped()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "SUPERVISOR", "REPARTIDOR"})
    void shouldNotBeBranchScopedForOtherRoles(Role role) {
        assertThat(role.isBranchScoped()).isFalse();
    }
}
