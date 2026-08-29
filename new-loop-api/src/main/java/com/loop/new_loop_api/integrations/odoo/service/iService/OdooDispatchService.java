package com.loop.new_loop_api.integrations.odoo.service.iService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface OdooDispatchService {

    /** Sends a LOAD dispenser movement to Odoo (dispatch/salida to reparto). Requires it to be already sent to Aguas. */
    void send(UUID movementId);

    /** Re-sends a previously failed Odoo attempt identified by its integration log id. */
    void retry(UUID logId);

    /** Lists equipment available in Odoo to load onto the truck (read-only, raw Odoo result). */
    Object getAvailableEquipment(Integer limite, Integer offset);

    /** Validates serials against Odoo stock/location before confirming a load (read-only, raw Odoo result). */
    Object validateEquipment(List<String> equipos);

    /**
     * Subset of {@code equipos} that Odoo reports as NOT available for dispatch (unknown serial, wrong
     * location or no stock). Returns an empty set when validation is unreachable, so a lookup failure
     * never blocks the movement.
     */
    Set<String> findUnavailableEquipos(List<String> equipos);
}
