package com.loop.new_loop_api.users.entity;

public enum Role {
    ADMIN,
    CONTROLADOR,
    REPARTIDOR,
    SUPERVISOR,
    VISUALIZADOR,
    CLARKISTA;

    /**
     * Plant roles work for a single branch, so they must have one and only see that branch's data:
     * warehouse operators switch tasks day to day, viewers follow their plant's controls and forklift
     * operators load that plant's trucks.
     */
    public boolean isBranchScoped() {
        return this == CONTROLADOR || this == VISUALIZADOR || this == CLARKISTA;
    }

    /** Roles that may only read an explicit allowlist of endpoints (see ReadOnlyRoleAccessPolicy). */
    public boolean isReadOnly() {
        return this == VISUALIZADOR || this == CLARKISTA;
    }
}
