package com.loop.new_loop_api.dispensers.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DispenserStatsResponse(
        Range range,
        Volume volume,
        List<DailyPoint> daily,
        List<HeatmapCell> heatmap,
        List<UserStats> users,
        List<TechnicianStats> technicians,
        Quality quality) {

    public record Range(LocalDate from, LocalDate to) {}

    public record Volume(
            int installMovements,
            int withdrawalMovements,
            int installedDispensers,
            int withdrawnDispensers,
            int netDispensers,
            Double withdrawalsPer100,
            Double avgSerialsPerMovement,
            int maxSerialsPerMovement) {}

    public record DailyPoint(
            LocalDate date,
            int installed,
            int withdrawn,
            int installMovements,
            int withdrawalMovements) {}

    public record HeatmapCell(int weekday, int hour, int movements) {}

    public record UserStats(
            String user,
            int movements,
            int installMovements,
            int withdrawalMovements,
            int dispensers,
            double skippedRate,
            double errorRate,
            double excludedRate,
            Double medianGapMinutes) {}

    public record TechnicianStats(
            String technician,
            int routes,
            int installed,
            int withdrawn,
            int movements) {}

    public record Quality(
            int activeMovements,
            int cancelledMovements,
            int sentToAguas,
            int skippedUnregistered,
            int aguasErrors,
            int odooErrors,
            int withExcludedSerials,
            int withoutUser,
            List<QualityIssue> issues) {}

    public record QualityIssue(
            UUID id,
            LocalDateTime createdAt,
            String type,
            String routeCode,
            String technician,
            String user,
            List<QualityIssueKind> kinds,
            int excludedCount) {}
}
