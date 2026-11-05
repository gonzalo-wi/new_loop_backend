package com.loop.new_loop_api.performance.domain;

import com.loop.new_loop_api.performance.domain.RouteStatisticsCalculator.Arrival;
import com.loop.new_loop_api.performance.domain.RouteStatisticsCalculator.Exit;
import com.loop.new_loop_api.performance.domain.RouteStatisticsCalculator.Reason;
import com.loop.new_loop_api.performance.domain.RouteStatisticsCalculator.Rejections;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse.BranchCount;
import com.loop.new_loop_api.performance.dto.RouteStatisticsResponse.RouteRef;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RouteStatisticsCalculatorTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 2);
    private static final LocalDate TUE = MON.plusDays(1);
    private static final LocalDate WED = MON.plusDays(2);

    private final UUID north  = UUID.randomUUID();
    private final UUID south  = UUID.randomUUID();
    private final UUID r10    = UUID.randomUUID();
    private final UUID r20    = UUID.randomUUID();
    private final UUID r30    = UUID.randomUUID();

    // ---------- stay ----------

    @Test
    void shouldAverageTheStayFromArrivalToTheExitMadeThatDay() {
        var result = calculate(MON, WED, List.of(
                arrival(north, r10, MON, 14, 0, MON.atTime(16, 0)),   // 2 h
                arrival(north, r10, TUE, 15, 0, TUE.atTime(16, 0)),   // 1 h
                arrival(south, r20, MON, 13, 0, MON.atTime(19, 0))),  // 6 h
                List.of(), List.of());

        var stay = result.stay();
        assertThat(stay.stays()).isEqualTo(3);
        assertThat(stay.avgSeconds()).isEqualTo(3 * 3600.0);
        assertThat(stay.medianSeconds()).isEqualTo(2 * 3600.0);
        assertThat(stay.withoutStay()).isZero();
        assertThat(stay.inconsistent()).isZero();
    }

    @Test
    void shouldLeaveArrivalsWithoutExitOrWithAnEarlierExitOutOfTheAverage() {
        var result = calculate(MON, MON, List.of(
                arrival(north, r10, MON, 14, 0, MON.atTime(15, 0)),   // 1 h
                arrival(north, r20, MON, 14, 0, null),                // exit next morning
                arrival(north, r30, MON, 14, 0, MON.atTime(13, 0))),  // exit before arrival
                List.of(), List.of());

        assertThat(result.stay().stays()).isEqualTo(1);
        assertThat(result.stay().avgSeconds()).isEqualTo(3600.0);
        assertThat(result.stay().withoutStay()).isEqualTo(1);
        assertThat(result.stay().inconsistent()).isEqualTo(1);
    }

    @Test
    void shouldUseTheMiddlePairForAnEvenMedian() {
        var result = calculate(MON, MON, List.of(
                arrival(north, r10, MON, 10, 0, MON.atTime(11, 0)),   // 1 h
                arrival(north, r20, MON, 10, 0, MON.atTime(12, 0)),   // 2 h
                arrival(north, r30, MON, 10, 0, MON.atTime(16, 0)),   // 6 h
                arrival(south, UUID.randomUUID(), MON, 10, 0, MON.atTime(20, 0))), // 10 h
                List.of(), List.of());

        assertThat(result.stay().medianSeconds()).isEqualTo(4 * 3600.0);
    }

    @Test
    void shouldReturnEmptyFiguresInsteadOfZeroWithoutData() {
        var result = calculate(MON, TUE, List.of(arrival(north, r10, MON, 14, 0, null)), List.of(), List.of());

        assertThat(result.stay().avgSeconds()).isNull();
        assertThat(result.stay().medianSeconds()).isNull();
        assertThat(result.stay().stays()).isZero();
        assertThat(result.stayByBranch()).isEmpty();
        assertThat(result.stayByRoute()).isEmpty();
    }

    @Test
    void shouldSplitTheStayPerBranchAndPerRouteLongestFirst() {
        var result = calculate(MON, TUE, List.of(
                arrival(north, r10, MON, 14, 0, MON.atTime(15, 0)),   // 1 h
                arrival(north, r10, TUE, 14, 0, TUE.atTime(17, 0)),   // 3 h
                arrival(north, r20, MON, 14, 0, MON.atTime(19, 0)),   // 5 h
                arrival(south, r30, MON, 14, 0, MON.atTime(14, 30))), // 30 min
                List.of(), List.of());

        assertThat(result.stayByRoute()).extracting(RouteStatisticsResponse.RouteStay::routeId)
                .containsExactly(r20, r10, r30);
        assertThat(result.stayByRoute().get(1).avgSeconds()).isEqualTo(2 * 3600.0);
        assertThat(result.stayByRoute().get(1).stays()).isEqualTo(2);
        assertThat(result.stayByBranch()).extracting(RouteStatisticsResponse.BranchStay::branchId)
                .containsExactly(north, south);
        assertThat(result.stayByBranch().get(0).avgSeconds()).isEqualTo(3 * 3600.0);
        assertThat(result.stayByBranch().get(0).stays()).isEqualTo(3);
    }

    // ---------- arrivals per hour and per day ----------

    @Test
    void shouldAlwaysReturnTheTwentyFourHoursWithoutRoutesForARange() {
        var result = calculate(MON, TUE, List.of(
                arrival(north, r10, MON, 14, 5, null),
                arrival(north, r20, TUE, 14, 55, null),
                arrival(south, r30, MON, 0, 10, null)),
                List.of(), List.of());

        assertThat(result.arrivalsByHour()).hasSize(24);
        assertThat(result.arrivalsByHour().get(0).count()).isEqualTo(1);
        assertThat(result.arrivalsByHour().get(13).count()).isZero();
        assertThat(result.arrivalsByHour().get(14).count()).isEqualTo(2);
        assertThat(result.arrivalsByHour()).allSatisfy(h -> assertThat(h.routes()).isEmpty());
    }

    @Test
    void shouldListTheRoutesOfEachHourForASingleDayInArrivalOrder() {
        var result = calculate(MON, MON, List.of(
                arrival(north, r10, MON, 14, 40, null),
                arrival(south, r20, MON, 14, 5, null)),
                List.of(), List.of());

        var hour = result.arrivalsByHour().get(14);
        assertThat(hour.routes()).extracting(RouteStatisticsResponse.ArrivalRoute::routeCode)
                .containsExactly(code(r20), code(r10));
        assertThat(hour.routes().get(0).arrivedAt()).isEqualTo(LocalTime.of(14, 5));
        assertThat(hour.routes().get(0).branchName()).isEqualTo(name(south));
    }

    @Test
    void shouldCountArrivalsPerDayAndBranchFillingZeros() {
        var result = calculate(MON, WED, List.of(
                arrival(north, r10, MON, 14, 0, null),
                arrival(north, r20, MON, 15, 0, null),
                arrival(south, r30, TUE, 15, 0, null)),
                List.of(), List.of());

        assertThat(result.arrivalsByDay()).hasSize(3);
        var monday = result.arrivalsByDay().get(0);
        assertThat(monday.date()).isEqualTo(MON);
        assertThat(monday.total()).isEqualTo(2);
        assertThat(monday.branches()).containsExactly(
                new BranchCount(north, name(north), 2), new BranchCount(south, name(south), 0));
        var wednesday = result.arrivalsByDay().get(2);
        assertThat(wednesday.total()).isZero();
        assertThat(wednesday.branches()).extracting(BranchCount::count).containsExactly(0, 0);
    }

    // ---------- morning exits ----------

    @Test
    void shouldTellMorningExitsFromExitsMadeTheDayBefore() {
        var result = calculate(MON, TUE, List.of(), List.of(
                exit(north, r10, TUE, MON),        // day before
                exit(north, r20, TUE, TUE),        // morning
                exit(south, r30, TUE, WED),        // made after its date: not the day before either
                exit(north, r10, MON, MON.minusDays(2))), // Saturday for Monday: day before
                List.of());

        assertThat(result.exitsByDay()).hasSize(2);
        var monday = result.exitsByDay().get(0);
        assertThat(monday.morning()).isZero();
        assertThat(monday.dayBefore()).isEqualTo(1);
        var tuesday = result.exitsByDay().get(1);
        assertThat(tuesday.morning()).isEqualTo(2);
        assertThat(tuesday.dayBefore()).isEqualTo(1);
        assertThat(tuesday.morningRoutes()).containsExactly(
                new RouteRef(code(r20), name(north)), new RouteRef(code(r30), name(south)));
    }

    @Test
    void shouldRankRoutesByMorningExitsLeavingOutRoutesWithNone() {
        var result = calculate(MON, WED, List.of(), List.of(
                exit(north, r10, MON, MON),
                exit(north, r20, MON, MON),
                exit(north, r20, TUE, TUE),
                exit(north, r30, TUE, MON)),
                List.of());

        assertThat(result.morningExitsByRoute()).extracting(RouteStatisticsResponse.RouteCount::routeId)
                .containsExactly(r20, r10);
        assertThat(result.morningExitsByRoute().get(0).count()).isEqualTo(2);
    }

    // ---------- rejections ----------

    @Test
    void shouldRateRejectionsAgainstTheRouteArrivalsMostRejectionsFirst() {
        var result = calculate(MON, WED,
                List.of(arrival(north, r10, MON, 14, 0, null),
                        arrival(north, r10, TUE, 14, 0, null),
                        arrival(north, r10, WED, 14, 0, null),
                        arrival(north, r10, WED.minusDays(0), 15, 0, null),
                        arrival(north, r20, MON, 14, 0, null)),
                List.of(),
                List.of(new Rejections(r20, code(r20), name(north), 1, 1),
                        new Rejections(r10, code(r10), name(north), 3, 2),
                        new Rejections(r30, "Rto 30 viejo", name(south), 1, 1)),
                List.of(new Reason(r10, code(r10), "Faltan bidones", 2),
                        new Reason(r10, code(r10), "Sobran sodas", 1),
                        new Reason(r30, "Rto 30 viejo", "Mal cargado", 1)));

        var rows = result.rejectionsByRoute();
        assertThat(rows).extracting(RouteStatisticsResponse.RouteRejections::routeId).containsExactly(r10, r20, r30);
        assertThat(rows.get(0).arrivals()).isEqualTo(4);
        assertThat(rows.get(0).rate()).isEqualTo(0.5);
        assertThat(rows.get(0).reasons()).extracting(RouteStatisticsResponse.ReasonCount::reason)
                .containsExactly("Faltan bidones", "Sobran sodas");
        assertThat(rows.get(1).rate()).isEqualTo(1.0);
        assertThat(rows.get(1).reasons()).isEmpty();
        // No arrivals in the range (e.g. the entry is from the day before): no rate, never zero.
        assertThat(rows.get(2).arrivals()).isZero();
        assertThat(rows.get(2).rate()).isNull();
        assertThat(rows.get(2).reasons()).singleElement()
                .satisfies(r -> assertThat(r.reason()).isEqualTo("Mal cargado"));
    }

    @Test
    void shouldListRejectionsOfAnUnknownRouteLastOnATie() {
        var result = calculate(MON, MON, List.of(), List.of(), List.of(
                new Rejections(null, null, null, 1, 1),
                new Rejections(r10, code(r10), name(north), 1, 1)));

        assertThat(result.rejectionsByRoute()).extracting(RouteStatisticsResponse.RouteRejections::routeCode)
                .containsExactly(code(r10), null);
        assertThat(result.rejectionsByRoute().get(1).rate()).isNull();
    }

    @Test
    void shouldKeepTheRangeInTheResponse() {
        var result = calculate(MON, WED, List.of(), List.of(), List.of());

        assertThat(result.from()).isEqualTo(MON);
        assertThat(result.to()).isEqualTo(WED);
        assertThat(result.exitsByDay()).extracting(RouteStatisticsResponse.DailyExits::date)
                .containsExactly(MON, TUE, WED);
        assertThat(result.arrivalsByDay()).allSatisfy(d -> assertThat(d.branches()).isEmpty());
    }

    // ---------- helpers ----------

    private static RouteStatisticsResponse calculate(LocalDate from, LocalDate to, List<Arrival> arrivals,
                                                     List<Exit> exits, List<Rejections> rejections) {
        return calculate(from, to, arrivals, exits, rejections, List.of());
    }

    private static RouteStatisticsResponse calculate(LocalDate from, LocalDate to, List<Arrival> arrivals,
                                                     List<Exit> exits, List<Rejections> rejections,
                                                     List<Reason> reasons) {
        return RouteStatisticsCalculator.calculate(from, to, arrivals, exits, rejections, reasons);
    }

    private Arrival arrival(UUID branch, UUID route, LocalDate day, int hour, int minute, LocalDateTime exitAt) {
        return new Arrival(branch, name(branch), route, code(route), day, day.atTime(hour, minute), exitAt);
    }

    private Exit exit(UUID branch, UUID route, LocalDate controlDate, LocalDate createdDate) {
        return new Exit(branch, name(branch), route, code(route), controlDate, createdDate);
    }

    private String name(UUID branch) {
        return branch.equals(north) ? "Norte" : "Sur";
    }

    private String code(UUID route) {
        if (route.equals(r10)) return "Rto 10";
        if (route.equals(r20)) return "Rto 20";
        if (route.equals(r30)) return "Rto 30";
        return "Rto " + route.toString().substring(0, 4);
    }
}
