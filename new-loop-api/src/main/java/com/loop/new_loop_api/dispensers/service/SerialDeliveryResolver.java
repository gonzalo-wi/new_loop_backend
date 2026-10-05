package com.loop.new_loop_api.dispensers.service;

import com.loop.new_loop_api.dispensers.dto.SerialDeliveryStatus;
import com.loop.new_loop_api.dispensers.dto.SerialExclusionReason;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;

import java.util.List;

/** Works out where a single serial of a movement ended up in Aguas and Odoo. */
final class SerialDeliveryResolver {

    // Odoo tracking columns hold these values (see the Odoo integration services).
    private static final String ODOO_SENT  = "SENT";
    private static final String ODOO_ERROR = "ERROR";

    record SerialDelivery(boolean excluded, SerialExclusionReason exclusionReason,
                          SerialDeliveryStatus aguas, SerialDeliveryStatus odoo, String odooPickingName) {
    }

    private SerialDeliveryResolver() {
    }

    static SerialDelivery resolve(DispenserMovement movement, String serial) {
        var excluded = containsIgnoreCase(movement.getExcludedSerials(), serial);
        var reason   = excluded ? SerialExclusionReason.forType(movement.getType()) : null;

        if (movement.getStatus() == DispenserMovementStatus.CANCELLED) {
            return new SerialDelivery(excluded, reason, SerialDeliveryStatus.CANCELLED, SerialDeliveryStatus.CANCELLED, null);
        }
        if (excluded) {
            return resolveExcluded(movement, reason);
        }
        if (containsIgnoreCase(movement.getOdooSkippedSerials(), serial)) {
            return new SerialDelivery(false, null, aguasStatus(movement), SerialDeliveryStatus.SKIPPED, null);
        }
        var odoo = odooStatus(movement.getOdooStatus());
        return new SerialDelivery(false, null, aguasStatus(movement), odoo,
                odoo == SerialDeliveryStatus.SENT ? movement.getOdooPickingName() : null);
    }

    /** Excluded UNLOAD serials skip Aguas but still go to the Odoo "no normalizados" intake; LOAD ones go nowhere. */
    private static SerialDelivery resolveExcluded(DispenserMovement movement, SerialExclusionReason reason) {
        if (movement.getType() == DispenserMovementType.LOAD) {
            return new SerialDelivery(true, reason, SerialDeliveryStatus.EXCLUDED, SerialDeliveryStatus.EXCLUDED, null);
        }
        var odoo = odooStatus(movement.getOdooNoNormalizadoStatus());
        return new SerialDelivery(true, reason, SerialDeliveryStatus.EXCLUDED, odoo,
                odoo == SerialDeliveryStatus.SENT ? movement.getOdooNoNormalizadoPickingName() : null);
    }

    private static SerialDeliveryStatus aguasStatus(DispenserMovement movement) {
        return switch (movement.getStatus()) {
            case SENT_TO_AGUAS        -> SerialDeliveryStatus.SENT;
            case AGUAS_ERROR          -> SerialDeliveryStatus.ERROR;
            case SKIPPED_UNREGISTERED -> SerialDeliveryStatus.EXCLUDED;
            case CANCELLED            -> SerialDeliveryStatus.CANCELLED;
            case REGISTERED           -> SerialDeliveryStatus.PENDING;
        };
    }

    // No Odoo status yet means it was not attempted: Odoo only goes after Aguas confirms.
    private static SerialDeliveryStatus odooStatus(String status) {
        if (ODOO_SENT.equals(status))  return SerialDeliveryStatus.SENT;
        if (ODOO_ERROR.equals(status)) return SerialDeliveryStatus.ERROR;
        return SerialDeliveryStatus.PENDING;
    }

    private static boolean containsIgnoreCase(List<String> serials, String serial) {
        return serials != null && serials.stream().anyMatch(serial::equalsIgnoreCase);
    }
}
