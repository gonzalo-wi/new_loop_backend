# Tasks 004 — Route arrivals for PAC

Status: approved (user, 2026-11-02, chat)

Each task: failing test first, then the code, then `./mvnw test` from Git Bash (green except the known
Postgres-backed tests, which are skipped without `LOOP_TEST_DB_URL`).

## Backend
- [x] **T1. Check the integration credentials in constant time.** FR-4, FR-5
  - Done when: `PacIntegrationCredentialsTest` (match, wrong user, wrong password, different lengths,
    null/blank/whitespace configured values never match) fails first and then passes.
- [x] **T2. Map an ENTRY control to a PAC arrival.** FR-1, FR-3a
  - Done when: a mapper test turns `createdAt 2026-11-02T15:20:00.123` into `2026-11-02T15:20-03:00`,
    "Rto 179" into 179 and a digit-less code into a null route number, with branch id and name; passes.
- [x] **T3. List a day's arrivals with the Argentina date from the clock.** FR-1, FR-2, FR-3, FR-3a, FR-6, FR-7
  - Done when: `PacArrivalServiceImplTest` (fixed UTC clock at `2026-11-03T01:30:00Z` queries
    `2026-11-02`; query called with ENTRY/CANCELLED/null branch; digit-less routes left out; repeated
    numbers and REJECTED_BY_DRIVER/AGUAS_ERROR kept; ordered by arrival time; empty list; repository
    failure propagates) passes, and the service has no `AuditService` dependency.
- [x] **T4. Expose `GET /integrations/pac/arrivals`.** FR-1, FR-2, FR-6
  - Done when: `PacArrivalControllerTest` (envelope with message, exact `"2026-11-02T15:20:00-03:00"`,
    malformed date → 400, missing date → service gets null, `data: []`) passes.
- [x] **T5. Answer 401 with a configurable JSON message.** FR-4
  - Done when: `JsonAuthErrorHandler.withMessage(...)` writes the same `ApiError` body with the given
    message (unit test with mock request/response); existing `SecurityConfigTest` still passes.
- [x] **T6. Protect `/integrations/pac/**` with its own Basic chain.** FR-4, FR-5
  - Done when: `PacIntegrationSecurityTest` passes: valid Basic → 200; none, wrong user, wrong password
    → 401 "Invalid integration credentials"; valid user JWT → 401; LOOP user credentials over Basic →
    401; PAC Basic on `/users` → 401; no `Set-Cookie`; blank/whitespace configured credentials → 401 and
    the context starts. `SecurityConfigTest` still green with the app chain at `@Order(3)`.
- [x] **T7. Wire the configuration.** FR-5
  - Done when: `application.properties` has `integrations.pac.username/password` with empty defaults,
    `docker-compose.yml` passes `PAC_INTEGRATION_USERNAME/PASSWORD` to `app`, `.env.example` has
    placeholders, the startup WARN appears when they are blank, and `./mvnw test` is green.
- [x] **T8. Document the endpoint.**
  - Done when: `docs/PAC_ARRIVALS.md` exists (auth, example, rules, known limitation, setup, curl checks),
    `API.md` has the section and summary row, and `CHANGELOG.md` `[Unreleased]` → `Added` has the entry.
- [ ] **T9. Check it on homologación.** FR-1, FR-4, FR-5 (manual, after the user approves the deploy)
  - Done when: with the credentials in the server `.env` and `docker compose up -d --force-recreate app`,
    `curl -u <pac> -w '%{time_total}' ".../integrations/pac/arrivals?date=<today>"` returns today's ENTRY
    controls with `-03:00` times in under 0.5 s, and the same call without credentials or with a user JWT
    gets 401.
