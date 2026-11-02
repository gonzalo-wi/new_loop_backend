# Spec 004 — Route arrivals for PAC

Status: approved (user, 2026-11-02, chat)

<!-- Keep the "Status:" line and the [NEEDS CLARIFICATION] markers literal: commands search for them. -->

## Context and goal
PAC (treasury) reconciles each route's cash deposit against what Aguas expects. A route with no deposit
can be "still on its way" or "already back and did not deposit" (a wrong login at the ATM, or money
that never went in), and PAC cannot tell them apart. LOOP knows when each truck came back: the ENTRY
stock control. After it, the driver must deposit within about an hour. The goal is a read-only endpoint
that gives PAC, for a day, which routes already came back and at what time, with its own integration
credentials (PAC has no LOOP user and a user session expires at midnight).

Counterpart: PAC spec 012 (`specs/012-loop-arrivals` in BackendPAC and FrontendPAC).

## Users / actors
- **PAC backend**: polls the endpoint once a minute, Mon–Sat 13:00–22:00 (deposits run ~14:00–21:00).
- **Operations**: sets the integration credentials in the server `.env`.

## Definitions
- **Arrival**: a non-cancelled ENTRY stock control of a route for a control date. There is at most one
  per route and date (duplicate rule already enforced).
- **Arrival time**: when that ENTRY control was created (`createdAt`), in Argentina time.
- **Route number**: the digits of the route code ("Rto 179" → 179), as `RouteNumber` already computes.
  PAC and Aguas use the same route numbers (user, 2026-11-02).

## Functional requirements (EARS)
- FR-1: WHEN `GET /integrations/pac/arrivals?date=YYYY-MM-DD` is called with valid integration
  credentials, THE SYSTEM SHALL return every arrival of that control date: route number, route code,
  branch id, branch name and arrival time with explicit offset (e.g. `2026-11-02T15:20:00-03:00`).
  The list is ordered by arrival time. The zone is the explicit `America/Argentina/Buenos_Aires`, never
  the JVM default (it is only set in `main`, not in tests).
- FR-2: IF `date` is missing, THEN THE SYSTEM SHALL use today in Argentina time from the injected
  `Clock`, never the server's UTC date. A malformed `date` SHALL answer 400; a future date an empty list.
- FR-3: THE SYSTEM SHALL leave cancelled controls and routes whose code has no digits (route number
  null) out of the list. Every other status counts (REJECTED_BY_DRIVER, AGUAS_ERROR...): the truck
  came back either way.
- FR-3a: Route numbers can repeat (the same number in two branches, or "179" and "R-179" in one): THE
  SYSTEM SHALL return one entry per control, never merge them; PAC disambiguates by branch.
- FR-4: THE SYSTEM SHALL accept on `/integrations/pac/**` only HTTP Basic with the integration
  credentials (`PAC_INTEGRATION_USERNAME` / `PAC_INTEGRATION_PASSWORD`), answering 401 with a JSON
  `ApiError` ("Invalid integration credentials") otherwise. It runs in its own chain, ordered before
  the JWT chain (which has no `securityMatcher`), stateless, with a JSON entry point. Those credentials
  SHALL NOT work on any other endpoint, and a valid user JWT SHALL get 401 on this one. The endpoint
  never reads the principal.
- FR-5: IF the integration username or password is unset, blank or whitespace (properties default to
  empty, never to a fallback password), THEN THE SYSTEM SHALL refuse every request to
  `/integrations/pac/**` with 401 and start normally. Credentials are compared in constant time.
- FR-6: THE SYSTEM SHALL answer in the usual `ApiResponse` envelope, with an empty list when no route
  arrived.
- FR-7: Each call SHALL NOT be written to the audit log (it is polled every minute); failures are
  logged with their cause.

## Non-functional requirements
- Read-only: no table, migration or existing endpoint changes.
- One query with route and branch fetched (no N+1); a full day of all branches (~300 arrivals) answers
  within 500 ms, checked on homologación with `curl -w %{time_total}`.
- No rate limit: LAN-only endpoint, polled once a minute by one client.

## Known limitation
- The arrival time is when the ENTRY control was created. A control loaded late (or whose date was
  edited) gives a later time than the real arrival; accepted, it is the only time LOOP records.

## Decisions
- D-1 (user, 2026-11-02): dedicated integration credentials over HTTP Basic, in a dedicated security
  chain like the one `/actuator/**` already uses.
- D-2: a new endpoint instead of `GET /stock-controls?type=ENTRY` (paginated, user JWT, no route number)
  or `/stock-controls/pending-arrivals` (lists who has not arrived, without times).
- D-3: no branch filter: PAC filters by route number and maps branches on its side.
- D-4 (QA review): blank credentials disable the endpoint instead of copying the actuator's
  `changeme` default.

## Out of scope
- Pushing arrivals to PAC (events/RabbitMQ).
- Showing deposited vs expected to the driver (later, needs a PAC endpoint).
- The existing `pending-arrivals` uses the server date when `date` is missing (`LocalDate.now()`);
  the JVM default zone is Argentina, so it is right today, but it is noted for a later review.

## Completion criteria
`./mvnw test` green; on homologación `curl -u <pac creds> ".../integrations/pac/arrivals?date=<today>"`
returns today's ENTRY controls with -03:00 times, and the same call without credentials or with a user
JWT gets 401.
