package com.loop.new_loop_api.integrations.aguas.service.iService;

import java.util.UUID;

public interface AguasEquipmentService {

    /** Sends a dispenser movement to Aguas (salida for LOAD, vuelta for UNLOAD) and records the attempt. */
    void send(UUID movementId);

    /** Re-sends a previously failed dispenser movement identified by its integration log id. */
    void retry(UUID logId);

    /** Deletes the movement in Aguas (by its stored idMovimiento). Returns true on success. */
    boolean deleteInAguas(UUID movementId);

    /** Aguas destination locations catalog (for populating the app selectors). */
    Object getDestinationLocations();

    /** Aguas destination states catalog. */
    Object getDestinationStates();
}
