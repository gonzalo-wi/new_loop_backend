package com.loop.new_loop_api.integrations.jmobile.service.iService;

import java.time.LocalDate;
import java.util.Set;

public interface UnregisteredDispenserService {

    /**
     * Serials flagged as "no registrado" in jMobile for the given date, normalized for comparison.
     * Returns an empty set when the service is unreachable, so a lookup failure never blocks the movement.
     */
    Set<String> findUnregisteredSerials(LocalDate date);

    /** Normalizes a serial the same way the returned set is normalized. */
    String normalize(String serial);
}
