# Spec 001 — Operational metrics per user

Status: approved

<!-- Keep the "Status:" line and the [NEEDS CLARIFICATION] markers literal: commands search for them. -->

## Context and goal
Plant managers want to know how warehouse operators (controllers) perform: how many stock
controls and dispenser loads they do, how long a load takes and how much time passes between
trucks, how often drivers reject their entry controls and how often a supervisor has to correct
them. Today the stock-control part of that only exists in the audit log, as free-form JSON
snapshots that are hard to query and are deleted after one year, and the current state of a
control overwrites its history (a control rejected twice and then accepted only shows
"accepted"). Dispenser movements are kept, but nothing records how long a load took. This
feature records each relevant operational fact durably and in a structured way, never deletes
it, and shows the resulting metrics in the admin panel.

## Users / actors
- **Plant manager** (panel, admin or supervisor role): reads the metrics.
- **Controller** (mobile app): performs stock controls and dispenser loads/unloads; the metrics
  are about them.
- **Driver** (mobile app): approves or rejects entry controls of their route.
- **Supervisor / admin**: corrects entry controls already sent to Aguas.
- **The system**: records the events and imports the history that is still available.

## User stories
- US-1. As a plant manager, I want to see how many controls and dispenser movements each
  controller did per day so that I can compare workload.
- US-2. As a plant manager, I want to see how often drivers reject each controller's entry
  controls, and how many rounds it takes until they are accepted, so that I can detect who
  needs training.
- US-3. As a plant manager, I want to see how often a controller's controls are corrected after
  being sent to Aguas so that I can measure their accuracy.
- US-4. As a plant manager, I want to see how long each dispenser load takes and how much time
  passes between one truck and the next, per controller, so that I can measure loading pace.
- US-5. As a plant manager, I want to see which routes came back without an entry control.
- US-6. As a plant manager, I want these metrics to cover the history still available and every
  day from now on, without losing them over time, so that I can compare periods.

## Definitions
- **Operational event**: an immutable record of one business fact (a control submitted, a
  control rejected, a dispenser load registered…), with who did it, who it is attributed to,
  when, and its figures.
- **Actor**: the user who performed the action (e.g. the driver who rejected a control, or the
  supervisor who corrected it).
- **Responsible user**: the user the fact is attributed to for metrics. For stock-control events
  it is the control's controller; for dispenser events it is the user who registered the
  movement. It may be unknown (old records); those events are grouped under "Sin usuario".
- **Event date**: the calendar date, in Argentina time (UTC-3), on which the fact happened. All
  per-user metrics and the daily trend group by event date.
- **Control date**: the date a stock control is for (an exit's control date is the next delivery
  day; an entry's is the day it was made). Only coverage (FR-22) uses it.
- **Rejection round**: one driver rejection of a given entry control. A control accepted on the
  first try has 0 rounds.
- **First-pass acceptance**: an entry control the driver accepted with 0 rejection rounds.
- **Time to driver response**: time from the last time the control was sent to the driver
  (created, or resubmitted after a rejection) until the driver approved or rejected it.
- **Units with difference**: sum of the absolute differences of a control's items, as last
  submitted by the controller (before any supervisor correction). [NEEDS CLARIFICATION] how an
  item's difference is calculated: the item's `differenceQuantity` is never filled in today. Blocks
  Phase B (FR-21 difference rate, FR-26); Phase A records each item's raw quantities so any rule can
  be applied later.
- **Difference rate**: units with difference divided by total units, both summed over all the
  controls in the scope (a ratio of sums, not an average of ratios).
- **Load duration**: time from the moment the first serial is added to an empty dispenser
  load/unload form until that movement is successfully submitted, measured on the device.
- **Slow load**: a load duration longer than 30 minutes. A normal load takes 10 to 15 minutes,
  because trucks queue up.
- **Gap between trucks**: time between the registration of one dispenser movement (load or
  unload, not cancelled) and the registration of the next one by the same responsible user on
  the same event date.
- **Pause**: a gap between trucks of 60 minutes or more.
- **Route of a dispenser movement**: operators type only the route number ("179") while routes are
  named like "Rto 179", so a movement belongs to the route whose code has the same number once
  letters are ignored. If no route, or more than one, has that number, the movement has no branch.
- **Expected entry**: every non-cancelled exit control of a route with control date D means that
  route's truck must come back on D, so an entry control for the same route and control date D
  is expected. Every truck that goes out comes back.
- **Missing entry**: an expected entry with no non-cancelled entry control for that route and
  control date, once D has ended.

## Functional requirements (EARS)

### Recording stock-control events
- FR-1: WHEN a stock control is created, THE SYSTEM SHALL record a "control submitted" event with
  the control type, branch, route, responsible user, number of items, total units and units with difference.
- FR-2: WHEN a stock control is edited before being sent to Aguas, THE SYSTEM SHALL record a
  "control edited" event with the actor and the new figures; IF the control was rejected by the
  driver, THEN the event SHALL be marked as a resubmission after rejection.
- FR-3: WHEN an entry control is approved, THE SYSTEM SHALL record a "control approved" event with
  the approver as actor, the controller as responsible user, the number of previous rejection
  rounds and the time to driver response.
- FR-4: WHEN an entry control is rejected, THE SYSTEM SHALL record a "control rejected" event with
  the rejecter as actor, the controller as responsible user, the round number, the time to driver
  response and the reason.
- FR-5: WHEN a supervisor or admin corrects an entry control already sent to Aguas, THE SYSTEM SHALL
  record a "control corrected" event with the corrector as actor, the controller as responsible user,
  the reason, and the units with difference before and after this correction.
- FR-6: IF a stock control is in cancelled status when metrics are computed, THEN THE SYSTEM SHALL NOT
  count it as a submitted control nor as an expected entry. (No operation cancels controls today; the
  status can only be set directly in the database.)

### Recording dispenser events
- FR-7: WHEN a dispenser load or unload is registered, THE SYSTEM SHALL keep, for that movement, the
  branch of its route at that moment, the number of serials, the serials excluded (unregistered in
  jMobile and unavailable in Odoo, separately) and the load duration when known.
- FR-8: WHEN a dispenser movement is edited, THE SYSTEM SHALL count it as one movement with its new
  figures, registered at the time of the original registration, and SHALL NOT count the edit as a
  cancellation.
- FR-9: WHEN a dispenser movement is cancelled (not as part of an edit), THE SYSTEM SHALL count it as
  a cancellation of its responsible user and exclude it from movement counts, serials and gaps.

### Recording integration errors
- FR-10: WHEN sending a stock control or a dispenser movement to Aguas or Odoo fails, THE SYSTEM SHALL
  record an "integration error" event with the integration name and the responsible user of that
  control or movement. Metrics SHALL count controls/movements with at least one error, not attempts.

### Consistency and durability
- FR-11: WHEN a user operation (FR-1 to FR-9) is persisted, THE SYSTEM SHALL record its event or
  figures atomically with it, so that they exist if and only if the operation was persisted.
- FR-12: WHEN an integration error is recorded (FR-10), THE SYSTEM SHALL record its event atomically
  with the error itself.
- FR-13: THE SYSTEM SHALL keep a snapshot of the actor's and responsible user's username and of the
  route code in each event, so that later renames do not change the history.
- FR-14: THE SYSTEM SHALL never update or delete recorded events, and SHALL NOT apply any automatic
  retention to them or to dispenser movements.

### Load duration from the app
- FR-15: WHEN the controller adds the first serial to an empty load/unload form, THE APP SHALL start
  measuring; WHEN the movement is submitted successfully, THE APP SHALL send the elapsed time with it.
  A failed submission that is retried keeps the original start; clearing the form, registering a
  movement or leaving the screen resets it.
- FR-16: IF the elapsed time is missing (older app versions, app restarted mid-load), negative, or 60
  minutes or more, THEN THE SYSTEM SHALL accept the movement and record it without a load duration.

### History import
- FR-17: THE SYSTEM SHALL provide a one-time import that builds stock-control events (FR-1 to FR-5)
  from the audit log entries still present, limited to entries older than the moment live recording
  started, and marks them as imported.
- FR-18: IF the import is run more than once, THEN THE SYSTEM SHALL NOT create duplicate events.
- FR-19: IF an audit entry cannot be interpreted, THEN THE SYSTEM SHALL skip it, keep importing the rest
  and report how many entries were skipped and why.
- FR-20: THE SYSTEM SHALL derive imported event dates in Argentina time, as for live events.

### Metrics query
- FR-21: WHEN an admin or supervisor requests metrics for a range of event dates, THE SYSTEM SHALL
  return, per responsible user: controls submitted (by type), edits, resubmissions after rejection,
  rejection rounds, first-pass acceptance rate, average time to driver response, corrections, difference
  rate, dispenser movements (by type), serials moved, serials excluded, average and median load duration,
  number of slow loads, average and median gap between trucks excluding pauses, number of pauses,
  cancellations and controls/movements with integration errors.
- FR-22: WHEN metrics are requested for a range, THE SYSTEM SHALL return, per branch and control date in
  the range, the number of expected entries, missing entries and the routes missing, counting only
  control dates that have already ended. Entries are matched by route and control date, regardless of
  the branch they were recorded in.
- FR-23: THE SYSTEM SHALL allow filtering by branch and by responsible user; a control-type filter applies
  to stock-control metrics and a movement-type filter applies to dispenser metrics.
- FR-24: THE SYSTEM SHALL return the FR-21 metrics per event date for one responsible user, to show a trend.
- FR-25: THE SYSTEM SHALL return the most frequent rejection and correction reasons in the range, with
  their count, per responsible user and per actor.
- FR-26: THE SYSTEM SHALL return the products with the most units with difference in the range, within
  the same branch and type filters.
- FR-27: IF an average, median or rate has no data in the range, THEN THE SYSTEM SHALL return it as empty;
  counts with no activity SHALL be zero.

### Access
- FR-28: IF a user without the admin or supervisor role requests metrics, THEN THE SYSTEM SHALL deny access.
- FR-29: WHILE the requester is an admin, THE SYSTEM SHALL return metrics for every branch, or for the
  branch they filter by.
- FR-30: WHILE the requester is a supervisor, THE SYSTEM SHALL return metrics only for their own branch,
  ignoring any other branch filter.
- FR-31: IF the requester is a supervisor without a branch assigned, THEN THE SYSTEM SHALL deny access
  with a message saying their user has no branch.
- FR-32: Dispenser movements whose route had no known branch SHALL only be visible to admins.

### Panel
- FR-33: THE PANEL SHALL show a "Desempeño" section with one row per responsible user who has at least
  one event in the range, the FR-21 metrics, and a range picker defaulting to the current week (Monday
  to Sunday, Argentina time); every column is sortable.
- FR-34: WHEN the manager selects a user, THE PANEL SHALL show their daily trend (FR-24) and their top
  rejection and correction reasons (FR-25).
- FR-35: THE PANEL SHALL show the missing entries per control date (FR-22) with the routes involved,
  and the products with most differences (FR-26).
- FR-36: THE PANEL SHALL show explicit loading, error and empty states, with every text in Spanish.

## Non-functional requirements
- Volume: a few hundred events per day (~150k per year). The metrics query for one month and one branch
  answers in under 2 seconds on the homologation server.
- Dates: event dates in Argentina time; neither the panel nor the app derives calendar dates through
  UTC conversion.
- Recording an event adds no external call to the operation.
- Backwards compatible: older app versions keep working (FR-16); existing endpoints keep their contract.

## Edge cases
- An entry control rejected, edited and resubmitted several times: one rejected event per round, one
  edited event (marked as resubmission) per resubmission, one approved event at the end.
- A control edited while pending driver approval (not rejected): an edited event, not a resubmission.
- A control created by one user and edited by another: the responsible user stays the control's controller.
- An entry approved or rejected by a supervisor instead of the driver: the supervisor is the actor.
- Several corrections of the same control: each correction compares with the figures right before it.
- Exit made on Saturday for Monday (Sunday skipped): it counts on Saturday for workload and expects an
  entry on Monday for coverage.
- Today's expected entries are not missing until the day ends (trucks may still be on the road).
- A route with two non-cancelled exits for the same control date expects a single entry.
- An entry cancelled after its day ended: the route becomes missing again.
- A gap crossing midnight is not paired (different event dates).
- Integration retries that fail several times: one control/movement with errors, however many attempts.
- A user is deactivated or renamed: their events keep the snapshot and still appear in past ranges.
  Users are deactivated, never hard-deleted.
- A dispenser movement typed as "179" belongs to route "Rto 179"; a code with no digits, or a number
  shared by two routes, leaves the movement without branch (only admins see it, FR-32).
- Imported history has no load durations and no integration errors (those were never recorded).

## Out of scope
- Categorized rejection reasons chosen from a list (second stage; it changes the driver screen).
- Metrics visible to controllers in the mobile app.
- Excel/PDF export, alerts or targets per controller.
- Duration of stock controls (only dispenser loads are timed in this iteration).
- Changing the audit log, its retention, or the integration log retention.
- Importing integration-error history (integration logs only keep 90 days and are not imported).
- Requiring a branch when creating supervisors (existing supervisors without one get FR-31's message).

## Approved interface changes
Approved by the user in conversation on 2026-10-28: a new persisted store for events with no retention,
an optional load-duration field on the dispenser movement request (older apps omit it), a new metrics
endpoint, and supervisor branch scoping for this endpoint only.

## Completion criteria
- Every FR with a green test: backend `./mvnw test` (with `LOOP_TEST_DB_URL` set, so the Postgres
  queries run), app `npx jest` and `npx tsc --noEmit`, front `npx tsc --noEmit`.
- History import run against a copy of the homologation database, with the skipped count reported.
- The one-month, one-branch metrics query timed on homologation under 2 seconds.
- Manual demo in the panel: a week of metrics for one branch, one controller's trend, missing entries.

## Open questions
- [NEEDS CLARIFICATION] Definition of an item's difference (see "Units with difference"). Blocks Phase B only.

Answered on 2026-10-28:
- Admins see every branch; supervisors only their own (FR-29–31).
- Gaps of 60 minutes or more are pauses.
- Coverage is included: exits for a day are the entries expected back that day (FR-22).
- Loads take 10–15 minutes; over 30 is slow, 60 or more is discarded as implausible (FR-16).
