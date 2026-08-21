package com.loop.new_loop_api.integrations.odoo.listener;

import com.loop.new_loop_api.dispensers.event.DispenserMovementSentToAguasEvent;
import com.loop.new_loop_api.integrations.odoo.service.iService.OdooDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OdooDispatchListenerTest {

    @Mock private OdooDispatchService odooDispatchService;

    @InjectMocks private OdooDispatchListener listener;

    @Test
    void should_delegateToOdooDispatchSend_when_sentToAguasEventReceived() {
        var movementId = UUID.randomUUID();

        listener.onSentToAguas(new DispenserMovementSentToAguasEvent(movementId));

        verify(odooDispatchService).send(movementId);
    }
}
