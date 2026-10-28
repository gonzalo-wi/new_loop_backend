# Tasks 001 — Operational metrics per user

Status: approved

Phase A (recording, new-loop-api). Phases B, C and D get their own task lists once A is merged.
Checks after every task: `./mvnw test` (Git Bash). Postgres-backed tests run with `LOOP_TEST_DB_URL`.

## Phase A — Recording

- [x] **T1. Migration for events and their items.** FR-11, FR-13, FR-14, FR-18
  - `V40__create_operation_events.sql`: `operation_events` and `operation_event_items`, indexes on
    `(event_date, branch_id)`, `(responsible_user_id, event_date)` and `(subject_id)`, a unique
    `source_audit_id`, and a trigger that rejects UPDATE and DELETE on both tables.
  - Postgres tests run locally on 2026-10-28 against an empty `loop_test` database: V1-V41 applied, the
    append-only trigger, the unique audit source and `findByRouteNumber` pass, and the full context starts
    with `ddl-auto=validate`.
  - Done when: a Postgres test shows inserts succeed and update/delete fail; the application context
    still starts with `ddl-auto=validate`.

- [x] **T2. Clock bean, entities, enum and repository.** FR-1–5, FR-10, FR-13
  - `ClockConfig`, `OperationEvent`, `OperationEventItem`, `OperationEventType`, `OperationEventSubject`,
    `OperationEventRepository` (count previous events of a type for a subject).
  - Done when: it compiles and the Postgres test from T1 saves and reads an event with items through
    the repository.

- [x] **T3. Control figures (pure).** FR-1, FR-2, FR-5
  - `ControlFigures.of(StockControl)`: items count, total units and a per-product snapshot (name, total,
    full, exchange), with only products that have quantities.
  - Done when: unit tests cover an empty control, zero items left out, and totals summed.

- [x] **T4. Load duration rule (pure).** FR-16
  - `LoadDuration.accept(Integer seconds)`.
  - Done when: unit tests cover null, negative, 0, 3599 and 3600.

- [x] **T5. Event recorder for stock controls.** FR-1–5, FR-13
  - `OperationEventRecorder`: submitted, edited (with the resubmission flag), approved (previous rounds,
    response time), rejected (round, response time, reason), corrected (reason, items). Actor from
    `CurrentUserProvider`, responsible user = controller (username looked up), snapshot of the route
    code, branch, control type, time from `Clock`.
  - Done when: unit tests with a fixed `Clock` check every field of each event type, including an
    unknown controller and a missing current user.

- [x] **T6. Wire the recorder into the stock-control service.** FR-1–5, FR-11
  - `StockControlServiceImpl` create, update, approve, reject and correct call the recorder in the same
    transaction; update flags a resubmission when the control was `REJECTED_BY_DRIVER`.
  - Done when: `StockControlServiceImplTest` verifies each call and its arguments, and no call happens
    when the operation throws.

- [x] **T7. Integration error events.** FR-10, FR-12
  - The error paths of the Aguas services (controls and equipment) and the Odoo services (dispatch,
    no-normalizados, repair) record an `INTEGRATION_ERROR` event with the integration name.
  - Done when: the existing tests of those services verify the call on error and its absence on success.

- [x] **T8. Dispenser movement columns.** FR-7, FR-8, FR-15, FR-16
  - `V41__add_metrics_columns_to_dispenser_movements.sql` (`load_duration_seconds`, `branch_id`
    backfilled from the route by code, `replaces_movement_id`), entity fields, optional
    `loadDurationSeconds` on the request, the service stores the duration through `LoadDuration`, the
    route's branch, and on edit links to and copies the duration of the replaced movement.
  - The route is matched by number, ignoring letters (`RouteNumber`, `RouteRepository.findByRouteNumber`
    and the same rule in the V41 backfill), decided by the user on 2026-10-28.
  - Done when: `DispenserMovementServiceImplTest` covers a valid duration, a discarded one, a missing
    one, an unknown route (no branch), a number shared by two routes, a code without digits and an edit (link plus copied duration).

- [x] **T9. Docs.** —
  - `docs/DISPENSERS.md`: the optional `loadDurationSeconds` request field.
  - Done when: the doc states the field, its unit and that out-of-range values are ignored.

## Phase B — Metrics API and history import

Status: approved (2026-10-28), implemented

The SQL tasks (T12–T15, T18) are covered by `PostgresIntegrationTest` tests, run locally against the
`loop_test` database.

- [x] **T10. Metrics scope (pure).** FR-28–32
  - `MetricsScope.resolve(AuthenticatedUser, UUID requestedBranchId)`: all branches (admin, no filter),
    one branch (admin with filter, or the supervisor's own branch whatever the filter), denied (other
    roles, supervisor without branch, no user) with typed exceptions mapped to 403.
  - Done when: unit tests cover each case, and the "all branches" scope is the only one that includes
    movements without branch.

- [x] **T11. Range resolution (pure).** FR-21, FR-22, FR-33
  - `MetricsRange.of(from, to, Clock)`: default current week Monday to Sunday; `lastEndedDay` = yesterday
    for coverage; from after to, or more than 92 days, is rejected with 400 (same limit as the existing
    dispenser statistics).
  - Done when: unit tests with a fixed clock cover the default week (including a Sunday and a Monday),
    the coverage cutoff and both invalid ranges.

- [x] **T12. Stock-control metrics per user (SQL).** FR-6, FR-21, FR-27
  - Repository query over `operation_events`: submitted by type, edits, resubmissions, rejection rounds,
    first-pass acceptance rate, average time to driver response, corrections, faulty controls, error
    rate, controls with integration errors; cancelled controls excluded; scope and filters (FR-23).
  - Done when: a Postgres test with seeded events checks every column for two users, including a
    control rejected twice, edited and corrected (one faulty control) and empty averages.

- [x] **T13. Dispenser metrics per user (SQL).** FR-8, FR-9, FR-21, FR-32
  - Repository query over `dispenser_movements`: movements by type, serials, excluded serials, average
    and median load duration, slow loads, average and median gap excluding pauses, pauses, cancellations
    (not edits), movements with integration errors; time and date from the first movement of each edit
    chain; movements without branch only in the "all branches" scope.
  - Done when: a Postgres test covers an edit chain, a pause, a gap across midnight, a slow load and
    the branch scoping.

- [x] **T14. Coverage (SQL).** FR-6, FR-22
  - Expected and missing entries per branch and control date up to the last ended day, with the routes
    missing; entries matched by route and date in any branch.
  - Done when: a Postgres test covers a Saturday exit for Monday, today excluded, two exits for one route
    and a cancelled entry.

- [x] **T15. Daily trend and top reasons (SQL).** FR-24, FR-25
  - Per-day metrics for one responsible user; most frequent rejection and correction reasons per
    responsible user and per actor (exact text, trimmed and case-insensitive).
  - Done when: a Postgres test checks two days of trend and the reason counts.

- [x] **T16. Metrics service, DTOs and endpoints.** FR-21–32
  - `PerformanceMetricsService` builds `PerformanceSummaryResponse` and `UserTrendResponse` from T10–T15;
    `PerformanceController`: `GET /performance/summary` and `GET /performance/users/{userId}`.
  - Done when: service unit tests (repository mocked) cover scope, range, filters and empty values, and
    the endpoints answer 403 for a controller.

- [x] **T17. Audit translator (pure).** FR-17, FR-19, FR-20
  - `AuditEventTranslator`: the ordered audit entries of one control in, events plus skip reasons out
    (rounds, resubmissions, response times, corrections).
  - Done when: unit tests cover create → reject → edit → reject → edit → approve, an edit while pending,
    a correction, an unreadable JSON and a missing field.

- [x] **T18. History import service and endpoint.** FR-17, FR-18, FR-19
  - `POST /performance/import` (admin only): cutoff at the first live event, pages through the audit
    log, translates per control, inserts with `ON CONFLICT DO NOTHING`, returns imported/skipped counts.
  - Done when: a service test (repositories mocked) checks the cutoff, the grouping and the report, and a
    Postgres test shows a second run imports nothing.

- [x] **T19. Docs.** —
  - `docs/PERFORMANCE.md`: both metrics endpoints and the import, in the format of the existing docs.
  - Done when: every field of the responses is described with its unit and when it can be empty.
