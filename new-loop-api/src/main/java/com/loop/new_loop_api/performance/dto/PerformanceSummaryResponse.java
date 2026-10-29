package com.loop.new_loop_api.performance.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Performance page (spec 001). Durations are in minutes and rates in percent, both rounded to one
 * decimal; an average, median or rate is null when there is no data for it, counts are zero.
 */
public record PerformanceSummaryResponse(
        LocalDate from,
        LocalDate to,
        List<UserPerformance> users,
        List<CoverageDay> coverage,
        List<ReasonCount> topReasons) {

    public record UserPerformance(UUID userId, String username, ControlMetrics controls, DispenserMetrics dispensers) {}

    public record ControlMetrics(
            int exitsSubmitted,
            int entriesSubmitted,
            int edits,
            int resubmissions,
            int rejectionRounds,
            Double firstPassAcceptancePercent,
            Double avgDriverResponseMinutes,
            int corrections,
            int faultyControls,
            // A control with several causes counts in each, so these may add up to more than faultyControls.
            int rejectedControls,
            int editedControls,
            int correctedControls,
            Double errorPercent,
            int controlsWithIntegrationErrors) {}

    public record DispenserMetrics(
            int loads,
            int unloads,
            int serials,
            int excludedUnregistered,
            int excludedUnavailableInOdoo,
            Double avgLoadMinutes,
            Double medianLoadMinutes,
            int slowLoads,
            Double avgGapMinutes,
            Double medianGapMinutes,
            int pauses,
            int cancellations,
            int movementsWithIntegrationErrors) {}

    public record CoverageDay(UUID branchId, String branchName, LocalDate controlDate,
                              int expectedEntries, int missingEntries, List<String> missingRoutes) {}

    /** kind is REJECTION or CORRECTION; the user is who wrote it (summary) or the controller (user trend). */
    public record ReasonCount(String kind, String reason, UUID userId, String username, int count) {}
}
