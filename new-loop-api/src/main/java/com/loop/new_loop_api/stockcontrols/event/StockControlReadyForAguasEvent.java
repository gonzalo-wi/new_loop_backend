package com.loop.new_loop_api.stockcontrols.event;

import java.util.UUID;

/**
 * Published once a stock control is saved locally and ready to be sent to Aguas:
 * - EXIT controls right after creation
 * - ENTRY controls right after the driver approves them
 */
public record StockControlReadyForAguasEvent(UUID controlId) {
}
