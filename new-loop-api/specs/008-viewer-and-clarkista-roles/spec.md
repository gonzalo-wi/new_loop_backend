# Spec 008 — Viewer and forklift operator (clarkista) roles

Status: approved (user, 2026-11-16, chat)

<!-- Keep the "Status:" line and the [NEEDS CLARIFICATION] markers literal: commands search for them. -->

## Context and goal
Two new kinds of users need LOOP, each only to look at data, never to change it.

1. **Viewer** (role VISUALIZADOR): people at each plant (Ciudadela, La Plata, NAF) who need to follow
   the entry and exit stock controls of their own plant from the admin panel, without being able to
   create, edit, correct, approve or print anything.
2. **Forklift operator** (role CLARKISTA): loads the trucks for the next delivery day. On a tablet,
   with the mobile app, they type a route number and see how much of each product that truck usually
   takes, so they can prepare the load. The figure is a forecast built from the route's own past exit
   controls (what was actually loaded), not from orders.

## Users / actors
- **Viewer** (admin panel, role VISUALIZADOR): reads entry and exit controls of their branch.
- **Forklift operator** (mobile app on a tablet, role CLARKISTA): reads the load forecast of a route
  of their branch.
- **Admin**: creates these users and assigns their branch from the panel.

## Definitions
- **User's branch**: the branch assigned to the user. Both new roles must have one.
- **Route number**: the digits of the route code ("Rto 179" → 179), as LOOP already computes elsewhere.
- **Load day**: the next delivery day after today (Argentina time): tomorrow, or Monday when tomorrow
  is Sunday. It is the same date an exit control created today gets.
- **Reference days**: the 4 dates 7, 14, 21 and 28 days before the load day (same weekday).
- **Reference control**: a non-cancelled EXIT control of the route whose control date is a reference day.
  There is at most one per route and date.
- **Forecast quantity** of a product: the average of that product's quantity across the reference
  controls found (a product missing from a control counts as 0), rounded half up to a whole number.

## Functional requirements (EARS)

### Accounts
- FR-1: THE SYSTEM SHALL support the roles VISUALIZADOR and CLARKISTA in addition to the existing ones.
- FR-2: IF an admin creates or updates a VISUALIZADOR or CLARKISTA user without a branch, THEN THE
  SYSTEM SHALL reject it with a validation error.

### Viewer — backend
- FR-3: WHEN a viewer lists stock controls, THE SYSTEM SHALL return only controls of the viewer's
  branch, of both types (ENTRY and EXIT), with every other filter (type, dates, status, route) working
  as for other roles; a requested branch filter SHALL be ignored.
- FR-4: WHEN a viewer requests one stock control by id, THE SYSTEM SHALL return it only if it belongs to
  the viewer's branch; otherwise THE SYSTEM SHALL answer "not found".
- FR-5: IF a viewer calls any operation that changes data (create, edit, approve, reject, correct,
  mark a remito as printed, or any other non-read operation), THEN THE SYSTEM SHALL deny it (403)
  without changing anything.
- FR-6: IF a viewer requests a remito PDF (single or batch), THEN THE SYSTEM SHALL deny it (403).
- FR-7: IF a viewer requests any read outside what the stock control pages of the panel need
  (controls, and the branch / route / product lists used by their filters and detail), THEN THE SYSTEM
  SHALL deny it (403).

### Forklift operator — backend
- FR-8: WHEN a forklift operator requests the forecast for a route number, THE SYSTEM SHALL resolve it to
  the active route of the operator's branch with that route number, and return: the route code, the load
  day, the reference days that had a reference control, and the list of products with forecast quantity
  greater than 0 (product code, name and forecast quantity), ordered as products are ordered elsewhere
  in the app.
- FR-9: IF no route of the operator's branch has that route number, THEN THE SYSTEM SHALL answer
  "route not found" (404).
- FR-10: IF the route has no reference control, THEN THE SYSTEM SHALL return the route and load day with
  an empty product list (no data), not an error.
- FR-11: THE SYSTEM SHALL compute the load day from the server clock in Argentina time, never from the
  device.
- FR-12: IF a forklift operator calls anything other than the forecast and what the app needs to start
  (login, app version check), THEN THE SYSTEM SHALL deny it (403).
- FR-13: IF a user with any role other than CLARKISTA or ADMIN requests the forecast, THEN THE SYSTEM
  SHALL deny it (403). An admin SHALL get the forecast of any route by number across branches only when
  the number matches a single active route; otherwise "route not found".

### Viewer — panel
- FR-14: THE panel SHALL let an admin create and edit users with role "Visualizador" and "Clarkista",
  requiring a branch for both.
- FR-15: WHEN a viewer logs into the panel, THE panel SHALL show only the stock entries and stock exits
  pages, landing on stock exits.
- FR-16: WHILE the user is a viewer, THE panel SHALL hide every action that changes data or prints on
  those pages: new control, edit, correct, remito download/print, batch selection.
- FR-17: IF a forklift operator tries to log into the panel, THEN THE panel SHALL refuse with a Spanish
  message saying the user is for the mobile app.

### Forklift operator — app
- FR-18: WHEN a forklift operator logs into the app, THE app SHALL show only the load forecast screen
  (no other tabs or entries) plus logout.
- FR-19: THE forecast screen SHALL let the operator type a route number with a large numeric input sized
  for a tablet and request its forecast.
- FR-20: WHEN the forecast arrives with products, THE app SHALL show the route code, the load day
  ("Carga para el martes 17/11"), how many reference days it is based on, and each product with its
  quantity in large type.
- FR-21: WHEN the forecast arrives without products, THE app SHALL say there is no data for that route
  ("No hay datos de cargas anteriores para este reparto").
- FR-22: THE app SHALL show loading, "route not found" and connection error states with short Spanish
  messages and a way to retry.

## Non-functional requirements
- NFR-1: The forecast request SHALL answer in under 1 second for a route with 4 reference controls.
- NFR-2: Neither role SHALL be able to change any data through the API, regardless of what the UI shows.

## Out of scope
- Changing the driver's "Sugerir pedido" (it keeps copying last week's order).
- Skipping holidays when picking the load day or the reference days.
- Choosing a load day other than the next delivery day.
- Viewers seeing anything outside stock controls (orders, dispensers, statistics...).
