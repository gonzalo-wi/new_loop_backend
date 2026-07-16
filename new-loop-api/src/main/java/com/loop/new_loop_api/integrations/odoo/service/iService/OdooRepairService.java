package com.loop.new_loop_api.integrations.odoo.service.iService;

import java.util.UUID;

public interface OdooRepairService {

    /** Sends an UNLOAD dispenser movement to Odoo (repair intake). Requires it to be already sent to Aguas. */
    void send(UUID movementId);

    /** Re-sends a previously failed Odoo attempt identified by its integration log id. */
    void retry(UUID logId);
}
