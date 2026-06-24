package com.loop.new_loop_api.stockcontrols.entity;

public enum ControlStatus {
    CONTROLLED,
    PENDING_DRIVER_APPROVAL,
    ACCEPTED_BY_DRIVER,
    REJECTED_BY_DRIVER,
    WITH_DIFFERENCES,
    SENT_TO_AGUAS,
    AGUAS_ERROR,
    CANCELLED
}
