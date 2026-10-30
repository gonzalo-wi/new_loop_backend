# Plan 002 — Send bagged serials to Odoo

Status: approved

<!-- Approved on 2026-10-30. -->

Spec: [spec.md](./spec.md)

## Where it lives
Existing layered structure, Odoo integration package (`integrations/odoo/`). No new layer, no
migration, no change to the public API of the backend.

| File | Change | FR |
|---|---|---|
| `dispensers/entity/DispenserMovement.java` | New `repairSerials()` and `baggedSerialsToSend()`: split `serialsToSend()` by `isBagged` | FR-1, FR-2, FR-4, FR-7 |
| `integrations/odoo/dto/OdooRepairParams.java` | New `embolsados` list, omitted from the JSON when null | FR-1, FR-3 |
| `integrations/odoo/mapper/OdooRepairMapper.java` | Repair intake: `equipos` = `repairSerials()`, `embolsados` = `baggedSerialsToSend()`. No-normalizados intake unchanged (no `embolsados`) | FR-1 to FR-5 |
| `dispensers/dto/CreateDispenserMovementRequest.java` | Fix the comment that says bagged serials are never sent out | — |
| `docs/DISPENSERS.md` | Note on the unload's repair intake: bagged serials go apart | — |

`OdooRepairServiceImpl` does not change: `send` and `retry` both build the body through
`odooRepairMapper.toRequest(movement)`, so a retry gets the same split (FR-6). An Odoo rejection of an
all-bagged intake already ends in `markError`, retryable (FR-5).

## Algorithm
```
forwarded = serialsToSend()                       // scanned minus excluded (FR-4)
bagged    = forwarded.filter(isBagged)            // case-insensitive (FR-7)
repair    = forwarded.filter(not isBagged)
params    = { fecha, idreparto, tecnico, usuario, equipos: repair, embolsados: bagged }
```
`embolsados` is always a list (possibly empty) on the repair intake (FR-3).

## Decisions
- **D1. Split in the entity, not in the mapper.** The entity already owns `serialsToSend()` and
  `isBagged()`; keeping the split next to them keeps one definition of "what goes where".
  Discarded: filtering in the mapper, which would duplicate the bagged-matching rule.
- **D2. `embolsados` null-excluded in the shared params DTO.** `OdooRepairParams` is also the body of
  the "equipos no normalizados" intake. `@JsonInclude(NON_NULL)` on the field keeps that request
  exactly as today, while the repair intake always sets it (empty list when there are none).
  Discarded: a separate DTO for repair, more code for one field.
- **D3. Send the serial as stored (the scanned spelling).** Matching ignores case, but the value sent
  is the one in `serials`, like `equipos` today.

## Test strategy
- `OdooRepairMapperTest` (new, pure unit): split with bagged; no bagged → `embolsados` empty;
  bagged that was excluded → in neither list; all bagged → `equipos` empty; case-insensitive match;
  no-normalizados request has no `embolsados`.
- `OdooRepairServiceImplTest`: the HTTP body carries `equipos` and `embolsados` (`jsonPath`), on
  send and on retry.
- Commands: `./mvnw test` (Git Bash). These tests don't need Postgres.
