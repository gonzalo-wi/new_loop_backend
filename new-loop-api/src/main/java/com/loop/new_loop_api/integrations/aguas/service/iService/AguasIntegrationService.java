package com.loop.new_loop_api.integrations.aguas.service.iService;

import java.util.UUID;

public interface AguasIntegrationService {

    /** Sends a stock control to Aguas (IN for ENTRY, OUT for EXIT) and records the attempt. */
    void send(UUID controlId);

    /** Re-sends a previously failed attempt identified by its integration log id. */
    void retry(UUID logId);
}
