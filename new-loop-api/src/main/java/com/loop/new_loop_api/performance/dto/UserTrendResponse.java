package com.loop.new_loop_api.performance.dto;

import com.loop.new_loop_api.performance.dto.PerformanceSummaryResponse.ControlMetrics;
import com.loop.new_loop_api.performance.dto.PerformanceSummaryResponse.DispenserMetrics;
import com.loop.new_loop_api.performance.dto.PerformanceSummaryResponse.ReasonCount;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** One user's metrics per day: every day of the range is present, with zeros when there was no activity. */
public record UserTrendResponse(
        UUID userId,
        String username,
        LocalDate from,
        LocalDate to,
        List<DailyPerformance> days,
        List<ReasonCount> reasons) {

    public record DailyPerformance(LocalDate date, ControlMetrics controls, DispenserMetrics dispensers) {}
}
