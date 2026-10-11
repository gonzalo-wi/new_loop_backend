package com.loop.new_loop_api.dispensers.service;

import com.loop.new_loop_api.dispensers.dto.SerialDeliveryStatus;
import com.loop.new_loop_api.dispensers.dto.SerialExclusionReason;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.dispensers.repository.DispenserMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispenserSerialHistoryServiceImplTest {

    @Mock private DispenserMovementRepository dispenserMovementRepository;

    private DispenserSerialHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DispenserSerialHistoryServiceImpl(dispenserMovementRepository);
    }

    private static DispenserMovement movement(DispenserMovementType type) {
        return DispenserMovement.builder()
                .id(UUID.randomUUID())
                .type(type)
                .status(DispenserMovementStatus.SENT_TO_AGUAS)
                .routeCode("Rto 12")
                .technician("Tech")
                .movementDate(LocalDate.of(2026, 9, 1))
                .registeredByUsername("chofer9")
                .serials(new ArrayList<>(List.of("SN-1")))
                .build();
    }

    @Test
    void shouldTrimTheSerialAndMapEachMovementToAnEntry() {
        var movement = movement(DispenserMovementType.UNLOAD);
        movement.getExcludedSerials().add("SN-1");
        when(dispenserMovementRepository.findBySerial(eq("SN-1"), any(Pageable.class))).thenReturn(List.of(movement));

        var history = service.getHistory("  SN-1 ");

        assertThat(history.serial()).isEqualTo("SN-1");
        assertThat(history.truncated()).isFalse();
        var entry = history.entries().getFirst();
        assertThat(entry.movementId()).isEqualTo(movement.getId());
        assertThat(entry.routeCode()).isEqualTo("Rto 12");
        assertThat(entry.registeredByUsername()).isEqualTo("chofer9");
        assertThat(entry.excluded()).isTrue();
        assertThat(entry.exclusionReason()).isEqualTo(SerialExclusionReason.UNREGISTERED_IN_JMOBILE);
        assertThat(entry.aguas()).isEqualTo(SerialDeliveryStatus.EXCLUDED);
    }

    @Test
    void shouldCutTheHistoryAndFlagIt_when_thereAreMoreMovementsThanTheLimit() {
        var movements = IntStream.rangeClosed(0, DispenserSerialHistoryServiceImpl.MAX_ENTRIES)
                .mapToObj(i -> movement(DispenserMovementType.LOAD))
                .toList();
        when(dispenserMovementRepository.findBySerial(eq("SN-1"), any(Pageable.class))).thenReturn(movements);

        var history = service.getHistory("SN-1");

        assertThat(history.entries()).hasSize(DispenserSerialHistoryServiceImpl.MAX_ENTRIES);
        assertThat(history.truncated()).isTrue();
    }

    @Test
    void shouldFlagTheEntryAsBaggedIgnoringCase_when_theSerialCameBackBagged() {
        var movement = movement(DispenserMovementType.UNLOAD);
        movement.getBaggedSerials().add("sn-1");
        when(dispenserMovementRepository.findBySerial(eq("SN-1"), any(Pageable.class))).thenReturn(List.of(movement));

        var entry = service.getHistory("SN-1").entries().getFirst();

        assertThat(entry.bagged()).isTrue();
    }

    @Test
    void shouldNotFlagTheEntryAsBagged_when_theSerialWasNotBagged() {
        var movement = movement(DispenserMovementType.UNLOAD);
        when(dispenserMovementRepository.findBySerial(eq("SN-1"), any(Pageable.class))).thenReturn(List.of(movement));

        var entry = service.getHistory("SN-1").entries().getFirst();

        assertThat(entry.bagged()).isFalse();
    }
}
