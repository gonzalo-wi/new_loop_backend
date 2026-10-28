# Tasks 001 — Operational metrics per user

Status: approved

Phase A (recording, new-loop-api). Phases B, C and D get their own task lists once A is merged.
Checks after every task: `./mvnw test` (Git Bash). Postgres-backed tests run with `LOOP_TEST_DB_URL`.

## Phase A — Recording

- [x] **T1. Migration for events and their items.** FR-11, FR-13, FR-14, FR-18
  - `V40__create_operation_events.sql`: `operation_events` and `operation_event_items`, indexes on
    `(event_date, branch_id)`, `(responsible_user_id, event_date)` and `(subject_id)`, a unique
    `source_audit_id`, and a trigger that rejects UPDATE and DELETE on both tables.
  - Note: the Postgres tests are written but not run locally (user decision); the migrations are validated on
    the homologation deploy before production.
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
