package com.loop.new_loop_api.dispensers;

import com.loop.new_loop_api.dispensers.dto.CreateDispenserMovementRequest;
import com.loop.new_loop_api.dispensers.dto.DispenserMovementResponse;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import com.loop.new_loop_api.dispensers.exception.DuplicateDispenserMovementException;
import com.loop.new_loop_api.dispensers.repository.DispenserMovementRepository;
import com.loop.new_loop_api.dispensers.service.iService.DispenserMovementService;
import com.loop.new_loop_api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

class DispenserMovementDuplicateConcurrencyTest extends PostgresIntegrationTest {

    @Autowired private DispenserMovementService dispenserMovementService;

    @MockitoSpyBean private DispenserMovementRepository dispenserMovementRepository;

    private String routeCode;

    @BeforeEach
    void setUp() {
        routeCode = "R-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void shouldCreateOnlyOneMovementWhenTwoRequestsArriveTogether() throws Exception {
        Callable<DispenserMovementResponse> create = () -> dispenserMovementService.createMovement(request());

        var outcomes = runConcurrently(List.of(create, create));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.succeeded())
                .singleElement()
                .extracting(Outcome::error)
                .isInstanceOf(DuplicateDispenserMovementException.class);
        assertThat(activeMovements()).isEqualTo(1);
    }

    @Test
    void shouldReturnConflictWhenDuplicateSlipsPastTheExistsCheck() {
        dispenserMovementService.createMovement(request());
        // Simulates the race window: the check sees nothing, so only the unique index can stop the insert.
        doReturn(false).when(dispenserMovementRepository)
                .existsByTypeAndRouteCodeAndMovementDateAndStatusNot(any(), any(), any(), any());

        assertThatThrownBy(() -> dispenserMovementService.createMovement(request()))
                .isInstanceOf(DuplicateDispenserMovementException.class);
        assertThat(activeMovements()).isEqualTo(1);
    }

    @Test
    void shouldAllowEditingAMovementForTheSameRouteAndDate() {
        var original = dispenserMovementService.createMovement(request());

        dispenserMovementService.updateMovement(original.getId(), request());

        assertThat(activeMovements()).isEqualTo(1);
    }

    private long activeMovements() {
        return dispenserMovementRepository.findAll().stream()
                .filter(m -> routeCode.equals(m.getRouteCode()))
                .filter(m -> m.getStatus() != DispenserMovementStatus.CANCELLED)
                .count();
    }

    private CreateDispenserMovementRequest request() {
        var request = new CreateDispenserMovementRequest();
        request.setType(DispenserMovementType.LOAD);
        request.setRouteCode(routeCode);
        request.setTechnician("Tech 1");
        request.setMovementDate(LocalDate.now());
        request.setSerials(List.of("SN-1"));
        return request;
    }
}
