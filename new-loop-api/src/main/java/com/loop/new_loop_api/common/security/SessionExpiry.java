package com.loop.new_loop_api.common.security;

import java.time.Instant;
import java.time.ZoneId;

/** Sessions end at midnight so everyone logs in again at the start of each working day. */
public final class SessionExpiry {

    // Explicit, so it doesn't depend on the JVM default zone being set at startup.
    static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    private SessionExpiry() {
    }

    public static Instant nextMidnight(Instant now) {
        return now.atZone(ARGENTINA).toLocalDate().plusDays(1).atStartOfDay(ARGENTINA).toInstant();
    }
}
