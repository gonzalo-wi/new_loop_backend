# Tasks 006 — Route statistics

Status: approved (user, 2026-11-04, chat)

## Backend (`new-loop-api`)
- [x] **T1. Pure calculator: stays, arrivals per hour and per day.** FR-1, FR-2, FR-3, FR-4, FR-5, FR-6, FR-16
  - Done when: `RouteStatisticsCalculatorTest` covers stay/no exit/inconsistent, median, empty → null, 24
    hours with zeros, single-day routes and zero-filled days; green.
- [x] **T2. Calculator: morning exits and rejections by route.** FR-7, FR-8, FR-9, FR-16
  - Done when: tests cover morning versus day-before (including created after the control date), per-route
    ranking without zeros, rejection rate with zero arrivals and ordering; green.
- [x] **T3. Repository queries.** FR-1, FR-7, FR-9, FR-10, FR-11
  - Done when: `RouteStatisticsRepository` has the arrivals (lateral exit), exits, rejections and top-3
    reasons queries; `RouteStatisticsRepositoryTest` (local Postgres) covers the cancelled exit, an exit
    on another day, scope and snapshot codes; it is skipped without `LOOP_TEST_DB_URL`.
- [x] **T4. Service, DTO and endpoint.** FR-12, FR-13, FR-14, FR-15
  - Done when: `GET /performance/routes` returns `RouteStatisticsResponse`; `RouteStatisticsServiceImplTest`
    covers admin all/one branch, supervisor own branch, supervisor without branch, other roles and too wide
    a range; `./mvnw test` green.
- [x] **T5. Docs.** —
  - Done when: `API.md` row and a "Route statistics" section with the definitions in the metrics doc.

## Panel (`new-loop-front`)
- [x] **T6. Types, service, hook and duration format.** FR-19
  - Done when: `features/route-statistics/{types.ts, services/…, hooks/useRouteStatistics.ts,
    lib/duration.ts}` exist; `npx tsc --noEmit` clean.
- [x] **T7. Page with stay, per-hour and per-day blocks.** FR-17, FR-18, FR-19
  - Done when: the page shows the filters, `StayPanel`, `ArrivalsByHourChart` (single-day click lists
    routes) and `ArrivalsByDayChart` with loading/error/empty states; tsc clean.
- [x] **T8. Morning exits and rejections blocks.** FR-18, FR-19
  - Done when: `MorningExitsPanel` and `RouteRejectionsTable` render with their states; tsc clean.
- [ ] **T9. Route, sidebar and Topbar.** FR-17
  - Done when: `/statistics/routes` is reachable for admin and supervisor with the same guard as
    `/performance`; "Repartos" shows in the sidebar; checked by hand in the browser.

## Validation
- [x] **T10. Validate FR by FR.** All
  - Done when: the reviewer gives `VERDICT: APPROVED`; NFR-1 is checked after deploy.
