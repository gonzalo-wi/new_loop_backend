package com.loop.new_loop_api.users.entity;

public enum Role {
    ADMIN,
    CONTROLADOR,
    REPARTIDOR,
    PICKER,
    CARGADOR_DISPENSERS,
    SUPERVISOR;

    /**
     * Warehouse operators work for a single branch and may switch tasks (control, picking, loading)
     * day to day, so all of them must have a branch and only see that branch's data.
     */
    public boolean isBranchScoped() {
        return this == CONTROLADOR || this == PICKER || this == CARGADOR_DISPENSERS;
    }
}
