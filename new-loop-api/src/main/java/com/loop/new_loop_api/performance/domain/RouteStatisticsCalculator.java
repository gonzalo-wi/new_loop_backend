package com.loop.new_loop_api.performance.domain;

import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse.ArrivalRoute;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse.BranchCount;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse.BranchStay;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse.DailyArrivals;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse.DailyExits;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse.HourArrivals;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse.ReasonCount;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse.RouteCount;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse.RouteRef;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse.RouteRejections;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse.RouteStay;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse.StaySummary;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns the raw arrivals, exits and rejections of a range into the route statistics (spec 006). Pure: no
 * I/O and no clock, so every rule is unit-tested.
 */
public final class RouteStatisticsCalculator {

    private static final int HOURS = 24;

    /** A non-cancelled ENTRY control; {@code exitAt} is when that route's exit was created the same day, if any. */
    public record Arrival(UUID branchId, String branchName, UUID routeId, String routeCode,
                          LocalDate controlDate, LocalDateTime arrivedAt, LocalDateTime exitAt) {}

    public record Exit(UUID branchId, String branchName, UUID routeId, String routeCode,
                       LocalDate controlDate, LocalDate createdDate) {

        /** Not made the day before: on its own control date or later. */
        boolean morning() {
            return !createdDate.isBefore(controlDate);
        }
    }

    /** Rejections of a route, grouped by the route code stored with each event; the code may be unknown (null). */
    public record Rejections(UUID routeId, String routeCode, String branchName, int rejections, int rejectedControls) {}

    /** One of the top rejection reasons of a route, already limited per route by the query. */
    public record Reason(UUID routeId, String routeCode, String reason, int count) {}

    private RouteStatisticsCalculator() {}

    public static RouteStatisticsResponse calculate(LocalDate from, LocalDate to, List<Arrival> arrivals,
                                                    List<Exit> exits, List<Rejections> rejections,
                                                    List<Reason> reasons) {
        var days = from.datesUntil(to.plusDays(1)).toList();
        var stays = new ArrayList<Stay>();
        int withoutStay = 0;
        int inconsistent = 0;
        for (var arrival : arrivals) {
            if (arrival.exitAt() == null) {
                withoutStay++;
                continue;
            }
            long seconds = Duration.between(arrival.arrivedAt(), arrival.exitAt()).getSeconds();
            if (seconds < 0) inconsistent++;
            else stays.add(new Stay(arrival, seconds));
        }
        var all = stays.stream().map(Stay::seconds).toList();
        return new RouteStatisticsResponse(
                from, to,
                new StaySummary(average(all), median(all), all.size(), withoutStay, inconsistent),
                stayByBranch(stays),
                stayByRoute(stays),
                arrivalsByHour(arrivals, from.equals(to)),
                arrivalsByDay(arrivals, days),
                exitsByDay(exits, days),
                morningExitsByRoute(exits),
                rejectionsByRoute(rejections, reasons, arrivals));
    }

    private record Stay(Arrival arrival, long seconds) {}

    private static List<BranchStay> stayByBranch(List<Stay> stays) {
        return group(stays, s -> s.arrival().branchId()).values().stream()
                .map(group -> new BranchStay(group.get(0).arrival().branchId(), group.get(0).arrival().branchName(),
                        average(seconds(group)), group.size()))
                .sorted(Comparator.comparing(BranchStay::branchName))
                .toList();
    }

    private static List<RouteStay> stayByRoute(List<Stay> stays) {
        return group(stays, s -> s.arrival().routeId()).values().stream()
                .map(group -> new RouteStay(group.get(0).arrival().routeId(), group.get(0).arrival().routeCode(),
                        group.get(0).arrival().branchName(), average(seconds(group)), group.size()))
                .sorted(Comparator.comparing(RouteStay::avgSeconds).reversed().thenComparing(RouteStay::routeCode))
                .toList();
    }

    private static List<HourArrivals> arrivalsByHour(List<Arrival> arrivals, boolean singleDay) {
        var byHour = arrivals.stream().collect(Collectors.groupingBy(a -> a.arrivedAt().getHour()));
        var hours = new ArrayList<HourArrivals>(HOURS);
        for (int hour = 0; hour < HOURS; hour++) {
            var inHour = byHour.getOrDefault(hour, List.of());
            var routes = singleDay
                    ? inHour.stream()
                            .sorted(Comparator.comparing(Arrival::arrivedAt))
                            .map(a -> new ArrivalRoute(a.routeCode(), a.branchName(), a.arrivedAt().toLocalTime()))
                            .toList()
                    : List.<ArrivalRoute>of();
            hours.add(new HourArrivals(hour, inHour.size(), routes));
        }
        return hours;
    }

    private static List<DailyArrivals> arrivalsByDay(List<Arrival> arrivals, List<LocalDate> days) {
        // Every branch seen in the range, on every day, so a branch with no trucks one day shows a zero.
        var branches = new TreeMap<String, UUID>();
        arrivals.forEach(a -> branches.put(a.branchName(), a.branchId()));
        var counts = arrivals.stream().collect(Collectors.groupingBy(Arrival::controlDate,
                Collectors.groupingBy(Arrival::branchId, Collectors.counting())));
        return days.stream().map(day -> {
            var ofDay = counts.getOrDefault(day, Map.of());
            var perBranch = branches.entrySet().stream()
                    .map(b -> new BranchCount(b.getValue(), b.getKey(), ofDay.getOrDefault(b.getValue(), 0L).intValue()))
                    .toList();
            return new DailyArrivals(day, perBranch.stream().mapToInt(BranchCount::count).sum(), perBranch);
        }).toList();
    }

    private static List<DailyExits> exitsByDay(List<Exit> exits, List<LocalDate> days) {
        var byDay = exits.stream().collect(Collectors.groupingBy(Exit::controlDate));
        return days.stream().map(day -> {
            var ofDay = byDay.getOrDefault(day, List.of());
            var morning = ofDay.stream().filter(Exit::morning)
                    .sorted(Comparator.comparing(Exit::routeCode))
                    .map(e -> new RouteRef(e.routeCode(), e.branchName()))
                    .toList();
            return new DailyExits(day, morning.size(), ofDay.size() - morning.size(), morning);
        }).toList();
    }

    private static List<RouteCount> morningExitsByRoute(List<Exit> exits) {
        return group(exits.stream().filter(Exit::morning).toList(), Exit::routeId).values().stream()
                .map(group -> new RouteCount(group.get(0).routeId(), group.get(0).routeCode(),
                        group.get(0).branchName(), group.size()))
                .sorted(Comparator.comparing(RouteCount::count).reversed().thenComparing(RouteCount::routeCode))
                .toList();
    }

    private static List<RouteRejections> rejectionsByRoute(List<Rejections> rejections, List<Reason> reasons,
                                                           List<Arrival> arrivals) {
        var arrivalsPerRoute = arrivals.stream().collect(Collectors.groupingBy(Arrival::routeId, Collectors.counting()));
        var reasonsPerRoute = reasons.stream().collect(Collectors.groupingBy(r -> new RouteKey(r.routeId(), r.routeCode()),
                LinkedHashMap::new, Collectors.toList()));
        return rejections.stream().map(r -> {
            int routeArrivals = arrivalsPerRoute.getOrDefault(r.routeId(), 0L).intValue();
            var routeReasons = reasonsPerRoute.getOrDefault(new RouteKey(r.routeId(), r.routeCode()), List.of()).stream()
                    .sorted(Comparator.comparing(Reason::count).reversed())
                    .map(reason -> new ReasonCount(reason.reason(), reason.count()))
                    .toList();
            return new RouteRejections(r.routeId(), r.routeCode(), r.branchName(), r.rejections(), r.rejectedControls(),
                    routeArrivals, routeArrivals == 0 ? null : (double) r.rejectedControls() / routeArrivals,
                    routeReasons);
        }).sorted(Comparator.comparing(RouteRejections::rejections).reversed()
                        .thenComparing(RouteRejections::routeCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private record RouteKey(UUID routeId, String routeCode) {}

    private static <T, K> Map<K, List<T>> group(List<T> items, Function<T, K> key) {
        return items.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
    }

    private static List<Long> seconds(List<Stay> stays) {
        return stays.stream().map(Stay::seconds).toList();
    }

    private static Double average(List<Long> values) {
        return values.isEmpty() ? null : values.stream().mapToLong(Long::longValue).average().orElseThrow();
    }

    private static Double median(List<Long> values) {
        if (values.isEmpty()) return null;
        var sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(middle).doubleValue()
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }
}
