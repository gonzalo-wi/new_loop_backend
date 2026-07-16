package com.loop.new_loop_api.integrations.odoo.listener;

import com.loop.new_loop_api.dispensers.event.DispenserMovementSentToAguasEvent;
import com.loop.new_loop_api.integrations.odoo.service.iService.OdooRepairService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OdooRepairListener {

    private final OdooRepairService odooRepairService;

    /** After an UNLOAD movement is confirmed in Aguas, forward it to Odoo. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSentToAguas(DispenserMovementSentToAguasEvent event) {
        odooRepairService.send(event.movementId());
    }
}
