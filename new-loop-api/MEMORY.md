# MEMORY

## Specs in progress
- **008-viewer-and-clarkista-roles** — Status: approved; implemented in the three repos (2026-11-16), not committed.
  - Two read-only branch-scoped roles: VISUALIZADOR (panel, entry/exit controls of their branch, no remito)
    and CLARKISTA (app on a tablet, load forecast by route number).
  - Forecast decided by the user: average of the route's own EXIT controls (what was loaded), not orders;
    load day = next delivery day (Sunday skipped); no data -> "No hay datos". Window of 4 same weekdays and
    no holiday skipping were proposed by Claude and accepted with the plan (2026-11-16).
  - Branch: feat/viewer-and-clarkista-roles (from main).
  - Plan: one central allowlist for read-only roles in the security chain (new endpoints denied to them by
    default); forecast uses EXIT `totalQuantity`, BigDecimal half-up, duplicated route number = not found.
  - Backend: full suite 663 run, 0 failures, 54 skipped. Review fixes: the forecast matcher is GET only
    (anything else under /load-forecast goes through the allowlist -> 403). LoadForecastRepositoryTest
    (findReferenceControls + V43) written but not yet run against Postgres: run it before merging.
  - Panel branch feat/viewer-role, app branch feat/clarkista-load-forecast. Deploy the panel together with
    the backend: the panel maps unknown roles to "controller", so an old panel would show a viewer the full menu
    (the API still answers 403).
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
  - Phase D implemented in new-loop-front, branch feat/performance-page (2026-10-28): "Desempeño" page
    at /statistics/performance, sortable DataTable headers (additive). Checked in the browser against a
    local backend on loop_test as admin and supervisor. Pending: commit approval; deploy, then run
    POST /performance/import once on homologation.
  - Errors are counted per control, not per item (user, 2026-10-28): a control is faulty if the driver
    rejected it, it was edited after submitting, or a supervisor corrected it. FR-26 (products with most
    differences) was removed.
  - Phase E spec change approved (2026-10-29): faulty breakdown (rejected/edited/corrected, distinct
    controls, additive API fields), low sample under 10 submitted ("pocos datos", sorted last by error
    rate), filters in the URL, previous-period deltas, detail prev/next + focus trap, weekly charts over
    31 days, CSV export (BOM, ";", decimal comma, no library). Plan D11–D16, tasks T30–T38 implemented
    (2026-10-29), checked in the browser against loop_test; not committed. Picking one end of the range
    now moves the other to keep it valid (an invalid range in the URL would be reset). Deploy the backend
    before the panel: the new detail reads the breakdown fields.
  - Item differenceQuantity is never computed in the code (always null) despite docs/STOCK_CONTROLS.md.

## Local Postgres for tests
- PostgreSQL 18 on localhost:5432, client at `C:/Program Files/PostgreSQL/18/bin/psql.exe`. Dev DB
  `loop_new_db`; tests use a separate empty `loop_test` (created 2026-10-28), user `postgres`.
  Run: `LOOP_TEST_DB_URL=jdbc:postgresql://localhost:5432/loop_test LOOP_TEST_DB_USERNAME=postgres
  LOOP_TEST_DB_PASSWORD=... ./mvnw test`. The password is the user's: ask for it, never write it to a file.
- Pre-existing failures with Postgres enabled (not from spec 001): NewLoopApiApplicationTests (uses the
  default datasource, no password), DispenserSerialHistoryRepositoryTest.shouldPersistBaggedSerials
  (LazyInitializationException), StockControlDuplicateConcurrencyTest (no security context since 7607aa3).

## Spec 002 — Odoo bagged serials (2026-10-30, implemented)
- UNLOAD repair intake (`/api/v1/repair/create`) sends bagged serials in `embolsados`, not in `equipos`, so Odoo opens no work order for them. `embolsados` is always present there (empty when none).
- Split lives in `DispenserMovement.repairSerials()` / `baggedSerialsToSend()`, both on top of `serialsToSend()`: an excluded (unregistered) bagged serial goes in neither list.
- `OdooRepairParams.embolsados` is `NON_NULL`: the shared no-normalizados body is unchanged.
- An all-bagged unload is still sent with `equipos: []`; if Odoo rejects it, it is a normal retryable error (decided with Gonza, Odoo behavior not confirmed).

## Spec 003 — Session required, expires at midnight (2026-10-30, implemented)
- Before: the app chain was `anyRequest().permitAll()`; the API was open without a token. Now default deny: public only `OPTIONS`, `POST /auth/login`, `GET /health`, `GET /app/version`, `GET /app/download/**`, `/error`. `POST /app/version` is ADMIN only. Stateless (no JSESSIONID).
- 401/403 from the chain are written by `JsonAuthErrorHandler` with the `ApiError` body. An invalid/expired token leaves the request anonymous (public endpoints still work).
- Tokens expire at the next midnight in Argentina (`SessionExpiry`, explicit zone; `JwtService` takes the `Clock`). `app.jwt.expiration` removed.
- Panel and app end the session on 401 and when the token `exp` passes (30 s check + focus), and show "Tu sesión venció" on login.
- Still open: per-endpoint role rules (any logged-in role can call e.g. `POST /users`). Swagger UI now needs a token.
- Deploy backend, panel and new APK the same day; old APKs already log out on 401.

## Spec 005 — Holiday remito, batch printing, quantities in bulks (2026-11-04, implemented, not deployed)
- Holiday calendar (`holidays` table, V42) for every branch; ADMIN adds/deletes, any logged-in user reads. Today and past dates allowed (unplanned holidays).
- The remito checks the calendar against the control date (delivery day) every time it is rendered, so reprints follow the calendar. Holiday remito: no company or fiscal data, title "REMITO", Aguas Formulario/Remito Nº kept. Still sent to Aguas. Business may still change the title or drop the Aguas numbers ("they don't know yet").
- Bulk products (`packQuantity` > 1) print the bulk count alone ("2"); a non-exact quantity prints units with "u" ("15 u"). Only the PDF changes.
- Batch: `POST /stock-controls/remitos` (one PDF, page per remito, request order, 1-100 ids, all-or-nothing 409 listing routes) and `POST /stock-controls/remitos/printed`. Panel exits list: checkbox per row with remito, "Seleccionar no impresos", page size 100 for exits.
- The V42 migration was not run against Postgres locally; it runs on deploy.


## Spec 006 — Route statistics (2026-11-04)
- Spec, plan and tasks approved (2026-11-04). Implemented on branch feat/route-statistics in both repos,
  uncommitted. Endpoint GET /performance/routes; panel page /statistics/routes.
- RouteStatisticsRepositoryTest green on local Postgres (user ran it, 5/5). Reviewer: VERDICT: APPROVED. Still
  page has not been opened in a browser (T9); NFR-1 is measured after deploy.
- A bound parameter repeated in SELECT and GROUP BY becomes two different $n in Postgres (42803): compute
  such expressions once in a CTE. The reviewer caught this in the first version of the rejections query.
- "Routes per day" counts routes that came back (ENTRY controls), per the user's choice.
- The stay average leaves out exits made the next morning; they are shown as "morning exits".
- Arrival time is the ENTRY control's creation time, not the gate (GPS) time; the user accepted it.

## Spec 007 — Driver settlement (2026-11-05)
- Spec approved; plan and tasks approved in advance ("prepare and develop everything tonight"). Counterpart: PAC
  spec 015, whose contract.md fixes the JSON both sides use.
- LOOP asks PAC on demand (`GET /settlements/me` → PAC `/api/integrations/loop/settlement`). It reuses the arrivals
  credentials both ways (user decision: no new secret). `PAC_BASE_URL` defaults to `http://localhost:8097`
  (same host, host network). Answers are cached 60 s; failures are not cached.
- PAC is only asked after the route's ENTRY control: Aguas' expected changes while the route is open (user).
- App: "Rendición" tab, drivers only; the badge comes from a 5-minute foreground check. Push is phase 2 (it needs
  Firebase and a new library).
