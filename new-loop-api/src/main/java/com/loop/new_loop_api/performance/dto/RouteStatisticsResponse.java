package com.loop.new_loop_api.performance.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Route statistics for a range (spec 006). Durations are in seconds; an average, median or rate with no
 * data is null, never zero.
 */
public record RouteStatisticsResponse(
        LocalDate from,
        LocalDate to,
        StaySummary stay,
        List<BranchStay> stayByBranch,
        List<RouteStay> stayByRoute,
        List<HourArrivals> arrivalsByHour,
        List<DailyArrivals> arrivalsByDay,
        List<DailyExits> exitsByDay,
        List<RouteCount> morningExitsByRoute,
        List<RouteRejections> rejectionsByRoute) {

    /** {@code withoutStay}: arrivals with no exit created that day; {@code inconsistent}: exit created before the arrival. */
    public record StaySummary(Double avgSeconds, Double medianSeconds, int stays, int withoutStay, int inconsistent) {}

    public record BranchStay(UUID branchId, String branchName, Double avgSeconds, int stays) {}

    public record RouteStay(UUID routeId, String routeCode, String branchName, Double avgSeconds, int stays) {}

    /** {@code routes} is only filled when the range is a single day. */
    public record HourArrivals(int hour, int count, List<ArrivalRoute> routes) {}

    public record ArrivalRoute(String routeCode, String branchName, LocalTime arrivedAt) {}

    public record DailyArrivals(LocalDate date, int total, List<BranchCount> branches) {}

    public record BranchCount(UUID branchId, String branchName, int count) {}

    public record DailyExits(LocalDate date, int morning, int dayBefore, List<RouteRef> morningRoutes) {}

    public record RouteRef(String routeCode, String branchName) {}

    public record RouteCount(UUID routeId, String routeCode, String branchName, int count) {}

    public record RouteRejections(UUID routeId, String routeCode, String branchName, int rejections,
                                  int rejectedControls, int arrivals, Double rate, List<ReasonCount> reasons) {}

    public record ReasonCount(String reason, int count) {}
}
