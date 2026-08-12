package com.loop.new_loop_api.stockcontrols.event;

import java.util.UUID;

/**
 * Published once a SUPERVISOR correction to an already-sent ENTRY control is saved locally,
 * so the corrected data can be re-sent to Aguas, overwriting the previous remito.
 */
public record StockControlCorrectedEvent(UUID controlId) {
}
