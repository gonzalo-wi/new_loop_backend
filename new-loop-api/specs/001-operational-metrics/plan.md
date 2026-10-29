# Plan 001 — Operational metrics per user

Status: approved

Spec: `specs/001-operational-metrics/spec.md`. The work touches three repos, so it is planned in
four phases that can each be merged and released on their own:

| Phase | Repo | What | FRs |
|---|---|---|---|
| A | new-loop-api | Recording: events table, stock-control and integration events, dispenser columns | FR-1–14 |
| B | new-loop-api | Metrics API and history import | FR-17–32 |
| C | react-nati | Load timer in the dispenser screen | FR-15, FR-16 (client side) |
| D | new-loop-front | "Desempeño" page | FR-33–36 |
| E | new-loop-api + new-loop-front | Panel improvements: faulty breakdown, low sample, URL, comparison, detail, CSV | FR-21 (breakdown), FR-37–44 |

Phase E (added 2026-10-29): the backend part is additive, so it ships first and the current panel keeps
working; the panel part needs it deployed only for the breakdown (FR-42).

A must ship first, because live recording has to start before the import runs (FR-17 cuts off at
the first live event). B, C and D only need A. C can ship as soon as A is deployed, because the
backend accepts the new field from day one.

## Technical decisions

### D1. Two stores: an events table for stock controls, plus the existing dispenser table
- **Stock controls → new `operation_events` table.** Rows are only ever added. A control's status
  overwrites its history, and the audit log is deleted after a year, so the control's lifecycle
  (submitted, edited, approved, rejected, corrected) has to be written as separate facts.
- **Dispenser movements → `dispenser_movements`, plus three new columns.** That table is never
  purged and already holds every figure the metrics need: serials, excluded serials, who
  registered it and when. The existing `DispenserStatsRepository` already computes gaps from it.
  What is missing gets added: `load_duration_seconds`, `branch_id` (the route's branch when the
  movement was registered) and `replaces_movement_id` (an edit links the new row to the one it
  cancelled).
- **Integration errors → `operation_events`**, with event type `INTEGRATION_ERROR`, for both
  controls and movements.
- *Discarded:* writing dispenser movements as events too. It would copy data that is already kept
  forever and give the same fact two sources that can disagree.

### D2. Record events with a direct call in the same transaction
`StockControlServiceImpl` calls `OperationEventRecorder` right next to the `auditService.register`
calls that already exist, inside the same `@Transactional` method (FR-11). The integration error
paths call it next to their error audit entries (FR-12).
- *Discarded:* Spring events with a `BEFORE_COMMIT` listener. It adds indirection with no gain, and
  this codebase already writes audit entries with direct calls.

### D3. Enforce immutability in the database
The table gets a trigger that rejects `UPDATE` and `DELETE`, and the entity is marked `@Immutable`
(FR-14). A bug or a manual query cannot rewrite history. The only exception is the migration
itself.

### D4. Per-product quantities in a child table
`operation_event_items` holds, per product, a snapshot of the product name, total, full and exchange
quantities. It is written for `CONTROL_SUBMITTED`, `CONTROL_EDITED` and `CONTROL_CORRECTED` events.
No metric reads them: errors are counted per faulty control (spec, Definitions), not per item. They
stay as a record of what each version of the control said (FR-5's corrected figures), at negligible cost.
- *Discarded:* reading `stock_control_items`. A correction overwrites those rows.

**Faulty controls (Phase B):** a control is faulty if it has at least one `CONTROL_REJECTED`,
`CONTROL_EDITED` or `CONTROL_CORRECTED` event. The query groups those events by `subject_id` and counts
the control once, on the event date of its `CONTROL_SUBMITTED` event, for its responsible user.

### D5. Metrics computed in SQL on every request, no summary tables
The volume is about 150k events a year. Aggregating with `NamedParameterJdbcTemplate` (the same
approach as `DispenserStatsRepository`) and indexes on `(event_date, branch_id)` and
`(responsible_user_id, event_date)` is enough for the 2-second target.
- *Discarded:* nightly summary tables. They add a job, are out of date during the day, and nothing
  shows they are needed yet.

### D6. Time is injected
A new `Clock` bean (`common/config/ClockConfig`, `Clock.systemDefaultZone()`; the JVM already runs on
`America/Argentina/Buenos_Aires`). The recorder, the coverage cutoff ("has D ended?") and the import
cutoff take their time from it. The event date is `occurredAt.toLocalDate()` in that zone
(FR-20, NFR on dates).

### D7. The app measures the duration; the server only checks it
The app sends `loadDurationSeconds`, measured on the device from the first serial added until the
submission succeeds. Both ends of the measurement come from the same clock, so a phone with the
wrong time cannot distort it. That would happen if the app sent a start time and the server
compared it with its own clock.
- *Discarded:* `startedAt` from the device. Clock skew, and the time-zone risk on the client.

### D8. Edits of dispenser movements
`updateMovement` today is a cancel plus a create. The new row gets `replaces_movement_id = old.id`
and copies `load_duration_seconds` from the old one (an edit is not a new load). The metrics:
- count a cancelled movement as a cancellation only if no movement replaces it (FR-9);
- take the time of the first movement in the edit chain as the registration time, for gaps and the
  event date (FR-8).

**Known limit:** edits made before Phase A have no link, so in old history they still count as one
cancellation plus one movement. Nothing reliable in the existing data tells them apart.

### D9. Branch scoping as a pure function
`MetricsScope.resolve(AuthenticatedUser, UUID requestedBranchId)` returns *all branches*, *one
branch*, or throws `ForbiddenException` (FR-28–31). Dispenser movements with no `branch_id` are only
returned when the scope is "all branches" (FR-32).

### D10. History import as an admin endpoint that can be re-run
`POST /performance/import` (ADMIN only) reads the audit entries `CREATE_STOCK_CONTROL`,
`UPDATE_STOCK_CONTROL`, `APPROVE_STOCK_CONTROL`, `REJECT_STOCK_CONTROL` and `CORRECT_STOCK_CONTROL`
older than the cutoff (the `occurred_at` of the first non-imported event, FR-17). It groups them by
control and passes each group, in order, to a pure translator. It returns
`{ imported, skipped, skippedByReason }` (FR-19).
- Running it again does not duplicate (FR-18): `source_audit_id` has a `UNIQUE` index, and the
  insert uses `ON CONFLICT DO NOTHING`.
- `branch_id` on old dispenser movements is filled in by the migration, joining `route_code` with
  the route's current branch.
- *Discarded:* a Flyway Java migration. It would run on every new environment, and a parse failure
  would block the application from starting.

## Files

### Phase A — new-loop-api (recording)
| File | Responsibility | FR |
|---|---|---|
| `db/migration/V40__create_operation_events.sql` | `operation_events`, `operation_event_items`, indexes, the trigger that blocks update/delete, the unique `source_audit_id` | 11–14, 18 |
| `db/migration/V41__add_metrics_columns_to_dispenser_movements.sql` | `load_duration_seconds`, `branch_id` (backfilled from the route), `replaces_movement_id` | 7, 8 |
| `common/config/ClockConfig.java` | `Clock` bean | D6 |
| `performance/entity/OperationEvent.java`, `OperationEventItem.java`, `OperationEventType.java` | Entities (`@Immutable`) and enum: `CONTROL_SUBMITTED`, `CONTROL_EDITED`, `CONTROL_APPROVED`, `CONTROL_REJECTED`, `CONTROL_CORRECTED`, `INTEGRATION_ERROR` | 1–5, 10 |
| `performance/repository/OperationEventRepository.java` | Save; count previous rejections of a control | 3, 4 |
| `performance/domain/ControlFigures.java` | Pure: items count, total units and per-product quantities from a control | 1, 2, 5 |
| `performance/domain/LoadDuration.java` | Pure: `accept(Integer)` returns the seconds, or null if null, negative or ≥ 3600 | 16 |
| `performance/service/OperationEventRecorder.java` | Builds and saves events: actor from `CurrentUserProvider`, responsible user, username and route snapshots, timing from `Clock` | 1–5, 10, 13 |
| `stockcontrols/service/StockControlServiceImpl.java` | Calls the recorder in create, update (flags a resubmission when the status was `REJECTED_BY_DRIVER`), approve, reject and correct | 1–5 |
| `integrations/aguas/service/AguasIntegrationServiceImpl.java`, `AguasEquipmentServiceImpl.java`, `integrations/odoo/service/Odoo*ServiceImpl.java` | Call the recorder next to each error audit entry | 10, 12 |
| `dispensers/dto/CreateDispenserMovementRequest.java` | Optional `loadDurationSeconds` | 15, 16 |
| `dispensers/entity/DispenserMovement.java`, `dispensers/service/DispenserMovementServiceImpl.java` | Store the duration through `LoadDuration`, resolve the route's branch, link and copy on edit | 7, 8, 16 |

### Phase B — new-loop-api (metrics and import)
| File | Responsibility | FR |
|---|---|---|
| `performance/domain/MetricsScope.java` | Pure branch scoping (D9) | 28–32 |
| `performance/domain/AuditEventTranslator.java` | Pure: the ordered audit entries of one control in, events plus skip reasons out. It computes rejection rounds, resubmissions and times to driver response | 17, 19, 20 |
| `performance/repository/PerformanceMetricsRepository.java` | SQL: per-user summary (control events, faulty controls, dispenser movements), daily trend, coverage, top reasons | 21–27 |
| `performance/service/PerformanceMetricsService(Impl).java` | Resolves the scope, the date range and "today" from the `Clock`, builds the responses | 21–32 |
| `performance/service/AuditHistoryImportService(Impl).java` | Cutoff, pages through the audit log, groups by control, translates, inserts with `ON CONFLICT DO NOTHING`, report | 17–20 |
| `performance/controller/PerformanceController.java` | `GET /performance/summary`, `GET /performance/users/{userId}`, `POST /performance/import`; access checked in the services (the project has no `@PreAuthorize`) | 21–32 |
| `performance/dto/*` | `PerformanceSummaryResponse` (rows, coverage, topReasons), `UserTrendResponse`, `ImportReport` | 21–25 |
| `docs/PERFORMANCE.md` | API contract for the panel, following the existing `docs/*.md` | — |

### Phase C — react-nati
| File | Responsibility | FR |
|---|---|---|
| `features/dispensers/lib/loadTimer.ts` | Pure state machine: `start(now)` only from empty, `elapsedSeconds(now)`, `reset()` | 15 |
| `features/dispensers/hooks/useLoadTimer.ts` | Wraps it with an injected `now` and resets on unmount | 15 |
| `app/dispensers.tsx` | Starts on the first serial, sends `loadDurationSeconds` on create (not on edit), keeps the start on a failed submission, resets on clear or after registering | 15 |
| `features/dispensers/types.ts` | Optional field on the payload | 15 |

### Phase D — new-loop-front
| File | Responsibility | FR |
|---|---|---|
| `features/performance/services/performance.service.ts`, `types.ts` | API calls and types | 33–35 |
| `features/performance/lib/dateRange.ts` | Pure: current week Monday to Sunday in local time, formatted without `toISOString()` | 33 |
| `features/performance/lib/sortRows.ts` | Pure: sort by any column, with empty values last | 33 |
| `features/performance/hooks/usePerformanceSummary.ts`, `useUserTrend.ts` | Fetching plus loading, error and empty state | 33–36 |
| `features/performance/components/PerformanceTable.tsx`, `UserTrendPanel.tsx`, `MissingEntriesList.tsx` | Rendering only; the trend chart follows `statistics/components/DailyVolumeChart.tsx` (no chart library) | 33–36 |
| `features/performance/pages/PerformancePage.tsx` | Puts the page together | 33–36 |
| `app/router/index.tsx` plus the navigation | `performance` route, visible to admin and supervisor | 28, 33 |

### Phase E — panel improvements (new-loop-api, then new-loop-front)

**D11. Breakdown in the same SQL.** `CONTROL_SQL` gets three counts next to `faulty_controls`, each the
submitted events whose control has at least one event of that type (`rejected`, `edited`, `corrected`),
with the same unbounded `EXISTS` as the total so they count on the submission date. One query serves the
summary and the daily trend, so both get them. Discarded: deriving them in the panel from `edits`,
`rejectionRounds` and `corrections`, which count events, not distinct controls.

**D12. Filters in the URL with `useSearchParams`.** React Router is already a dependency. Parameters
`from`, `to`, `branch` and `user`; a pure parser validates them (dates, FR-27a range, admin only for
`branch`) and the hook rewrites the address with `replace` so invalid values disappear without a new
history entry. `branch` and `user` are checked against the loaded branches and rows. Discarded:
`localStorage`, which survives a reload but cannot be shared.

**D13. Previous period with a second summary request.** Same endpoint, previous range from a pure
function; its totals feed the tiles. If it fails or is loading, the tiles show no change instead of an
error. Discarded: a backend "previous" block, which changes the API for something the panel can compute.

**D14. Low sample in the sorting.** `sortRows` gets an optional `tierOf(row, key)`: rows sort by tier
first, then by value, so low-sample users go after the rest for `errorPercent` in both directions. Other
columns pass no tier and behave as today.

**D15. Detail navigation.** A table calls `onSelect(user, orderedRows)` with its sorted rows; the page
keeps that list and moves within it, skipping "Sin usuario". Focus trap is a small hook in
`shared/hooks/useFocusTrap.ts` (no dependency): Tab/Shift+Tab cycle inside the dialog, focus returns to
the element that opened it on close.

**D16. CSV without a library.** A pure `lib/csv.ts` builds the text (BOM, `;`, decimal comma, quoting
of `;`, `"` and line breaks); each column gets an `exportValue` next to its `value`, so the file matches
the table. Download through a `Blob` and a temporary link, as `stock/components/RemitoActions.tsx` does.

| File | Responsibility | FR |
|---|---|---|
| backend `performance/repository/PerformanceMetricsRepository.java` | Three breakdown counts in `CONTROL_SQL` and `ControlMetricsRow` | 21 |
| backend `performance/dto/PerformanceSummaryResponse.java`, `performance/mapper/PerformanceMetricsMapper.java` | `rejectedControls`, `editedControls`, `correctedControls` in `ControlMetrics` | 21 |
| backend `docs/PERFORMANCE.md`; front `api.md` | The three new fields | 21 |
| `features/performance/types.ts` | The three fields | 42 |
| `features/performance/lib/lowSample.ts` | Pure: `LOW_SAMPLE_MIN = 10`, `isLowSample(controls)` (submitted 1–9) | 37, 38 |
| `features/performance/lib/sortRows.ts` | Optional tier before the value | 38 |
| `features/performance/lib/urlFilters.ts` | Pure: parse and serialize `from`, `to`, `branch`, `user` | 39, 40 |
| `features/performance/lib/dateRange.ts` | `previousPeriod(range)` | 41 |
| `features/performance/lib/summary.ts` | `totalsDelta(current, previous)`: counts and error-rate points | 41 |
| `features/performance/lib/weekly.ts` | Pure: group days by Monday–Sunday week, summing counts, partial edge weeks | 43 |
| `features/performance/lib/csv.ts` | Pure: rows and columns to CSV text; file name with table and range | 44 |
| `features/performance/hooks/usePerformance.ts` | Filters from the URL; previous-period summary query | 39–41 |
| `shared/hooks/useFocusTrap.ts` | Keeps focus inside a dialog and restores it | 42 |
| `features/performance/components/ControlsTable.tsx`, `DispensersTable.tsx` | "pocos datos", tier, `exportValue`, export button, ordered rows on select | 37, 38, 42, 44 |
| `features/performance/components/UserDetailModal.tsx` | Breakdown, previous/next, focus trap, weekly charts over 31 days | 37, 42, 43 |
| `features/performance/pages/PerformancePage.tsx` | Tiles with the change, the selected list from the URL | 39–42 |

## Algorithms

### Recording a rejection or approval (FR-3, FR-4)
```
round        = repo.countByTypeAndSubjectId(CONTROL_REJECTED, control.id)   // previous rounds
responseSecs = seconds(control.confirmedAt, clock.now())   // confirmedAt is reset on every resubmission
reject:  save(CONTROL_REJECTED, round = round + 1, responseSecs, reason)
approve: save(CONTROL_APPROVED, previousRounds = round, responseSecs)
```

### Gap between trucks (FR-21)
```
movements = non-cancelled dispenser movements in scope and range
registeredAt(m) = createdAt of the first movement in m's replaces chain   // recursive CTE
per responsible user, ordered by registeredAt:
  gap = registeredAt - LAG(registeredAt) within the same date(registeredAt)
avg/median over gaps < 60 min; pauses = count of gaps >= 60 min
```

### Coverage (FR-22)
```
lastEndedDay = today(clock) - 1
for D in [from, min(to, lastEndedDay)]:
  expected = distinct route_id of exits with control_date = D, status != CANCELLED, in scope
  missing  = expected minus distinct route_id of entries with control_date = D, status != CANCELLED (any branch)
```

### Audit translation for one control (FR-17)
```
rounds = 0; sentAt = null
for entry in entries ordered by createdAt:
  CREATE  → SUBMITTED (figures from newValue); sentAt = createdAt if ENTRY
  UPDATE  → EDITED; resubmission = oldValue.status == REJECTED_BY_DRIVER; if so, sentAt = createdAt
  REJECT  → REJECTED, round = ++rounds, responseSecs = createdAt - sentAt, reason
  APPROVE → APPROVED, previousRounds = rounds, responseSecs = createdAt - sentAt
  CORRECT → CORRECTED, before = oldValue figures, after = newValue figures, reason
  JSON unreadable or a required field missing → skip(entry, reason)
```

## Test strategy
- **Pure units (JUnit, no Spring):** `ControlFigures`, `LoadDuration` (null, negative, 0, 3599, 3600),
  `MetricsScope` (admin with and without a filter, a supervisor with a different branch filter, a
  supervisor without a branch, other roles), and `AuditEventTranslator` (every edge case in the spec:
  several rounds, an edit while pending, corrupt JSON).
- **Services (Mockito):** `OperationEventRecorder` with a fixed `Clock`. `StockControlServiceImpl`
  and `DispenserMovementServiceImpl` verify the recorder calls and the new columns, including an edit
  that links and copies the duration.
- **SQL (`PostgresIntegrationTest`, runs with `LOOP_TEST_DB_URL`):** the trigger rejects update and
  delete; summary, gaps with a pause and an edit chain, coverage with a Saturday exit and with today
  excluded, branch scoping, imports that are idempotent. A seeded performance test: one month and one
  branch at about 3× the real volume, under 2 s.
- **App:** `loadTimer` (start only from empty, retry keeps the start, reset) with `npx jest`;
  `npx tsc --noEmit`.
- **Panel:** `npx tsc --noEmit`. There is no unit-test runner, so `dateRange`, `sortRows` and the
  states are checked by hand on homologation (FR-33–36).
- **Phase E:** the backend breakdown is a Postgres repository test (a control rejected and later
  corrected counts once in each; a control edited while pending counts in "edited"; the daily trend puts
  them on the submission day). The panel pure modules (`lowSample`, `sortRows` tiers, `urlFilters`,
  `previousPeriod`, `totalsDelta`, `weekly`, `csv`) still have no runner and are checked by hand in the
  browser with the spec's edge cases; the CSV is opened in Excel.
- Commands: backend `./mvnw test` (Git Bash), app `npx jest` and `npx tsc --noEmit`, front
  `npx tsc --noEmit`.

## Operations
- Deploy Phase A, then run `POST /performance/import` the same day: every day of delay loses a day of
  audit history to the 365-day purge. Until then, raising `AUDIT_RETENTION_DAYS` in the server `.env`
  stops the loss (needs `docker compose up -d --force-recreate app`).
- Make sure every supervisor has a branch before Phase D, or they get FR-31's message.
