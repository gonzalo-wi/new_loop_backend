# Spec 006 — Route statistics

Status: approved (user, 2026-11-04, chat)

<!-- Keep the "Status:" line and the [NEEDS CLARIFICATION] markers literal: commands search for them. -->

## Context and goal
The plant manager already uses the performance page (spec 001), which is about controllers. They now
need figures about the routes themselves: how long a truck stays inside the plant between its entry
control and its exit control, at what hours the trucks come back, how many routes come back each day
per branch, which routes leave their exit control for the morning of the delivery day instead of the
day before, and which routes reject their entry controls. All of it can be computed from the stock
controls and operational events LOOP already stores, so the whole available history is covered. This
spec adds a "Repartos" statistics module to the panel.

## Users / actors
- **Plant manager** (panel, admin or supervisor role): reads the statistics.
- **Controller** (mobile app): makes the entry and exit controls the statistics are built from.
- **Driver** (mobile app): accepts or rejects the entry control of their route.

## User stories
- US-1. As a plant manager, I want the average time a truck spends inside the plant, from its entry
  control to its exit control, so that I can measure the internal process.
- US-2. As a plant manager, I want to see how many routes come back in each hour of the day, for a
  range or for one day, so that I can plan staff for the peak hours.
- US-3. As a plant manager, I want the number of routes that came back each day per branch.
- US-4. As a plant manager, I want to know, per day, how many and which routes made their exit control
  on the morning of the delivery day instead of the day before.
- US-5. As a plant manager, I want to know which routes reject their entry controls, and how often, so
  that I can follow them up.

## Definitions
- **Argentina time**: every date and hour below is in `America/Argentina/Buenos_Aires` (UTC-3).
- **Arrival**: a non-cancelled ENTRY stock control. Same definition as spec 004: there is at most one per
  route and control date, and every status other than cancelled counts (the truck came back either way).
- **Arrival day / arrival time**: the arrival's control date, and the moment it was created.
- **Exit**: a non-cancelled EXIT stock control.
- **Exit made the day before**: an exit created on a calendar day earlier than its control date.
- **Morning exit**: an exit created on its own control date or later (it was not done the day before).
  The name follows the plant's usage; the hour it was made does not matter.
- **Stay**: for a route and an arrival day D, the time from the arrival time to the creation of that
  route's exit created on D for a later control date. Exits made on another day (morning exits) have no
  stay: they are not part of the average.
- **Inconsistent stay**: a stay whose exit was created before the arrival. It is left out of the
  average and counted apart.
- **Rejection**: one driver rejection of an entry control (operational event CONTROL_REJECTED of spec
  001). A control rejected twice counts two rejections. A rejection counts on its event date.
- **Rejection rate of a route**: entry controls of the route rejected at least once, divided by the
  route's arrivals, in the same range.
- **Range**: the dates a request covers. Missing ends default to the current week (Monday to Sunday);
  at most 92 days, like the performance page. Arrivals, stays, exits and daily totals group by control
  date (arrival day for stays); rejections by event date.

## Functional requirements (EARS)

### Stay inside the plant
- FR-1: WHEN statistics are requested for a range, THE SYSTEM SHALL return the average and the median
  stay of the arrivals in the range, and how many stays they were computed from.
- FR-2: THE SYSTEM SHALL return the average stay and the stay count per branch, and per route ordered
  from the longest average stay to the shortest.
- FR-3: THE SYSTEM SHALL return how many arrivals in the range had no stay (no exit created that day)
  and how many had an inconsistent stay, so the average can be read with its coverage.

### Arrivals per hour
- FR-4: WHEN statistics are requested for a range, THE SYSTEM SHALL return the number of arrivals per
  hour of the day (0 to 23, by arrival time), always the 24 hours, with zero where there were none.
- FR-5: WHEN the range is a single day, THE SYSTEM SHALL return the arrivals of each hour of that day,
  and the panel SHALL let the manager see which routes came back in a chosen hour.

### Routes per day
- FR-6: THE SYSTEM SHALL return, per control date in the range and per branch, the number of arrivals
  (routes that came back), with zero for days without arrivals.

### Morning exits
- FR-7: THE SYSTEM SHALL return, per control date in the range, the number of morning exits and the
  routes they belong to (route code and branch), together with the number of exits made the day before.
- FR-8: THE SYSTEM SHALL return, per route, how many morning exits it had in the range, ordered from
  most to fewest, leaving out routes with none.

### Rejections by route
- FR-9: THE SYSTEM SHALL return, per route with at least one rejection in the range: its rejections,
  the entry controls rejected at least once, its arrivals and its rejection rate, ordered from most to
  fewest rejections.
- FR-10: THE SYSTEM SHALL return the most frequent rejection reasons of each route in that list, at
  most three per route.
- FR-11: THE SYSTEM SHALL use the route code stored with each rejection event, so a renamed route
  keeps its history under the code it had then.

### Scope and access
- FR-12: IF a user without the admin or supervisor role requests route statistics, THEN THE SYSTEM
  SHALL deny access.
- FR-13: WHILE the requester is an admin, THE SYSTEM SHALL return statistics for every branch, or for
  the branch they pick.
- FR-14: WHILE the requester is a supervisor, THE SYSTEM SHALL return statistics only for their own
  branch, whatever branch they ask for; IF the supervisor has no branch, THEN THE SYSTEM SHALL deny
  access.
- FR-15: IF the range ends before it starts or is longer than 92 days, THEN THE SYSTEM SHALL reject the
  request with a clear message.
- FR-16: IF an average, median or rate has no data in the range, THEN THE SYSTEM SHALL return it as
  empty, never as zero.

### Panel
- FR-17: THE SYSTEM SHALL show a "Repartos" statistics page in the panel, visible to admins and
  supervisors, with a range picker (default: current week) and, for admins, a branch filter.
- FR-18: THE page SHALL show: the average stay with its coverage (FR-1, FR-3) and the routes with the
  longest stays; a chart of arrivals per hour; a chart or table of routes per day and branch; the
  morning exits per day with their routes; and the rejections by route with their reasons.
- FR-19: THE page SHALL show loading, error and empty states for each block, and durations in hours
  and minutes ("2 h 15 min").

## Non-functional requirements
- NFR-1: A 92-day range for all branches SHALL answer in under 3 seconds on the production data.
- NFR-2: The statistics SHALL be read-only: no new stored data and no change to how controls are
  recorded.
- NFR-3: Hours and dates SHALL be computed in Argentina time from the injected clock and an explicit
  zone, never the JVM or database default.

## Edge cases
- A route that came back but whose exit for the next day was cancelled and redone: the stay uses the
  non-cancelled exit.
- An arrival on Saturday whose exit is for Monday (Sunday is skipped): the stay still counts if the
  exit was created on Saturday.
- An arrival created today: it counts in arrivals and per hour; its stay counts once the exit exists.
- A rejected control later accepted: its rejections still count; it still is one arrival.
- Routes without a branch match are not possible (controls always have a branch).

## Out of scope
- Real gate times from GPS (Powerfleet); the arrival time is the entry control's time.
- Exporting to Excel or PDF.
- Alerts or notifications.
- Changes to the mobile app.

## Open questions
None.
