# Tasks 002 — Send bagged serials to Odoo

Status: approved, implemented

<!-- Approved on 2026-10-30. -->

- [x] **T1. Split the forwarded serials into repair and bagged in the movement.** FR-1, FR-2, FR-4, FR-7
  - Done when: mapper unit tests for the split, the excluded bagged serial and case-insensitive
    matching fail first and then pass with `repairSerials()` / `baggedSerialsToSend()`.
- [x] **T2. Send `embolsados` in the repair intake only.** FR-1, FR-3, FR-5
  - Done when: `OdooRepairMapperTest` checks `embolsados` empty when none and `equipos` empty when
    all are bagged, the no-normalizados request has no `embolsados` key, and all pass.
- [x] **T3. Cover the HTTP body on send and retry.** FR-6
  - Done when: `OdooRepairServiceImplTest` asserts `$.params.equipos` and `$.params.embolsados` on
    `send` and on `retry`, green.
- [x] **T4. Update docs and the request comment.**
  - Done when: `docs/DISPENSERS.md` explains the bagged split on unloads, the comment in
    `CreateDispenserMovementRequest` no longer says "never sent out", and `./mvnw test` is green
    (except the known pre-existing Postgres failures).
