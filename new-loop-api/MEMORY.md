# MEMORY

## Specs in progress
- **001-operational-metrics** — Status: draft (spec written 2026-10-28, open questions answered the same day).
  - Durable, append-only operational events per user (controls, dispenser movements), separate from
    `audit_logs`, which keeps its one-year retention and free-form JSON.
  - Decisions: no retention on events; one-time import of the history still in `audit_logs`;
    metrics shown in a new panel section; the app sends a start time for dispenser loads;
    categorized rejection reasons deferred to a second stage.
  - Why: the audit log is deleted after 365 days and is hard to query, Micrometer counters are
    global, and the stock control status overwrites its rejection history.
  - Answers: admin sees all branches, supervisor only their own; gaps >= 60 min are pauses;
    every exit with control date D expects an entry for that route on D (trucks always come back);
    loads normally take 10-15 min, > 30 min is slow, >= 60 min is discarded as implausible.
  - Clarification review done (2026-10-28) and applied. Key correction: drivers approve/reject
    ENTRY controls (EXIT goes straight to Aguas). Stock controls have no cancel operation.
  - Plan written (draft), four phases: A recording (backend), B metrics API + audit import
    (backend), C load timer (app), D "Desempeño" page (panel). A ships first; run the import
    the same day it is deployed.
  - Plan decisions: events table only for stock-control lifecycle and integration errors;
    dispenser metrics stay on `dispenser_movements` (never purged) plus `load_duration_seconds`,
    `branch_id`, `replaces_movement_id`; the app sends a device-measured duration, not a start time;
    a DB trigger forbids UPDATE/DELETE on events.
  - Never run Flyway migrations by hand on homologation/production: Flyway applies them on deploy.
  - Phase A implemented (2026-10-28), not committed. Full suite: 347 run, 0 failures, 33 skipped.
    Dispenser route codes are typed as the number only ("179") while routes are "Rto 179":
    matched by digits (RouteNumber); a number shared by several routes gives no branch.
    Branch route ranges (user, informative only): Ciudadela 1-200, La Plata 200-300, 400-500 another
    plant; 500+ is another business unit in Ciudadela, not in LOOP yet.
    Open: (2) Postgres tests ran locally on 2026-10-28 and Phase A ones pass;
    (3) nothing else blocks Phase B.
  - Phase B implemented and reviewed (2026-10-28), not committed: GET /performance/summary,
    GET /performance/users/{id}, POST /performance/import (admin only, re-runnable). Range max 92 days
    (FR-27a). Import cutoff: global first live event plus, per control, entries older than its first
    live event minus 5 s (the audit entry is written a few ms before the live event). Load test: monthly
    summary of one branch at ~3x volume in 161 ms (budget 2 s).
  - Phase C committed in react-nati, branch feat/dispenser-load-timer (2026-10-28): silent load timer,
    hidden from controllers (FR-15a). T22 manual check pending on homologation after deploy: a new load
    sends loadDurationSeconds and stores load_duration_seconds; an edit sends none.
  - Next: Phase D (panel "Desempeño" page) task list.
  - Errors are counted per control, not per item (user, 2026-10-28): a control is faulty if the driver
    rejected it, it was edited after submitting, or a supervisor corrected it. FR-26 (products with most
    differences) was removed.
  - Item differenceQuantity is never computed in the code (always null) despite docs/STOCK_CONTROLS.md.

## Local Postgres for tests
- PostgreSQL 18 on localhost:5432, client at `C:/Program Files/PostgreSQL/18/bin/psql.exe`. Dev DB
  `loop_new_db`; tests use a separate empty `loop_test` (created 2026-10-28), user `postgres`.
  Run: `LOOP_TEST_DB_URL=jdbc:postgresql://localhost:5432/loop_test LOOP_TEST_DB_USERNAME=postgres
  LOOP_TEST_DB_PASSWORD=... ./mvnw test`. The password is the user's: ask for it, never write it to a file.
- Pre-existing failures with Postgres enabled (not from spec 001): NewLoopApiApplicationTests (uses the
  default datasource, no password), DispenserSerialHistoryRepositoryTest.shouldPersistBaggedSerials
  (LazyInitializationException), StockControlDuplicateConcurrencyTest (no security context since 7607aa3).
