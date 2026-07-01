package com.loop.new_loop_api.integrations.aguas.listener;

import com.loop.new_loop_api.dispensers.event.DispenserMovementReadyForAguasEvent;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DispenserMovementListener {

    private final AguasEquipmentService aguasEquipmentService;

    /** After the dispenser movement transaction commits, send it to Aguas on a separate thread. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMovementReady(DispenserMovementReadyForAguasEvent event) {
        aguasEquipmentService.send(event.movementId());
    }
}
