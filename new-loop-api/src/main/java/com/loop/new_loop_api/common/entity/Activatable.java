package com.loop.new_loop_api.common.entity;

/** Marks entities that support the activate/deactivate lifecycle. */
public interface Activatable {
    void setActive(Boolean active);
}
