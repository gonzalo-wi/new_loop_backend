package com.loop.new_loop_api.integrations.aguas.listener;

import com.loop.new_loop_api.integrations.aguas.service.iService.AguasIntegrationService;
import com.loop.new_loop_api.stockcontrols.event.StockControlCorrectedEvent;
import com.loop.new_loop_api.stockcontrols.event.StockControlReadyForAguasEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AguasControlListener {

    private final AguasIntegrationService aguasIntegrationService;

    /**
     * Runs after the stock control transaction commits, on a separate thread, so the API
     * response is not blocked and an Aguas failure never rolls back the local control.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onControlReady(StockControlReadyForAguasEvent event) {
        aguasIntegrationService.send(event.controlId());
    }

    /**
     * Runs after a SUPERVISOR correction commits: re-sends the control to Aguas so the
     * corrected data overwrites the previous remito.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onControlCorrected(StockControlCorrectedEvent event) {
        aguasIntegrationService.resend(event.controlId());
    }
}
