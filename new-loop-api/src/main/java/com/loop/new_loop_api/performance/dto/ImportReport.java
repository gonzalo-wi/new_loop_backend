package com.loop.new_loop_api.performance.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Outcome of the audit history import. {@code importedBefore} counts entries already imported by an
 * earlier run; {@code skippedByReason} explains entries that could not be read.
 */
public record ImportReport(
        LocalDateTime cutoff,
        int controls,
        int imported,
        int importedBefore,
        int skipped,
        Map<String, Integer> skippedByReason) {
}
