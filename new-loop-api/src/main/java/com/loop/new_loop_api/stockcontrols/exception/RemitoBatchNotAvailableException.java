package com.loop.new_loop_api.stockcontrols.exception;

import com.loop.new_loop_api.common.exception.ConflictException;

import java.util.List;

/** Lists every remito of the batch that can't be printed, so the operator fixes them all at once. */
public class RemitoBatchNotAvailableException extends ConflictException {
    public RemitoBatchNotAvailableException(List<String> failures) {
        super("Some remitos are not available: " + String.join("; ", failures));
    }
}
