package com.loop.new_loop_api.integrations.pac.mapper;

import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.stockcontrols.entity.ControlType;
import com.loop.new_loop_api.stockcontrols.entity.StockControl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PacArrivalMapperTest {

    private final PacArrivalMapper mapper = new PacArrivalMapper();

    private static StockControl control(String routeCode, LocalDateTime createdAt, Branch branch) {
        return StockControl.builder()
                .type(ControlType.ENTRY)
                .route(Route.builder().code(routeCode).build())
                .branch(branch)
                .createdAt(createdAt)
                .build();
    }

    @Test
    void shouldMapAnEntryControlToAnArrival_inArgentinaTimeWithoutFractionsOfASecond() {
        var branchId = UUID.randomUUID();
        var branch = Branch.builder().id(branchId).name("Mar del Plata").build();

        var arrival = mapper.toResponse(control("Rto 179", LocalDateTime.parse("2026-11-02T15:20:00.123"), branch));

        assertThat(arrival.routeNumber()).isEqualTo(179L);
        assertThat(arrival.routeCode()).isEqualTo("Rto 179");
        assertThat(arrival.branchId()).isEqualTo(branchId);
        assertThat(arrival.branchName()).isEqualTo("Mar del Plata");
        assertThat(arrival.arrivedAt()).isEqualTo(OffsetDateTime.of(2026, 11, 2, 15, 20, 0, 0, ZoneOffset.ofHours(-3)));
        assertThat(arrival.arrivedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(-3));
    }

    @Test
    void shouldLeaveTheRouteNumberNull_when_theCodeHasNoDigits() {
        var branch = Branch.builder().id(UUID.randomUUID()).name("Centro").build();

        var arrival = mapper.toResponse(control("Sin número", LocalDateTime.parse("2026-11-02T15:20:00"), branch));

        assertThat(arrival.routeNumber()).isNull();
        assertThat(arrival.routeCode()).isEqualTo("Sin número");
    }
}
