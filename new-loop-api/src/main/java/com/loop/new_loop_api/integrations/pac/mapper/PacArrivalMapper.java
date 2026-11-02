package com.loop.new_loop_api.integrations.pac.mapper;

import com.loop.new_loop_api.integrations.pac.dto.PacArrivalResponse;
import com.loop.new_loop_api.performance.domain.RouteNumber;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Component
public class PacArrivalMapper {

    // Explicit on purpose: the JVM default zone is Argentina only because main sets it, not in tests.
    public static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    public PacArrivalResponse toResponse(StockControl control) {
        var route  = control.getRoute();
        var branch = control.getBranch();
        var arrivedAt = control.getCreatedAt().truncatedTo(ChronoUnit.SECONDS).atZone(ARGENTINA).toOffsetDateTime();
        return new PacArrivalResponse(RouteNumber.of(route.getCode()), route.getCode(),
                branch.getId(), branch.getName(), arrivedAt);
    }
}
