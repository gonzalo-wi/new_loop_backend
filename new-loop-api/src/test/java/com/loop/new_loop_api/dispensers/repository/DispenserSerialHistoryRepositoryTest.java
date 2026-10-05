package com.loop.new_loop_api.dispensers.repository;

import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DispenserSerialHistoryRepositoryTest extends PostgresIntegrationTest {

    @Autowired private DispenserMovementRepository repository;

    // Shared test DB: serials and routes are unique per test so assertions only see this test's rows.
    private final String serial    = "SN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    private final String routeCode = "R-" + UUID.randomUUID().toString().substring(0, 8);

    private DispenserMovement save(DispenserMovementType type, LocalDate date, List<String> serials) {
        return repository.saveAndFlush(DispenserMovement.builder()
                .type(type)
                .routeCode(routeCode)
                .technician("Tech")
                .locationId(2)
                .stateId(2)
                .movementDate(date)
                .status(DispenserMovementStatus.SENT_TO_AGUAS)
                .serials(new ArrayList<>(serials))
                .build());
    }

    @Test
    void shouldFindEveryMovementOfTheSerialNewestFirstIgnoringCase() {
        var load   = save(DispenserMovementType.LOAD,   LocalDate.of(2026, 9, 1), List.of(serial, "OTHER-1"));
        var unload = save(DispenserMovementType.UNLOAD, LocalDate.of(2026, 9, 20), List.of(serial));
        save(DispenserMovementType.LOAD, LocalDate.of(2026, 9, 25), List.of("OTHER-2"));

        var found = repository.findBySerial(serial.toLowerCase(), PageRequest.of(0, 10));

        assertThat(found).extracting(DispenserMovement::getId).containsExactly(unload.getId(), load.getId());
    }

    @Test
    void shouldReturnAMovementOnce_when_theSerialWasScannedTwiceInIt() {
        var load = save(DispenserMovementType.LOAD, LocalDate.of(2026, 9, 1), List.of(serial, serial.toLowerCase()));

        var found = repository.findBySerial(serial, PageRequest.of(0, 10));

        assertThat(found).extracting(DispenserMovement::getId).containsExactly(load.getId());
    }
}
