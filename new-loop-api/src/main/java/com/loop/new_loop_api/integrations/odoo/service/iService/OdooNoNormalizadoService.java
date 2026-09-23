package com.loop.new_loop_api.integrations.odoo.service.iService;

import java.util.UUID;

public interface OdooNoNormalizadoService {

    /**
     * Sends the excluded (unregistered in jMobile) serials of an UNLOAD movement to Odoo's
     * "equipos no normalizados" location, so they don't fall into the workshop nor create a repair order.
     */
    void send(UUID movementId);

    /** Re-sends a previously failed Odoo attempt identified by its integration log id. */
    void retry(UUID logId);
}
