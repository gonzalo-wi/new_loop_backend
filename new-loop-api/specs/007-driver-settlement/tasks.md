# Tasks 007 — Driver settlement in the app

Status: approved (user, 2026-11-05, chat: approved in advance)

## Backend (`new-loop-api`)
- [x] **T1. Pure state resolution.** FR-2, FR-4, FR-5, FR-6
  - Done when: `SettlementStatesTest` covers every state; green.
- [x] **T2. PAC client and cached gateway.** FR-3, FR-5, FR-7, FR-9
  - Done when: `PacSettlementGatewayTest` covers cache hits/expiry, 404, errors, blank credentials and the
    Basic header; green.
- [x] **T3. Driver service, endpoint and security.** FR-1, FR-8
  - Done when: `DriverSettlementServiceImplTest` and the `SecurityConfigTest` cases are green; `./mvnw test` green.
- [x] **T4. Docs.** —
  - Done when: DRIVER_FLOW.md section, API.md row and the PAC_ARRIVALS.md pointer are written.

## App (`react-nati`)
- [x] **T5. Types, API, format and seen logic.** FR-15, FR-16
  - Done when: jest covers `format` and `seen`; tsc clean.
- [x] **T6. "Rendición" tab and screen.** FR-10..FR-15
  - Done when: driver-only tab with badge, cards per route with every state, pull-to-refresh, 5-minute
    foreground refresh; tsc and jest green.
- [x] **T7. Version bump 1.2.14.** —
  - Done when: `app.json` version and versionCode bumped; CHANGELOG `[Unreleased]` entry.

## Validation
- [x] **T8. Validate FR by FR.** All
  - Done when: reviewer `VERDICT: APPROVED` over backend and app.
