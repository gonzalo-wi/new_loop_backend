package com.loop.new_loop_api.dispensers.dto;

import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Every load/unload a dispenser serial went through, newest first, and whether it reached Aguas and Odoo. */
public record SerialHistoryResponse(String serial, List<Entry> entries, boolean truncated) {

    public record Entry(
            UUID                    movementId,
            DispenserMovementType   type,
            DispenserMovementStatus movementStatus,
            String                  routeCode,
            String                  technician,
            LocalDate               movementDate,
            String                  registeredByUsername,
            LocalDateTime           createdAt,
            boolean                 bagged,
            boolean                 excluded,
            SerialExclusionReason   exclusionReason,
            SerialDeliveryStatus    aguas,
            SerialDeliveryStatus    odoo,
            String                  odooPickingName) {
    }
}
