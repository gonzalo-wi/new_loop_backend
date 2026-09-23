package com.loop.new_loop_api.dispensers.event;

import java.util.UUID;

/** An UNLOAD movement kept serials out of Aguas/repair because jMobile flags them as not normalized. */
public record DispenserMovementUnregisteredSerialsExcludedEvent(UUID movementId) {
}
