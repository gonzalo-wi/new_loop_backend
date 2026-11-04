# Spec 005 — Holiday remito, batch printing and quantities in bulks

Status: approved (user, 2026-11-04, chat)

<!-- Keep the "Status:" line and the [NEEDS CLARIFICATION] markers literal: commands search for them. -->

## Context and goal
Every confirmed exit control has a printable remito with the company's data (name, address, CUIT,
IIBB, VAT condition, "Cód. 91 · Comprobante R"). On holidays IVESS must not print remitos with the
company's data: the load still needs a paper that goes with the truck, but a generic one, with no
company data. The control is still sent to Aguas as on any other day; only the printed document
changes.

Today the panel prints remitos one at a time, so on a busy morning an operator clicks print once per
route. They need to select several remitos (or all the ones not yet printed) and print them at once.

The remito lists every product in units. Products that go out in bulks (for example soda, in crates)
show "12" where the driver loads 2 crates, which is confusing. Bulk products must be shown in bulks,
on both the normal and the holiday remito.

## Users / actors
- **Admin**: keeps the holiday calendar in the panel.
- **Panel operators** (admin, supervisor): print the remitos from the panel.
- **Drivers**: open their route's remito in the app; they get whatever version the day calls for.
- **The system**: decides which remito to render and how to show each quantity.

## User stories
- US-1. As an admin, I want to load the holidays in a calendar in the panel, so that on those days
  the remitos come out without the company's data with nobody remembering to switch anything.
- US-2. As an admin, I want to add today as a holiday when an unplanned one comes up, so that the
  remitos printed from then on come out generic.
- US-3. As a panel operator, I want to select several remitos, or all the ones not printed yet, and
  print them in one go.
- US-4. As a driver or operator, I want bulk products shown in bulks, so that the remito matches what
  is loaded on the truck.

## Definitions
- **Holiday**: a calendar date marked as such in the holiday calendar. It applies to every branch.
- **Holiday remito**: the remito of an exit control whose control date is a holiday. It has the same
  layout as the normal remito minus every piece of company data (see FR-6).
- **Bulk product**: a product whose units per bulk (pack quantity) is greater than 1.
- **Remito available**: an exit control already confirmed with Aguas (it has Formulario and Remito
  Nº), as today.

## Functional requirements

### Holiday calendar
- FR-1: THE SYSTEM SHALL let an admin add a date to the holiday calendar, with an optional
  description (for example "Día de la Lealtad"), and remove it.
- FR-2: THE SYSTEM SHALL let an admin add today or a past date as a holiday, not only future dates.
- FR-3: IF an admin adds a date that is already a holiday, THEN THE SYSTEM SHALL refuse it and say
  that the date is already a holiday.
- FR-4: THE SYSTEM SHALL show the panel users the holidays from the current month onwards, ordered by
  date.
- FR-5: THE SYSTEM SHALL refuse creating or removing holidays for users who are not admins (HTTP 403).

### Holiday remito
- FR-6: WHEN a remito is requested for an exit control whose control date is a holiday, THE SYSTEM
  SHALL render it without the company name, address, locality, phone, CUIT, IIBB, VAT condition and
  the "Cód. 91 · Comprobante R" / "Documento no válido como factura" lines. Its title SHALL be
  "REMITO" and it SHALL keep the Aguas Formulario and Remito Nº banner. (Business may revisit both
  later; for now the holiday remito only drops the company data.)
- FR-7: THE SYSTEM SHALL decide between the normal and the holiday remito when the remito is
  rendered, from the control date and the calendar at that moment, so that adding or removing a
  holiday changes every remito rendered afterwards, including reprints.
- FR-8: THE SYSTEM SHALL send exit controls on holidays to Aguas exactly as on any other day.
- FR-9: THE SYSTEM SHALL give the app the same remito the panel gets for that control (normal or
  holiday), with no change needed in the app.

### Quantities in bulks (both remitos)
- FR-10: WHEN a remito lists a bulk product whose quantity is an exact multiple of its pack quantity,
  THE SYSTEM SHALL show the number of bulks alone, with no unit word (for example 12 units with a
  pack of 6 → "2").
- FR-11: WHEN a remito lists a product that is not a bulk product, THE SYSTEM SHALL show its quantity
  in units, as today.
- FR-12: IF a bulk product's quantity is not an exact multiple of its pack quantity, THEN THE SYSTEM
  SHALL show it in units followed by "u" (for example 15 units → "15 u"), so that it is not read as
  bulks. Exit loads are almost always whole bulks, so this is the exception.

### Batch printing in the panel
- FR-13: THE SYSTEM SHALL let a panel operator select, in the stock controls list, the exit controls
  whose remito is available, one by one with a checkbox.
- FR-14: THE SYSTEM SHALL offer a "select all not printed" action that selects every exit control in
  the current list whose remito is available and not yet printed.
- FR-15: THE SYSTEM SHALL NOT let the operator select controls whose remito is not available.
- FR-16: WHEN the operator prints the selection, THE SYSTEM SHALL open a single print dialog with all
  the selected remitos, each one starting on its own page, in the order shown in the list.
- FR-17: WHEN the print dialog for a selection opens, THE SYSTEM SHALL mark every remito in it as
  printed, as single printing does today.
- FR-18: IF the selection includes remitos already printed, THEN THE SYSTEM SHALL ask the operator to
  confirm before printing, saying how many were already printed.
- FR-19: IF any remito of the selection can't be rendered, THEN THE SYSTEM SHALL print none of them
  and tell the operator which routes failed and why.
- FR-20: THE SYSTEM SHALL limit a batch to the remitos of the list on screen (whatever date range
  it shows); a selection of more than 100 remitos SHALL be refused with a message.

## Edge cases
- A holiday is added after a remito was printed: a reprint comes out generic (FR-7). Accepted.
- Exit controls are made the day before for the next delivery day: what counts is the control date
  (the delivery day), not the day it was printed.
- A batch mixes holiday and normal remitos (different control dates in the list): each remito is
  rendered as its own date calls for.
- A product with no pack quantity, or a pack quantity of 1, is shown in units.
- Two operators print overlapping batches at the same time: both prints go out; the second sees
  them as already printed only after refreshing. Accepted, as with single printing today.

## Out of scope
- Holidays per branch.
- A switch that turns the holiday mode on and off without a date (the calendar covers it: FR-2).
- Changes to what is sent to Aguas.
- Batch printing from the app (the app prints only the driver's own route).
- Changing how quantities are loaded or stored: only how the remito shows them.

## Acceptance criteria
- With today marked as a holiday, the remito of an exit control dated today has no company data and
  the one of a control dated tomorrow is the normal one.
- Removing that holiday makes the next render of the same control the normal remito again.
- A remito with 12 soda units (pack of 6) shows "2"; with 15, "15 u"; a product in units shows its
  units.
- In the panel, "select all not printed" plus "print" opens one print dialog with every selected
  remito on its own page, and they all show as printed afterwards.
- A supervisor can't add or remove holidays.
