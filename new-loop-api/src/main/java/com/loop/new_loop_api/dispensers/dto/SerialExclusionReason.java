package com.loop.new_loop_api.dispensers.dto;

import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;

/**
 * Why scanned serials were kept out of Aguas. Each movement type has a single exclusion check (UNLOAD against
 * jMobile, LOAD against Odoo), so the reason follows from the type and needs no column of its own.
 */
public enum SerialExclusionReason {
    UNREGISTERED_IN_JMOBILE,
    UNAVAILABLE_IN_ODOO;

    public static SerialExclusionReason forType(DispenserMovementType type) {
        return type == DispenserMovementType.LOAD ? UNAVAILABLE_IN_ODOO : UNREGISTERED_IN_JMOBILE;
    }
}
