# Spec 007 — Driver settlement in the app

Status: approved (user, 2026-11-05, chat)

<!-- Keep the "Status:" line and the [NEEDS CLARIFICATION] markers literal: commands search for them. -->

## Context and goal
Drivers deposit their route's cash in a Minibank ATM and hand in checks and withholdings, without
knowing what Aguas expects from them or whether the deposit matched. They find out about a shortage
days later. PAC (treasury) holds both numbers. This spec adds a "Mi rendición" screen to the mobile
app: after the route's entry control, the driver sees what Aguas expects, what they deposited and,
once treasury reviewed it, the difference. LOOP's backend reads the data from PAC (PAC spec 015) and
only shows each driver their own routes. The amount Aguas expects can change while the route is
open (a sale is modified), which is why it is shown only after the entry control.

Counterpart: PAC spec 015 (`specs/015-loop-driver-settlement`). Push notifications are a later phase.

## Users / actors
- **Driver** (mobile app, role REPARTIDOR): reads their settlement.
- **LOOP backend**: asks PAC for the driver's route and date.
- **Operations**: sets PAC's address and integration credentials in the server `.env`.

## Definitions
- **Driver's route**: a route whose assigned driver is the logged-in user.
- **Settlement day**: today in Argentina time.
- **Settlement available**: the driver's route has a non-cancelled ENTRY control for the settlement day.
- **Route number**: the digits of the route code ("Rto 179" → 179), as LOOP already computes for PAC.
- **Final difference**, **status group**: as PAC spec 015 defines them.

## Functional requirements (EARS)

### Backend
- FR-1: WHEN a driver requests their settlement, THE SYSTEM SHALL return one entry per driver's route,
  for the settlement day.
- FR-2: WHILE the settlement is not available for a route, THE SYSTEM SHALL return that route as
  "waiting for the entry control" without calling PAC.
- FR-3: WHEN the settlement is available, THE SYSTEM SHALL return PAC's figures for that route number and
  day: expected cash, checks, withholdings and total; deposited cash; confirmed checks and withholdings;
  total received; final difference and its type; status group; the update times; the live cash difference
  (deposited cash minus expected cash); when the deposits were read from PIMS; and whether that read is live.
- FR-4: IF PAC answers that it has no settlement for the route yet, THEN THE SYSTEM SHALL return the route
  as "not loaded in treasury yet".
- FR-5: IF PAC is not configured, unreachable, slow (over 5 seconds) or answers an error, THEN THE SYSTEM
  SHALL return the route as "treasury unavailable", log the cause, and still answer the request.
- FR-6: IF the route code has no digits, THEN THE SYSTEM SHALL return the route as "no route number".
<!-- FR-7 changed 2026-11-05 (user, chat): the deposit must show right after the driver presses "finalizar". -->
- FR-7: THE SYSTEM SHALL keep each PAC answer for 10 seconds per route and day, so repeated opens do not hit
  PAC each time while a new deposit still shows within seconds.
- FR-8: IF the requester is not a driver, THEN THE SYSTEM SHALL deny access. A driver SHALL only ever get
  their own routes; the request takes no route or driver parameter.
- FR-9: THE SYSTEM SHALL read PAC's address and integration credentials from the environment; without
  them the feature answers "treasury unavailable" for every available route.

### App
- FR-10: THE app SHALL show a "Mi rendición" screen to drivers, reachable from the main navigation.
- FR-11: WHILE a route is waiting for the entry control, THE app SHALL say so ("Vas a ver tu rendición
  después del control de entrada").
<!-- FR-12/FR-13 changed 2026-11-05 (user, chat): the driver looks at the phone before going to the ATM to see how
much cash to deposit, and right after "finalizar" to see what they paid against it, so they can deposit again. -->
- FR-12: WHEN figures are available, THE app SHALL show for each route: expected total with its cash,
  checks and withholdings; deposited cash; confirmed checks and withholdings; total received; and the
  status group with the time of the last update.
- FR-12a: WHILE there is no deposit, THE app SHALL lead with the cash the driver has to deposit ("Tenés que
  depositar $ X").
- FR-13: WHEN there is a deposit, THE app SHALL lead with the live cash difference: "Te faltan $ X", "Te sobran
  $ X" or "Depositaste justo", with the time the deposits were read. WHEN treasury's difference is final, THE
  app SHALL also show it, marked as surplus, shortage or no difference; until then it says "Pendiente de
  revisión de tesorería" for the official difference.
- FR-13a: IF PIMS could not be read for this answer, THEN THE app SHALL say the deposits shown are the last
  ones read, with their time.
- FR-14: THE app SHALL show the "not loaded yet", "treasury unavailable" and "no route number" states with
  a short Spanish message, and a pull-to-refresh on the screen.
- FR-15: WHILE the app is in the foreground, THE app SHALL check the settlement every 5 minutes, and
  WHEN a deposit or a final difference appears that the driver has not seen yet, THE app SHALL mark the
  "Mi rendición" entry with an in-app indicator until the driver opens the screen.
- FR-16: THE app SHALL format amounts as Argentine pesos ("$ 91.500,00") and times in Argentina time.

## Non-functional requirements
- NFR-1: The settlement request SHALL answer in under 6 seconds even when PAC does not answer (bounded
  by FR-5's timeout).
- NFR-2: Credentials never reach the app, the logs or the responses.

## Edge cases
- A driver with two routes the same day: two entries, each with its own state.
- An entry control made, then cancelled: back to "waiting for the entry control".
- A route reassigned to another driver during the day: it moves to the new driver's screen.
- The expected amount changes after the entry control: the next refresh shows the new value and time.
- A driver with no route assigned: the screen says there are no routes assigned.

## Out of scope
- Push notifications (phase 2: needs Firebase and a new library in the app).
- Past days' settlements (only today).
- Anything the driver could change: the screen is read-only.
- Showing it to controllers, supervisors or admins.

## Open questions
None.
