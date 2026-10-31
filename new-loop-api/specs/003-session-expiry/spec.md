# Spec 003 — Require a valid session and expire it at midnight

Status: approved

<!-- Approved on 2026-10-30. -->

<!-- Keep the "Status:" line and the [NEEDS CLARIFICATION] markers literal: commands search for them. -->

## Context and goal
Today the API answers almost every request without checking that the caller is logged in: the
session token only tells who the caller is, it never blocks anyone. Only stock controls, dispenser
movements, orders and performance check the user, and they answer "forbidden" (with a misleading
role message) instead of "not logged in". Anyone on the server's network can read or change users,
branches, products, routes, audit and integration logs, and fleet data without a token. Because the
API never says "not logged in", the panel and the app never send an expired session back to the
login screen: users keep seeing data with an expired session, and a controller in Núñez got "Only
users with role CONTROLADOR, SUPERVISOR or ADMIN can edit a stock control" when their session had
simply expired. The goal is that every request needs a valid session, that a missing or expired
session is reported as such, that sessions end at midnight so everyone logs in at the start of the
working day, and that the panel and the app go back to the login screen when the session ends.

## Users / actors
- **Panel users** (admin, supervisor): use the web admin panel.
- **App users** (controllers, drivers): use the mobile app.
- **The system**: issues sessions at login and checks them on every request.
- **Monitoring** (Prometheus): already uses its own credentials; unchanged.

## User stories
- US-1. As the business, I want the API to refuse any request without a valid session, so that
  nobody on the network can read or change data without logging in.
- US-2. As a panel or app user, I want to be sent to the login screen when my session ends, instead
  of seeing stale data or a misleading permission error.
- US-3. As a plant manager, I want sessions to end at midnight, so that every user logs in again at
  the start of each working day.

## Definitions
- **Session**: what the system issues at login; every later request carries it.
- **Valid session**: issued by this system, not expired, and belonging to a user that is still active.
- **Midnight**: 00:00 in Argentina time (UTC-3), the users' time zone.
- **Public endpoints**: the ones that must work before logging in: login, the health check, reading
  the latest app version and downloading the app installer.

## Functional requirements
- FR-1: IF a request to a non-public endpoint carries no session, THEN THE SYSTEM SHALL refuse it as
  "not logged in" (HTTP 401) without running it.
- FR-2: IF a request to a non-public endpoint carries an expired, malformed or foreign session, or
  one of a deactivated user, THEN THE SYSTEM SHALL refuse it as "not logged in" (HTTP 401) without
  running it.
- FR-3: WHEN a logged-in user lacks the role an action requires, THE SYSTEM SHALL keep refusing it as
  "forbidden" (HTTP 403), as today.
- FR-4: THE SYSTEM SHALL answer public endpoints with or without a session, and a stale or invalid
  session on them SHALL NOT block them (an old session kept on a phone must not block the login).
- FR-5: WHEN a user logs in, THE SYSTEM SHALL issue a session that expires at the next midnight.
- FR-6: THE SYSTEM SHALL refuse uploading a new app version unless the caller is an admin (HTTP 403
  for other roles).
- FR-7: WHEN the panel receives "not logged in" from the API, THE SYSTEM SHALL end the local session
  and show the login screen.
- FR-8: WHILE the panel is open, IF the session's expiry time has passed, THEN THE SYSTEM SHALL end
  the local session and show the login screen within one minute, even if no request is made, and
  also when the tab becomes visible again.
- FR-9: WHEN the app receives "not logged in" from the API, THE SYSTEM SHALL end the local session
  and show the login screen (as today).
- FR-10: WHILE the app is open, IF the session's expiry time has passed, THEN THE SYSTEM SHALL end
  the local session and show the login screen within one minute, even if no request is made, and
  also when the app returns to the foreground.
- FR-11: WHEN the panel or the app shows the login screen because the session ended, THE SYSTEM
  SHALL tell the user that their session expired.
- FR-12: THE SYSTEM SHALL compute "the next midnight" with an injectable clock, so that it is
  testable without waiting.

## Edge cases
- A user who logs in at 23:50 gets a 10-minute session. Accepted.
- Sessions issued before the deploy keep their old expiry (24 h from login); after that, every new
  login follows FR-5.
- Prometheus keeps using its own credentials on the monitoring endpoints; unchanged.
- The app checks for updates before login: reading the latest version and downloading the installer
  stay public (FR-4).
- An app operation in progress when the session ends (a scan, a form) is lost when the login screen
  appears. Accepted for now.
- A phone whose clock is wrong may end the session early or late on its own (FR-10); the API still
  enforces the real expiry (FR-2).

## Out of scope
- Per-endpoint role rules beyond FR-6 (for example, who may manage users). Today any logged-in user
  can call them; a separate spec should define the roles per action.
- Refresh tokens or "remember me".
- Rotating the session signing secret.

## Interface changes
- Every non-public endpoint answers 401 without a valid session (today it answers as if logged out,
  or 403 in the four modules above).
- `POST /app/version` requires an admin.
- The login response does not change. The session expiry is read from the session itself.
