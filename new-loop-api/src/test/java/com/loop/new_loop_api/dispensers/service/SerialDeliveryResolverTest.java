package com.loop.new_loop_api.dispensers.service;

import com.loop.new_loop_api.dispensers.dto.SerialDeliveryStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SerialDeliveryResolverTest {

    private static DispenserMovement movement(DispenserMovementType type, DispenserMovementStatus status) {
        return DispenserMovement.builder()
                .type(type)
                .status(status)
                .serials(new ArrayList<>(List.of("SN-1", "SN-2")))
                .build();
    }

    @Test
    void shouldReportSentToBoth_when_loadReachedAguasAndOdoo() {
        var movement = movement(DispenserMovementType.LOAD, DispenserMovementStatus.SENT_TO_AGUAS);
        movement.setOdooStatus("SENT");
        movement.setOdooPickingName("WH/OUT/0001");

        var delivery = SerialDeliveryResolver.resolve(movement, "SN-1");

        assertThat(delivery.excluded()).isFalse();
        assertThat(delivery.aguas()).isEqualTo(SerialDeliveryStatus.SENT);
        assertThat(delivery.odoo()).isEqualTo(SerialDeliveryStatus.SENT);
        assertThat(delivery.odooPickingName()).isEqualTo("WH/OUT/0001");
    }

    @Test
    void shouldReportSkippedInOdoo_when_odooLeftTheSerialOutOfAPartialDispatch() {
        var movement = movement(DispenserMovementType.LOAD, DispenserMovementStatus.SENT_TO_AGUAS);
        movement.setOdooStatus("SENT");
        movement.setOdooPickingName("WH/OUT/0001");
        movement.getOdooSkippedSerials().add("SN-1");

        var delivery = SerialDeliveryResolver.resolve(movement, "sn-1");

        assertThat(delivery.aguas()).isEqualTo(SerialDeliveryStatus.SENT);
        assertThat(delivery.odoo()).isEqualTo(SerialDeliveryStatus.SKIPPED);
        assertThat(delivery.odooPickingName()).isNull();
    }

    @Test
    void shouldWaitForAguasBeforeOdoo_when_movementIsStillRegistered() {
        var delivery = SerialDeliveryResolver.resolve(
                movement(DispenserMovementType.LOAD, DispenserMovementStatus.REGISTERED), "SN-1");

        assertThat(delivery.aguas()).isEqualTo(SerialDeliveryStatus.PENDING);
        assertThat(delivery.odoo()).isEqualTo(SerialDeliveryStatus.PENDING);
    }

    @Test
    void shouldReportAguasError() {
        var delivery = SerialDeliveryResolver.resolve(
                movement(DispenserMovementType.UNLOAD, DispenserMovementStatus.AGUAS_ERROR), "SN-1");

        assertThat(delivery.aguas()).isEqualTo(SerialDeliveryStatus.ERROR);
        assertThat(delivery.odoo()).isEqualTo(SerialDeliveryStatus.PENDING);
    }

    @Test
    void shouldExcludeFromBoth_when_loadSerialWasUnavailableInOdoo() {
        var movement = movement(DispenserMovementType.LOAD, DispenserMovementStatus.SENT_TO_AGUAS);
        movement.getExcludedSerials().add("SN-2");

        var delivery = SerialDeliveryResolver.resolve(movement, "SN-2");

        assertThat(delivery.excluded()).isTrue();
        assertThat(delivery.aguas()).isEqualTo(SerialDeliveryStatus.EXCLUDED);
        assertThat(delivery.odoo()).isEqualTo(SerialDeliveryStatus.EXCLUDED);
    }

    @Test
    void shouldFollowTheNoNormalizadoIntake_when_unloadSerialWasUnregisteredInJmobile() {
        var movement = movement(DispenserMovementType.UNLOAD, DispenserMovementStatus.SKIPPED_UNREGISTERED);
        movement.getExcludedSerials().addAll(List.of("SN-1", "SN-2"));
        movement.setOdooNoNormalizadoStatus("SENT");
        movement.setOdooNoNormalizadoPickingName("WH/NN/0003");

        var delivery = SerialDeliveryResolver.resolve(movement, "SN-1");

        assertThat(delivery.aguas()).isEqualTo(SerialDeliveryStatus.EXCLUDED);
        assertThat(delivery.odoo()).isEqualTo(SerialDeliveryStatus.SENT);
        assertThat(delivery.odooPickingName()).isEqualTo("WH/NN/0003");
    }

    @Test
    void shouldReportCancelled_when_movementWasCancelled() {
        var movement = movement(DispenserMovementType.LOAD, DispenserMovementStatus.CANCELLED);
        movement.setOdooStatus("SENT");

        var delivery = SerialDeliveryResolver.resolve(movement, "SN-1");

        assertThat(delivery.aguas()).isEqualTo(SerialDeliveryStatus.CANCELLED);
        assertThat(delivery.odoo()).isEqualTo(SerialDeliveryStatus.CANCELLED);
    }
}
