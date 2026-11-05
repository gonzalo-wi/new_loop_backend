# Plan 007 — Driver settlement in the app

Status: approved (user, 2026-11-05, chat: approved in advance, "prepare and develop everything tonight")

Spec: `specs/007-driver-settlement/spec.md`. Contract with PAC: PAC repo
`specs/015-loop-driver-settlement/contract.md` (copied below in short).

## Backend (`new-loop-api`)

### Outbound client — `integrations/pac/`
- `client/PacSettlementClient` (Feign, same style as `PowerfleetClient`):
  `GET /api/integrations/loop/settlement?routeNumber&date` with an explicit `Authorization` header (Basic,
  preemptive). Base URL `integrations.pac.base-url` = `${PAC_BASE_URL:http://localhost:8097}`. Both backends run
  with host network on the same server, so the default needs no `.env` change. Timeouts in
  `application.properties`: connect 2 s, read 5 s (FR-5, NFR-1).
- `dto/PacSettlementResponse`: a record mirroring the contract (BigDecimal amounts, OffsetDateTime times).
- Credentials: the existing `integrations.pac.username/password` (`PAC_INTEGRATION_*`), the same pair PAC uses on
  `/integrations/pac/**` (user, 2026-11-05: reuse it both ways). Blank → the feature answers UNAVAILABLE without
  calling PAC (FR-9).

### Gateway with cache — `settlements/service/PacSettlementGateway`
- `lookup(routeNumber, date)` → `PacLookup` (sealed: `Found(figures)`, `NotLoaded`, `Unavailable(reason)`).
- 404 → `NotLoaded`. Any other Feign or IO failure, or a timeout → `Unavailable`, logged with its cause (never the
  credentials).
- 60-second cache per (routeNumber, date) in a `ConcurrentHashMap`, with expiry from the injected `Clock` (FR-7).
  `Found` and `NotLoaded` are cached; `Unavailable` is not, so a refresh retries. No new dependency.

### Feature — `settlements/` (new package, layered like `holidays/`)
- `controller/SettlementController`: `GET /settlements/me`.
- `service/DriverSettlementServiceImpl`:
  - The current user must be REPARTIDOR, else `ForbiddenException` (FR-8). SecurityConfig also adds
    `/settlements/**` `hasRole("REPARTIDOR")`.
  - Routes: active routes with `driver_id` = user (new `RouteRepository.findByDriverIdAndActiveTrueOrderByCodeAsc`).
  - Per route, the state comes from the pure `SettlementStates.resolve(...)` (below). PAC is called only when
    the route has a non-cancelled ENTRY control for today (`existsByTypeAndRouteIdAndControlDateAndStatusNot`,
    already used for duplicates) and a route number.
  - Today is `LocalDate.now(clock)`; `Clock` is in Argentina time.
- `domain/SettlementState.resolve` (pure, on the enum): `(hasEntry, routeNumber, lookup)` → `WAITING_ENTRY | NO_ROUTE_NUMBER |
  NOT_LOADED | UNAVAILABLE | AVAILABLE`. FR-2, FR-4, FR-5, FR-6.
- `dto/DriverSettlementResponse`: `date`, `routes[] {routeId, routeCode, state, figures|null}`.
  `figures` = the contract fields except `routeNumber`/`date`.

```
for route in driverRoutes:
  hasEntry = exists ENTRY(route, today, status != CANCELLED)
  number   = RouteNumber.of(route.code)
  lookup   = hasEntry && number != null ? gateway.lookup(number, today) : null
  state    = SettlementState.resolve(hasEntry, number, lookup)
```

### Docs
`docs/DRIVER_FLOW.md` section "Mi rendición"; `API.md` row; `docs/PAC_ARRIVALS.md` points to the reverse
direction.

## App (`react-nati`)
- `features/settlement/`:
  - `types.ts`;
  - `api.ts` (`GET /settlements/me`);
  - `hooks/useSettlement.ts`: the app's own fetch pattern (no TanStack Query in the app), loading on focus and
    pull-to-refresh; `hooks/useSettlementWatcher.ts`, mounted in the tab layout, checks every 5 minutes while the
    app is in the foreground and drives the badge through `store/settlementStore.ts` (FR-15);
  - `lib/format.ts` (ARS and time, FR-16);
  - `lib/seen.ts`: pure "what is new since last seen" over the deposit time and the final difference, persisted
    with the app's existing storage (FR-15).
- `app/(tabs)/settlement.tsx`: tab "Rendición", only for REPARTIDOR (`href: isDriver ? undefined : null`), with
  a badge while something is unseen. One card per route, with the states of FR-11..FR-14.
- Version bump to 1.2.14 (`app.json` `version` + `versionCode`), not built or published (user, 2026-11-05).

## Decisions
- **Through LOOP's backend, not the app → PAC**: PAC has no driver users or auth, and only LOOP knows which routes
  belong to the driver.
- **On demand with a 60 s cache, no poller**: one driver's screen asks for its routes; nothing runs when nobody
  looks. Discarded: polling PAC for every route, which is more load for data nobody opened.
- **Unavailable is not cached**: the driver's pull-to-refresh must be able to retry at once.
- **Reuse of the arrivals credentials**: no new secret to distribute (user decision).

## Tests
- `SettlementStateTest` (pure, every state).
- `PacSettlementGatewayTest`: Found/NotLoaded cached for 60 s with a fixed clock, Unavailable not cached, 404 vs
  500 vs timeout, blank credentials never call PAC, Basic header built right.
- `DriverSettlementServiceImplTest`: non-driver denied, only own routes, waiting entry without calling PAC,
  cancelled entry, route without digits.
- `SecurityConfigTest`: `/settlements/me` 401 without token, 403 for other roles.
- App: jest for `lib/format.ts` and `lib/seen.ts`, and `npx tsc --noEmit`.
- Commands: `./mvnw test`; app `npx jest`, `npx tsc --noEmit`.

## FR coverage
FR-1..FR-9: backend (service, gateway, states, security). FR-10..FR-16: app. NFR-1: 2 s + 5 s timeouts.
NFR-2: credentials only in `application.properties` placeholders and the Feign header.
