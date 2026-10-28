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
    Open: (2) the user will not run the Postgres tests locally: V40/V41, the trigger and findByRouteNumber are
    validated on the homologation deploy (Flyway logs, then one entry control, one driver rejection and
    one dispenser movement by hand) before production;
    (3) the item difference rule, which blocks Phase B.
  - Item differenceQuantity is never computed in the code (always null) despite docs/STOCK_CONTROLS.md.
