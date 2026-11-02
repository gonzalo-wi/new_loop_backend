package com.loop.new_loop_api.integrations.pac.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A route that already came back (non-cancelled ENTRY control). {@code routeNumber} is null when the code has no digits. */
public record PacArrivalResponse(
        Long routeNumber,
        String routeCode,
        UUID branchId,
        String branchName,
        OffsetDateTime arrivedAt
) {
}
