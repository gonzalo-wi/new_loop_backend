package com.loop.new_loop_api.performance.mapper;

import com.loop.new_loop_api.performance.dto.PerformanceSummaryResponse.ControlMetrics;
import com.loop.new_loop_api.performance.dto.PerformanceSummaryResponse.CoverageDay;
import com.loop.new_loop_api.performance.dto.PerformanceSummaryResponse.DispenserMetrics;
import com.loop.new_loop_api.performance.dto.PerformanceSummaryResponse.ReasonCount;
import com.loop.new_loop_api.performance.entity.OperationEventType;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.ControlMetricsRow;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.CoverageRow;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.DispenserMetricsRow;
import com.loop.new_loop_api.performance.repository.PerformanceMetricsRepository.ReasonRow;
import org.springframework.stereotype.Component;

/** Turns query rows into the API shape: seconds to minutes, ratios to percent, one decimal. */
@Component
public class PerformanceMetricsMapper {

    // Same label as the dispenser statistics for activity whose user is unknown.
    public static final String NO_USER_LABEL = "Sin usuario";

    private static final double SECONDS_PER_MINUTE = 60.0;
    private static final double PERCENT            = 100.0;
    private static final double ONE_DECIMAL        = 10.0;

    public static final ControlMetrics NO_CONTROLS =
            new ControlMetrics(0, 0, 0, 0, 0, null, null, 0, 0, 0, 0, 0, null, 0);

    public static final DispenserMetrics NO_DISPENSERS =
            new DispenserMetrics(0, 0, 0, 0, 0, null, null, 0, null, null, 0, 0, 0);

    public ControlMetrics toControlMetrics(ControlMetricsRow row) {
        if (row == null) return NO_CONTROLS;
        int submitted = row.exitsSubmitted() + row.entriesSubmitted();
        return new ControlMetrics(
                row.exitsSubmitted(), row.entriesSubmitted(), row.edits(), row.resubmissions(),
                row.rejectionRounds(), percent(row.firstPassApprovals(), row.approvals()),
                minutes(row.avgResponseSeconds()), row.corrections(), row.faultyControls(),
                row.rejectedControls(), row.editedControls(), row.correctedControls(),
                percent(row.faultyControls(), submitted), row.controlsWithIntegrationErrors());
    }

    public DispenserMetrics toDispenserMetrics(DispenserMetricsRow row) {
        if (row == null) return NO_DISPENSERS;
        return new DispenserMetrics(
                row.loads(), row.unloads(), row.serials(), row.excludedUnregistered(),
                row.excludedUnavailableInOdoo(), minutes(row.avgLoadSeconds()), minutes(row.medianLoadSeconds()),
                row.slowLoads(), minutes(row.avgGapSeconds()), minutes(row.medianGapSeconds()), row.pauses(),
                row.cancellations(), row.movementsWithIntegrationErrors());
    }

    public CoverageDay toCoverageDay(CoverageRow row) {
        return new CoverageDay(row.branchId(), row.branchName(), row.controlDate(),
                row.expectedEntries(), row.missingEntries(), row.missingRoutes());
    }

    public ReasonCount toReasonCount(ReasonRow row) {
        var kind = row.eventType() == OperationEventType.CONTROL_REJECTED ? "REJECTION" : "CORRECTION";
        return new ReasonCount(kind, row.reason(), row.userId(), usernameOrLabel(row.userId(), row.username()), row.count());
    }

    public String usernameOrLabel(Object userId, String username) {
        return userId == null ? NO_USER_LABEL : username;
    }

    private static Double percent(int part, int total) {
        return total == 0 ? null : round((double) part / total * PERCENT);
    }

    private static Double minutes(Double seconds) {
        return seconds == null ? null : round(seconds / SECONDS_PER_MINUTE);
    }

    private static double round(double value) {
        return Math.round(value * ONE_DECIMAL) / ONE_DECIMAL;
    }
}
