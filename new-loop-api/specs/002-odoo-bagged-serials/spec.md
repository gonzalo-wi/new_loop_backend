# Spec 002 — Send bagged serials to Odoo

Status: approved

<!-- Approved on 2026-10-30. -->

<!-- Keep the "Status:" line and the [NEEDS CLARIFICATION] markers literal: commands search for them. -->

## Context and goal
When a truck comes back, the controller scans the dispensers it returns (an unload) and marks the
ones that came back still bagged, that is, never installed at a customer. Today the system stores
those bagged serials but sends every returned serial to Odoo as equipment to repair, so Odoo opens
a work order for dispensers that do not need one. Odoo's repair intake now accepts a separate list
of bagged serials. This feature sends the bagged serials in that list, and only the others as
equipment to repair.

## Users / actors
- **Controller** (mobile app): scans the unload and marks the bagged serials. Nothing changes for them.
- **Workshop** (Odoo): receives the repair intake; opens work orders only for the equipment to repair.
- **The system**: forwards the unload to Odoo after Aguas confirms it.

## User stories
- US-1. As the workshop, I want bagged dispensers to reach Odoo apart from the ones to repair, so
  that no work order is opened for a dispenser that was never installed.

## Definitions
- **Bagged serial**: a serial of an unload that the controller marked as still bagged. It is always
  one of the serials scanned in that unload.
- **Serials forwarded**: the scanned serials minus those excluded as unregistered in jMobile (those
  go to Odoo's "equipos no normalizados" intake instead, unchanged by this spec).
- **Repair intake**: the request that informs Odoo of an unload; it carries the date, route,
  technician, user, the equipment to repair and, from now on, the bagged serials.

## Functional requirements
- FR-1: WHEN the system sends an unload to Odoo's repair intake, THE SYSTEM SHALL list as bagged
  every forwarded serial that is a bagged serial of that unload.
- FR-2: WHEN the system sends an unload to Odoo's repair intake, THE SYSTEM SHALL list as
  equipment to repair only the forwarded serials that are not bagged.
- FR-3: IF an unload has no bagged serials, THEN THE SYSTEM SHALL send the bagged list empty and
  the equipment to repair as today.
- FR-4: IF a bagged serial was excluded as unregistered in jMobile, THEN THE SYSTEM SHALL leave it
  out of both lists (it keeps going only to the "equipos no normalizados" intake).
- FR-5: IF every forwarded serial of an unload is bagged, THEN THE SYSTEM SHALL still send the
  repair intake, with the equipment-to-repair list empty and all of them as bagged. If Odoo
  rejects it, the intake is recorded as a failed, retryable integration like any other Odoo error.
- FR-6: WHEN a failed repair intake is retried, THE SYSTEM SHALL send the same split as a first
  attempt would.
- FR-7: THE SYSTEM SHALL match bagged serials against scanned serials ignoring upper/lower case, as
  it already does when it validates them.

## Edge cases
- Movements sent to Odoo before this change are not re-sent.
- A failed intake recorded before this change that is retried after deploying it goes with the new
  split (FR-6).
- Aguas keeps receiving every forwarded serial, bagged or not.
- Loads are unaffected: bagged serials are only allowed on unloads.

## Out of scope
- Any change in the mobile app or the admin panel.
- Showing in the panel which serials went to Odoo as bagged.
- Sending bagged serials to Aguas or to the "equipos no normalizados" intake.

## Interface changes
- Odoo repair intake (outgoing): new field `embolsados`, a list of serials, always present.
  `equipos` no longer includes the bagged serials.
