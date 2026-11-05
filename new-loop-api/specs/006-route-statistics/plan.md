# Plan 006 — Route statistics

Status: approved (user, 2026-11-04, chat)

Spec: `specs/006-route-statistics/spec.md` (approved 2026-11-04).

## Backend
Lives in the existing `performance/` package: it already has the access rules (`MetricsScope`), the range
rules (`MetricsRange`, 92 days, current week by default) and the read-only SQL style
(`NamedParameterJdbcTemplate`). Same layering: controller → service (interface + impl) → repository, with
the arithmetic in a pure domain class.

### Endpoint
- `GET /performance/routes?from&to&branchId` → `ApiResponse<RouteStatisticsResponse>`. New method in
  `PerformanceController`. Like the other `/performance` endpoints, it needs authentication
  (`anyRequest().authenticated()`) and the service enforces the roles. FR-12 to FR-15.

### Repository — `performance/repository/RouteStatisticsRepository.java` (new)
Three read-only queries, all filtered by scope (`:allBranches OR branch_id = :branchId`):
- `findArrivals(range, scope)`: every non-cancelled ENTRY with `control_date` in the range, with its
  branch (id, name), route (id, current code), `created_at`, and the creation time of its stay exit.
  The exit comes from a `LEFT JOIN LATERAL`: the first non-cancelled EXIT of the same route with
  `created_at` within the entry's control date (`>= D AND < D + 1`) and `control_date` between `D + 1`
  and `D + 7`. Bounding `control_date` keeps the lookup an index range scan on the existing
  `idx_stock_controls_route_type_date`: with only `control_date > D`, old days would scan every later exit
  of the route, and that grows with the history. Seven days cover the Sunday skip and holidays. The
  arrivals themselves use `idx_stock_controls_date_type_status`.
  FR-1 to FR-6.
- `findExits(range, scope)`: every non-cancelled EXIT with `control_date` in the range: branch, route,
  `control_date`, `CAST(created_at AS date)`. FR-7, FR-8.
- `findRejections(range, scope)`: `operation_events` with `event_type = CONTROL_REJECTED` and `event_date`
  in the range (cancelled controls left out, as the performance page does), grouped by
  `(route_id, route_code)`, the snapshot code (FR-11): rejection count, `count(DISTINCT subject_id)`, and
  branch. A second query returns the top three reasons per `(route_id, route_code)`
  (`row_number() OVER (PARTITION BY … ORDER BY count DESC)` ≤ 3). FR-9 to FR-11.

`created_at` is a `TIMESTAMP` written in Argentina time: the JVM zone is set in `main`, the same
assumption spec 004 makes. The hour and the date are read straight from it with no conversion.

### Domain — `performance/domain/RouteStatisticsCalculator.java` (new, pure)
Takes the rows plus the range and returns the response blocks. No I/O, no clock.
- Stay per arrival: `exitAt − arrivalAt`. None if there is no exit. Inconsistent if negative.
- Average and median over the valid stays: per branch, per route (sorted by average, descending) and in
  total. Empty (null) when there are none. FR-1 to FR-3, FR-16.
- Arrivals per hour: 24 buckets from `arrivalAt.getHour()`. WHEN `from == to`, each bucket also lists its
  routes (code and branch), ordered by time. FR-4, FR-5.
- Arrivals per day and branch: every date in the range × every branch with an arrival in the range, zero
  where there are none. FR-6.
- Exits: morning when `createdDate >= controlDate`, otherwise "day before". Per date: both counts plus
  the morning routes. Per route: the morning count, routes with none left out, sorted descending. FR-7,
  FR-8.
- Rejections: the repository rows plus the route's arrivals in the range (counted from the arrival rows by
  `route_id`). Rate = rejected controls / arrivals, empty when the route had no arrivals in the range.
  Sorted by rejections, descending. FR-9, FR-16.

```
for a in arrivals:
  stay = a.exitAt == null ? NONE : a.exitAt - a.arrivalAt
  if stay == NONE: withoutStay++
  elif stay < 0: inconsistent++
  else: add stay to total, branch[a.branch], route[a.route]
  hour[a.arrivalAt.hour].count++ (+ route if single day)
  day[a.controlDate][a.branch]++
median(xs) = sorted xs, middle value or average of the two middle values
```

### Service
`RouteStatisticsService` / `RouteStatisticsServiceImpl` in `performance/service`:
`@Transactional(readOnly = true)`. It resolves `MetricsScope` from `CurrentUserProvider`, builds
`MetricsRange.of(from, to, clock)` (injected `Clock`), calls the repository and then the calculator.
FR-12 to FR-15 reuse the existing exceptions (`MetricsAccessDeniedException`,
`InvalidMetricsRangeException`) and their handler mappings.

### DTO — `performance/dto/RouteStatisticsResponse.java` (records)
`from`, `to`; `stay {avgSeconds, medianSeconds, stays, withoutStay, inconsistent}`;
`stayByBranch[] {branchId, branchName, avgSeconds, stays}`;
`stayByRoute[] {routeId, routeCode, branchName, avgSeconds, stays}`;
`arrivalsByHour[24] {hour, count, routes[] {routeCode, branchName, arrivedAt}}`;
`arrivalsByDay[] {date, branches[] {branchId, branchName, count}}`;
`exitsByDay[] {date, morning, dayBefore, morningRoutes[] {routeCode, branchName}}`;
`morningExitsByRoute[] {routeId, routeCode, branchName, count}`;
`rejectionsByRoute[] {routeId, routeCode, branchName, rejections, rejectedControls, arrivals, rate, reasons[] {reason, count}}`.
Seconds are numbers. Formatting ("2 h 15 min") is the panel's job.

### Docs
`API.md`: one summary row. `docs/` metrics doc (the one spec 001 added): a "Route statistics" section with
the definitions (stay, morning exit, rejection rate).

## Panel (`new-loop-front`)
New feature `src/features/route-statistics/`, the same structure as `performance/`:
- `types.ts`: mirrors the DTO.
- `services/route-statistics.service.ts`: `getRouteStatistics({from, to, branchId})`.
- `hooks/useRouteStatistics.ts`: TanStack Query, key `['route-statistics', filters]`.
- `lib/duration.ts`: `formatDuration(seconds)` → "2 h 15 min" / "45 min" / "—" for empty. FR-19.
- `pages/RouteStatisticsPage.tsx`: range picker and branch filter (admins only), reusing
  `performance/lib/dateRange.ts` and `urlFilters.ts` so the filters behave the same. It renders the
  blocks below. FR-17.
- `components/`:
  - `StayPanel` (average, median, coverage, longest routes);
  - `ArrivalsByHourChart` (24 hand-made bars like `DailyBarsChart`; in single-day mode a click lists that
    hour's routes);
  - `ArrivalsByDayChart` (reuses `performance/components/DailyBarsChart`, one series per branch);
  - `MorningExitsPanel` (per-day table with the routes, plus the route ranking);
  - `RouteRejectionsTable` (with `ReasonList` style reasons).
  Each one has its own empty state. Loading and error are shown once for the page: one request feeds
  every block, so they load and fail together. FR-18, FR-19.
- Route `/statistics/routes`, next to `/statistics/performance` and with the same role guard. A sidebar entry "Repartos" next
  to "Desempeño", plus the Topbar title.

## Decisions
- **One endpoint, not one per block**: every block shares the range and the scope, and the queries are
  cheap. Discarded: five endpoints, which means five round trips and five scope checks for one page.
- **Aggregation in Java, not SQL** (except rejections): about 18k arrival rows at most for 92 days across
  all branches. Median and zero filling are simpler and unit-testable in a pure class. Discarded:
  `percentile_cont` plus `generate_series` SQL, harder to test without Postgres.
- **The stay exit is the first non-cancelled exit created that day for a later date**: a cancelled and
  redone exit uses the live one (edge case in the spec).
- **Inside `performance/`, not a new package**: it reuses scope, range, exceptions and the controller
  prefix. Discarded: a new `routestats/` package that would duplicate those or depend on `performance/`
  anyway.
- **No new tables or indexes** (NFR-2). If NFR-1 fails on production data, an index on
  `operation_events (event_type, event_date)` would be the first candidate, in a later migration.

## Tests
- `RouteStatisticsCalculatorTest` (pure): stay, no exit, inconsistent, median even/odd, empty → null, the
  24 hours with zeros, single-day routes, zero-filled days per branch, morning versus day-before (including
  created after the control date), rejection rate with zero arrivals, ordering.
- `RouteStatisticsRepositoryTest` (`@RequiresLocalPostgres`, skipped without `LOOP_TEST_DB_URL`): lateral
  exit pick (cancelled exit ignored, exit of another day ignored), scope filter, rejections grouped by
  snapshot code, top 3 reasons.
- `RouteStatisticsServiceImplTest`: admin all/one branch, supervisor forced to own branch, supervisor
  without branch denied, other roles denied, range too wide.
- Commands: `./mvnw test` (backend); `npx tsc --noEmit` (panel, which has no unit runner). The page is
  checked by hand in the browser against the local or deployed API.

## FR coverage
FR-1 to FR-6: arrivals query + calculator. FR-7, FR-8: exits query + calculator. FR-9 to FR-11:
rejections queries + calculator. FR-12 to FR-15: service (`MetricsScope`, `MetricsRange`). FR-16:
calculator. FR-17 to FR-19: panel. NFR-1: checked after deploy with a 92-day all-branches request. NFR-2:
read-only, no migration. NFR-3: no zone conversion, local timestamps (see Repository).
