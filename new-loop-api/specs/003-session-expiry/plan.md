# Plan 003 — Require a valid session and expire it at midnight

Status: approved

<!-- Approved on 2026-10-30. -->

Spec: [spec.md](./spec.md)

Three repos, each in its existing structure. Backend first (it is the part that changes behavior),
then panel and app, which only react to it.

## Backend (`new-loop-api`)

| File | Change | FR |
|---|---|---|
| `common/security/SessionExpiry.java` (new) | Pure: `nextMidnight(Instant now)` in `America/Argentina/Buenos_Aires` | FR-5, FR-12 |
| `common/security/JwtService.java` | Inject `Clock`; `issuedAt` = now, `expiration` = `SessionExpiry.nextMidnight(now)`; validation uses the clock too | FR-2, FR-5 |
| `application.properties` | Remove `app.jwt.expiration` (no longer used) | FR-5 |
| `common/config/SecurityConfig.java` | App chain: public matchers (`POST /auth/login`, `GET /health`, `GET /app/version`, `GET /app/download/**`, error page), `POST /app/version` → `hasRole("ADMIN")`, rest → `authenticated()`; custom entry point (401) and access-denied handler (403) | FR-1, FR-3, FR-4, FR-6 |
| `common/security/JsonAuthErrorHandler.java` (new) | Writes the same `ApiError` JSON the rest of the API returns, for 401 and 403 raised by the filter chain | FR-1, FR-2, FR-6 |
| `common/security/JwtAuthenticationFilter.java` | Unchanged logic (an invalid token leaves the request anonymous, so public endpoints still work and the rest get 401); only the log message is reviewed | FR-2, FR-4 |
| `appupdate/controller/AppVersionController.java` | Comment: upload is now admin-only | FR-6 |
| `docs/AUTH.md` | 401 vs 403, midnight expiry, public endpoints | — |

The existing service-level role checks (`ControlEditNotAllowedException`, etc.) stay: with the chain
requiring authentication they only fire for a logged-in user with the wrong role, which is FR-3.

### Algorithm
```
nextMidnight(now):
  today = now.atZone(ARGENTINA).toLocalDate()
  return today.plusDays(1).atStartOfDay(ARGENTINA).toInstant()
```

## Panel (`new-loop-front`)

| File | Change | FR |
|---|---|---|
| `src/features/auth/lib/sessionExpiry.ts` (new) | Pure: `tokenExpiresAt(token)` (reads `exp` from the token payload, null if unreadable) and `isExpired(token, now)` | FR-8 |
| `src/features/auth/hooks/useSessionExpiry.ts` (new) | While mounted: checks every 30 s and on `visibilitychange`; when expired → `expireSession()` | FR-8 |
| `src/features/auth/store/auth.store.ts` | New `expireSession()`: `logout()` + remember the reason for the login page | FR-7, FR-8, FR-11 |
| `src/shared/lib/api.ts` | On 401 (except the login request) call `expireSession()` instead of only removing `loop_token` (today the persisted store still says "authenticated", so the guard bounces back) | FR-7 |
| `src/layouts/AdminLayout.tsx` | Mounts `useSessionExpiry()` | FR-8 |
| `src/features/auth/pages/LoginPage.tsx` | Shows "Tu sesión venció. Volvé a ingresar." when the reason is set; clears it on the next login | FR-11 |

## App (`react-nati`)

| File | Change | FR |
|---|---|---|
| `lib/sessionExpiry.ts` (new) | Same pure helpers as the panel (`tokenExpiresAt`, `isExpired`) | FR-10 |
| `hooks/useSessionExpiry.ts` (new) | While logged in: checks every 30 s and when `AppState` becomes `active`; when expired → `expireSession()` | FR-10 |
| `store/authStore.ts` | New `sessionExpired` flag and `expireSession()` (`logout()` + flag); `setUser` clears it | FR-9, FR-10, FR-11 |
| `lib/api.ts` | On 401 call `expireSession()` instead of `logout()` | FR-9, FR-11 |
| `app/_layout.tsx` | Mounts `useSessionExpiry()`; the existing guard already sends a logged-out user to `/login` | FR-10 |
| `app/login.tsx` | Shows "Tu sesión venció. Volvé a ingresar." when `sessionExpired` | FR-11 |

All UI text in Spanish; colors and spacing from each project's theme tokens.

## Decisions
- **D1. Explicit Argentina zone in `SessionExpiry`.** The app sets the JVM default zone, but relying on
  it would break silently if that line moves. Discarded: `Clock.systemDefaultZone()`'s zone.
- **D2. Expiry read from the token (`exp`), no login response change.** It also works for tokens
  already stored on phones and browsers. Discarded: adding `expiresAt` to the login response, an API
  change that old sessions would not have.
- **D3. Enforcement in the security chain, not per controller.** One rule covers every current and
  future endpoint (default deny). Discarded: `@PreAuthorize` on each controller, easy to forget.
- **D4. Periodic check every 30 s plus on focus.** Meets "within one minute" (FR-8, FR-10) with a
  trivial cost. Discarded: one timer set exactly at `exp`, which browsers and phones throttle or drop
  while in the background.
- **D5. Login-page reason kept in the store** (app) / `sessionStorage` (panel), not in the URL, so a
  reload of the login page doesn't keep showing it.

## Test strategy
- Backend (`./mvnw test`):
  - `SessionExpiryTest` (pure): morning, 23:59, exactly 00:00, with a fixed clock.
  - `JwtServiceTest`: a token issued with a fixed clock expires at the next midnight; past that clock
    it is rejected.
  - `SecurityConfigTest` (`@WebMvcTest` slice with the real chain and a mocked user cache): no token →
    401 JSON; expired/garbage token → 401; valid token → 200; public endpoints with and without a
    garbage token → not 401; `POST /app/version` as CONTROLADOR → 403, as ADMIN → passes security.
- Panel (`npx tsc --noEmit`, `npm run build`; no unit runner): `sessionExpiry.ts` checked with the
  same esbuild scratch script as Phase E; the redirect and the message checked in the browser
  against a local backend.
- App (`npx jest`, `npx tsc --noEmit`): unit tests for `sessionExpiry.ts`, the store's
  `expireSession`, the 401 interceptor and `useSessionExpiry` (fake timers, mocked `AppState`).

## Deploy order
Backend, panel and a new APK on the same day. Older APKs already log out on 401, so they keep working.
