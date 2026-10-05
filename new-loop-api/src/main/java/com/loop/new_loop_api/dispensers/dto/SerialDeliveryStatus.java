package com.loop.new_loop_api.dispensers.dto;

/** Where one serial of a movement stands with respect to one destination (Aguas or Odoo). */
public enum SerialDeliveryStatus {
    SENT,
    PENDING,
    ERROR,
    /** Kept out on purpose when the movement was registered (see {@link SerialExclusionReason}). */
    EXCLUDED,
    /** Odoo accepted the rest of the dispatch but left this serial out: not available in expedición. */
    SKIPPED,
    CANCELLED
}
