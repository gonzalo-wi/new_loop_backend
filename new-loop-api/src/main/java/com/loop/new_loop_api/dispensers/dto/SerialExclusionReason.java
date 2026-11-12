package com.loop.new_loop_api.dispensers.dto;

import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;

/**
 * Why scanned serials were kept out of Aguas. The reason follows from the movement type and has no column of its
 * own. UNLOAD is checked against jMobile. LOAD is checked against Odoo availability and Aguas existence, and
 * UNAVAILABLE_IN_ODOO covers both: telling them apart would need the reason stored per excluded serial.
 */
public enum SerialExclusionReason {
    UNREGISTERED_IN_JMOBILE,
    UNAVAILABLE_IN_ODOO;

    public static SerialExclusionReason forType(DispenserMovementType type) {
        return type == DispenserMovementType.LOAD ? UNAVAILABLE_IN_ODOO : UNREGISTERED_IN_JMOBILE;
    }
}
