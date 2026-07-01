package com.loop.new_loop_api.dispensers.event;

import java.util.UUID;

/** Published after a dispenser movement is saved locally and ready to be sent to Aguas. */
public record DispenserMovementReadyForAguasEvent(UUID movementId) {
}
