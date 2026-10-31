# Tasks 003 — Require a valid session and expire it at midnight

Status: approved, implemented

<!-- Approved on 2026-10-30. -->

## Backend
- [x] **T1. Compute the next midnight in Argentina.** FR-5, FR-12
  - Done when: `SessionExpiryTest` (morning, 23:59, 00:00) fails first and then passes.
- [x] **T2. Issue tokens that expire at the next midnight.** FR-2, FR-5
  - Done when: `JwtServiceTest` with a fixed clock passes; `app.jwt.expiration` is gone.
- [x] **T3. Require authentication and answer 401/403 as JSON.** FR-1, FR-2, FR-3, FR-4
  - Done when: `SecurityConfigTest` covers no token, expired and garbage tokens, valid token and the
    public endpoints, and passes.
- [x] **T4. Make APK upload admin-only.** FR-6
  - Done when: `SecurityConfigTest` has CONTROLADOR → 403 and ADMIN → allowed; `./mvnw test` green
    (except the known Postgres failures); `docs/AUTH.md` updated.

## Panel
- [x] **T5. Read the token expiry and expire the session.** FR-7, FR-8, FR-11
  - Done when: `sessionExpiry.ts` cases pass in the scratch check; `expireSession()`, the 401
    interceptor (login excluded) and `useSessionExpiry` in `AdminLayout` are in; `tsc` and `build` clean.
- [x] **T6. Show the expired-session message on the login page.** FR-11
  - Done when: in the browser, an expired token sends you to the login with the message, without a
    request, and a wrong password still shows its own error.

## App
- [x] **T7. Read the token expiry.** FR-10
  - Done when: jest tests for `tokenExpiresAt` / `isExpired` (valid, expired, garbage token) pass.
- [x] **T8. Expire the session on 401 and on time.** FR-9, FR-10
  - Done when: jest tests for `expireSession`, the 401 interceptor and `useSessionExpiry` (interval
    and foreground) pass.
- [x] **T9. Show the expired-session message on the login screen.** FR-11
  - Done when: a jest test renders the message when `sessionExpired` is set; `npx jest` and
    `npx tsc --noEmit` green.
  - Note: the app's jest runs in a node environment without a renderer, so the store flag that drives
    the notice is tested instead (`store/__tests__/authStore.test.ts`); the notice itself is checked by hand.
