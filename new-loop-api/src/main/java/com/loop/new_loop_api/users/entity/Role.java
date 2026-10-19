package com.loop.new_loop_api.users.entity;

public enum Role {
    ADMIN,
    CONTROLADOR,
    REPARTIDOR,
    SUPERVISOR;

    /**
     * Warehouse operators work for a single branch and switch tasks (control, picking, loading)
     * day to day, so they must have a branch and only see that branch's data.
     */
    public boolean isBranchScoped() {
        return this == CONTROLADOR;
    }
}
