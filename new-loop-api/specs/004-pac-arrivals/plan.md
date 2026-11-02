# Plan 004 — Route arrivals for PAC

Status: approved (user, 2026-11-02, chat)

Spec: [spec.md](./spec.md)

Backend only (`new-loop-api`). The new code lives in a small `integrations/pac/` module with the
usual layers (`controller/`, `service/` + `service/iService/`, `dto/`, `mapper/`, plus `security/`
for the credential check), next to the other integrations. It reads stock controls through the
existing `StockControlRepository`, as `integrations/aguas` already does. No entity, table, migration
or existing endpoint changes.

## Files

| File | Change | FR |
|---|---|---|
| `integrations/pac/security/PacIntegrationCredentials.java` (new) | Pure, no Spring. Built from the configured username/password. `isConfigured()` = both non-blank (whitespace counts as blank). `matches(user, pass)`: false when not configured; otherwise compares the SHA-256 of each value with `MessageDigest.isEqual` (constant time, no length leak), always evaluating both before combining | FR-4, FR-5 |
| `integrations/pac/security/PacIntegrationAuthenticationProvider.java` (new) | `AuthenticationProvider` for `UsernamePasswordAuthenticationToken`: on match returns an authenticated token with the single authority `ROLE_PAC_INTEGRATION`; otherwise throws `BadCredentialsException` and logs a WARN with the remote address only (never the supplied values) | FR-4, FR-5, FR-7 |
| `common/security/JsonAuthErrorHandler.java` | New `AuthenticationEntryPoint withMessage(String message)` that reuses the existing private `write` (same `ApiError` body, 401). Existing behavior unchanged | FR-4 |
| `common/config/SecurityConfig.java` | Reads `integrations.pac.username` / `integrations.pac.password` with `@Value("${...:}")`. New `@Bean @Order(2) pacIntegrationSecurityFilterChain`; the app chain moves to `@Order(3)` (actuator stays `@Order(1)`). If the credentials are not configured, logs one WARN at startup ("PAC integration disabled: credentials not set") and still builds the chain | FR-4, FR-5 |
| `integrations/pac/dto/PacArrivalResponse.java` (new) | Record: `routeNumber` (Long), `routeCode`, `branchId` (UUID), `branchName`, `arrivedAt` (`OffsetDateTime`) | FR-1 |
| `integrations/pac/mapper/PacArrivalMapper.java` (new) | `StockControl` → `PacArrivalResponse`: `RouteNumber.of(route.code)`, `createdAt.truncatedTo(SECONDS).atZone(ARGENTINA).toOffsetDateTime()`. Holds `ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires")` | FR-1, FR-3a |
| `integrations/pac/service/iService/PacArrivalService.java` (new) | `List<PacArrivalResponse> getArrivals(LocalDate date)` | FR-1, FR-2 |
| `integrations/pac/service/PacArrivalServiceImpl.java` (new) | `@Transactional(readOnly = true)`. Injects `Clock`, `StockControlRepository`, mapper. Resolves the date, runs the single query, maps, drops null route numbers, sorts. Never calls `AuditService`. A `DataAccessException` is logged (`"PAC arrivals lookup failed for {date}"`, with the exception) and rethrown | FR-1, FR-2, FR-3, FR-3a, FR-6, FR-7 |
| `integrations/pac/controller/PacArrivalController.java` (new) | `GET /integrations/pac/arrivals`, `@RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate date`; returns `ApiResponse.ok(list, ARRIVALS_RETRIEVED_MESSAGE)`. Does not read the principal. A malformed date reaches the existing `MethodArgumentTypeMismatchException` handler → 400 | FR-1, FR-2, FR-6 |
| `src/main/resources/application.properties` | `integrations.pac.username=${PAC_INTEGRATION_USERNAME:}` and `integrations.pac.password=${PAC_INTEGRATION_PASSWORD:}`, empty defaults, with a comment that blank disables the endpoint | FR-5 |
| `docker-compose.yml`, `.env.example` | Pass `PAC_INTEGRATION_USERNAME` / `PAC_INTEGRATION_PASSWORD` to the `app` container (no default); placeholders in the example | FR-4, FR-5 |
| `docs/PAC_ARRIVALS.md` (new) | What it is for, auth, request/response example with `-03:00`, rules (cancelled and digit-less routes left out, every other status counts, repeated numbers not merged), known limitation, how to set the credentials and recreate the container, `curl` checks | — |
| `API.md`, `CHANGELOG.md` | API.md: section for `GET /integrations/pac/arrivals` and a row in the endpoint summary. CHANGELOG `[Unreleased]` → `Added` entry | — |

Docs follow the language the existing `docs/`, `API.md` and `CHANGELOG.md` already use (Spanish), so a
file never mixes languages.

## Security chain

```
@Order(2) pacIntegrationSecurityFilterChain
  securityMatcher("/integrations/pac/**")
  csrf disabled
  sessionManagement STATELESS
  authenticationManager(new ProviderManager(pacProvider))   // no parent manager
  authorizeHttpRequests: anyRequest().authenticated()
  httpBasic(entryPoint = jsonAuthErrorHandler.withMessage("Invalid integration credentials"))
  exceptionHandling(entryPoint = same)
  // no JwtAuthenticationFilter in this chain
```

- A valid user JWT gets 401: this chain has no JWT filter, so the request stays anonymous.
- The PAC credentials don't work elsewhere: the app chain (`@Order(3)`) has no `httpBasic`, so a Basic
  header there is ignored and the request gets the usual 401. The actuator chain keeps its own provider.
- Blank credentials: `PacIntegrationCredentials.matches` is always false, so every request gets 401 and
  startup is not affected.

## Algorithm

```
getArrivals(date):
  target = date ?? clock.instant().atZone(ARGENTINA).toLocalDate()
  controls = repository.findControlsForDate(ENTRY, target, CANCELLED, null)   // join fetch route + branch
  return controls.map(mapper::toResponse)
                 .filter(r -> r.routeNumber != null)
                 .sorted(by arrivedAt, then routeCode, then branchName)
```

A future date simply finds no rows → empty list (FR-2). `createdAt` is a `LocalDateTime` written by
Hibernate in the JVM zone, which `main` sets to Argentina, so it is read as Argentina wall time.

## Decisions
- **D1. Reuse `findControlsForDate(ENTRY, date, CANCELLED, null)`.** It is already one JPQL query with
  `join fetch` of route and branch and a `branchId is null` branch that `pending-arrivals` exercises for
  admins. Sorting ~300 rows in memory is negligible and keeps the ordering rule unit-testable.
  Discarded: a new repository query with `order by sc.createdAt`, which duplicates the filter and
  could only be tested against Postgres.
- **D2. Dedicated `ProviderManager` with no parent.** With the default wiring, a `BadCredentialsException`
  from the PAC provider makes Spring fall back to the global manager built on `UserDetailsServiceImpl`,
  so a LOOP username/password over Basic would pass. Discarded: `.authenticationProvider(...)` on the
  chain (the actuator chain's style), which keeps that fallback.
- **D3. Custom provider with SHA-256 + `MessageDigest.isEqual`.** Discarded: `InMemoryUserDetailsManager`
  + BCrypt like the actuator: `User.withUsername("")` fails at startup with blank credentials (FR-5
  requires a normal start), and it would hash a password at boot for no gain.
- **D4. Explicit Argentina zone in the mapper and the service.** Discarded: `Clock`'s zone or
  `ZoneId.systemDefault()`, which are UTC in tests and only Argentina because `main` sets it.
  Discarded: making `SessionExpiry.ARGENTINA` public, which couples an integration to a security class.
- **D5. `arrivedAt` as `OffsetDateTime` truncated to seconds.** PAC needs minute precision and a stable
  `2026-11-02T15:20:00-03:00` shape; the WebMvc test pins the exact JSON string so a Jackson setting that
  normalizes to UTC would fail the build. Discarded: a preformatted `String`, which loses the type.
- **D6. Module `integrations/pac/`.** It is an integration surface for an external system, like the
  other `integrations/*`. Discarded: adding the endpoint to `StockControlController`, which sits under
  the JWT chain and `/stock-controls`.
- **D7. Logging.** One WARN per rejected call (at most once a minute from a misconfigured PAC), one WARN
  at startup if disabled, ERROR with cause on query failure; nothing on success, nothing in the audit log.

## Test strategy
All with `./mvnw test` from Git Bash (`./mvnw clean test` if JUnit reports "ClassSelector ... resolution failed").

- `PacIntegrationCredentialsTest` (pure): match; wrong user; wrong password; different lengths;
  null/blank/whitespace configured values → never matches and `isConfigured()` false.
- `PacArrivalServiceImplTest` (Mockito repository, real mapper, fixed UTC clock):
  - no date at `2026-11-03T01:30:00Z` → queries `2026-11-02` (Argentina), not the UTC date.
  - `createdAt 2026-11-02T15:20:00.123` → `arrivedAt` `2026-11-02T15:20-03:00`.
  - route "Sin número" left out; "179" in two branches and "R-179" all returned; REJECTED_BY_DRIVER and
    AGUAS_ERROR controls returned; ordered by `arrivedAt`.
  - repository returns nothing → empty list; repository throws → exception propagates (logged).
  - the query is called with `ENTRY`, `CANCELLED` and a null branch.
- `PacArrivalControllerTest` (`@WebMvcTest`, filters off, mocked service): envelope with `data` list and
  message; exact `arrivedAt` string `2026-11-02T15:20:00-03:00`; `date=2026-13-40` → 400; no `date` →
  service called with null; empty list → `data: []`.
- `PacIntegrationSecurityTest` (`@WebMvcTest` of `PacArrivalController` + `UserController`, real
  `SecurityConfig`, same imports and mocks as `SecurityConfigTest`, properties `integrations.pac.*=pac/secret`):
  valid Basic → 200; no auth, wrong password, wrong user → 401 JSON with message "Invalid integration
  credentials"; valid user JWT → 401; a LOOP user's username/password over Basic (mocked
  `UserDetailsService` with a BCrypt user) → 401; PAC Basic on `/users` → 401; no `Set-Cookie`.
  A nested class (or second test class) with blank/whitespace credentials → valid-looking Basic gets 401
  and the context starts.
- `SecurityConfigTest` (existing) keeps passing with the app chain at `@Order(3)`.
- No repository test: D1 reuses a query already in use, and Postgres-backed tests are skipped without
  `LOOP_TEST_DB_URL`. If the local Postgres is available, the manual check below covers the real query.
- Manual (homologación, after deploy): `curl -u <pac> -w '%{time_total}'` for today returns ENTRY controls
  with `-03:00` in under 0.5 s; without credentials and with a user JWT → 401.
