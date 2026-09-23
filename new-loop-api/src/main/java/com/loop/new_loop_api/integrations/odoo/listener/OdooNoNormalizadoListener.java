package com.loop.new_loop_api.integrations.odoo.listener;

import com.loop.new_loop_api.dispensers.event.DispenserMovementUnregisteredSerialsExcludedEvent;
import com.loop.new_loop_api.integrations.odoo.service.iService.OdooNoNormalizadoService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OdooNoNormalizadoListener {

    private final OdooNoNormalizadoService odooNoNormalizadoService;

    /** Once an UNLOAD with unregistered serials is committed, send those serials to Odoo's no-normalizados location. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnregisteredSerialsExcluded(DispenserMovementUnregisteredSerialsExcludedEvent event) {
        odooNoNormalizadoService.send(event.movementId());
    }
}
