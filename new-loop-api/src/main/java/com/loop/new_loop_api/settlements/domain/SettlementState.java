package com.loop.new_loop_api.settlements.domain;

/** What the driver's screen shows for a route (spec 007). */
public enum SettlementState {
    WAITING_ENTRY,
    NO_ROUTE_NUMBER,
    NOT_LOADED,
    UNAVAILABLE,
    AVAILABLE;

    /**
     * PAC is only asked once the route came back: the amount Aguas expects changes while the route is open.
     * {@code lookup} is null exactly when PAC was not asked.
     */
    public static SettlementState resolve(boolean hasEntry, Long routeNumber, PacLookup lookup) {
        if (!hasEntry) return WAITING_ENTRY;
        if (routeNumber == null) return NO_ROUTE_NUMBER;
        return switch (lookup) {
            case PacLookup.Found found -> AVAILABLE;
            case PacLookup.NotLoaded notLoaded -> NOT_LOADED;
            case PacLookup.Unavailable unavailable -> UNAVAILABLE;
            case null -> throw new IllegalArgumentException("PAC must be asked for a route that came back and has a number");
        };
    }
}
