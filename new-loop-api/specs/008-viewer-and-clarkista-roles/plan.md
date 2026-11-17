# Plan 008 — Viewer and forklift operator (clarkista) roles

Status: approved (user, 2026-11-16, chat)

Spec: `spec.md` (approved 2026-11-16). This plan covers the backend (FR-1..FR-13, NFR-1, NFR-2).
The panel (FR-14..FR-17) and the app (FR-18..FR-22) are planned and done in their own repos
(`new-loop-front`, `react-nati`) after the backend, against the contract in section 4.

## 1. Files and responsibilities (backend, existing layered-by-feature structure)

| File | Change | FR |
|---|---|---|
| `users/entity/Role.java` | Add `VISUALIZADOR`, `CLARKISTA`. `isBranchScoped()` true for CONTROLADOR, VISUALIZADOR, CLARKISTA. New `isReadOnly()` true for VISUALIZADOR, CLARKISTA. | FR-1, FR-2, FR-3 |
| `db/migration/V43__add_viewer_and_clarkista_roles.sql` | Drop and recreate `chk_user_role` with the 6 roles. | FR-1 |
| `common/security/ReadOnlyRoleAccessPolicy.java` (new) | Pure: given role + HTTP method + path, decides if a read-only role may call it. Allowlists below. | FR-5, FR-6, FR-7, FR-12, NFR-2 |
| `common/config/SecurityConfig.java` | `GET /load-forecast` → `hasAnyRole(CLARKISTA, ADMIN)`. `anyRequest()` → authenticated AND (not read-only OR allowed by the policy). | FR-5..7, FR-12, FR-13 |
| `stockcontrols/service/StockControlServiceImpl.java` | `getControlById`: for read-only roles, a control from another branch → `StockControlNotFoundException`. List and pending arrivals already scope by branch through `isBranchScoped()`. | FR-3, FR-4 |
| `loadforecast/domain/LoadDay.java` (new) | Pure: `of(LocalDate today)` → next delivery day; `referenceDays(loadDay)` → loadDay −7/−14/−21/−28. | FR-8, FR-11 |
| `loadforecast/domain/LoadForecastCalculator.java` (new) | Pure: reference controls → products with forecast quantity > 0, in product display order. | FR-8, FR-10 |
| `loadforecast/service/iService/LoadForecastService.java`, `loadforecast/service/LoadForecastServiceImpl.java` (new) | Resolves the route, reads reference controls, calls the domain. Injected `Clock`. | FR-8..FR-11, FR-13 |
| `loadforecast/controller/LoadForecastController.java` (new) | `GET /load-forecast?routeNumber=179`. | FR-8 |
| `loadforecast/dto/LoadForecastResponse.java`, `LoadForecastItemResponse.java` (new) | Response records. | FR-8 |
| `loadforecast/exception/ForecastRouteNotFoundException.java` (new) | Extends the common `NotFoundException` → 404 via `GlobalExceptionHandler`. | FR-9, FR-13 |
| `stockcontrols/repository/StockControlRepository.java` | `findReferenceControls(routeId, type, dates, excludedStatus)` with `join fetch` of items and product (one query). | FR-8, NFR-1 |
| `routes/repository/RouteRepository.java` | Reuse or add `findByActiveTrue[AndBranchId]`. | FR-8, FR-13 |
| `API.md`, `docs/STOCK_CONTROLS.md`, `docs/AUTH.md` (new section) | Document the roles, their access and the endpoint. | — |

## 2. Access policy (FR-5..7, FR-12)

```
allowed(role, method, path):
  VISUALIZADOR: method == GET and path matches one of
      /stock-controls
      /stock-controls/pending-arrivals
      /stock-controls/{uuid}            (regex on UUID, so /stock-controls/remito does not match)
  CLARKISTA:    method == GET and path == /load-forecast
  other roles:  not handled here (existing rules apply)
```
Public endpoints (`/auth/login`, `/app/version`, `/app/download/**`, `/health`, OPTIONS) stay
`permitAll` above, so both roles can log in and run the app update check.
The viewer list starts minimal. If the panel's stock pages turn out to call `/branches`, `/routes` or
`/products` (allowed by FR-7), they are added to the allowlist in the panel phase, with a test.

Decision: one central allowlist in the security chain, not checks in each service.
Discarded: `@PreAuthorize` / role sets per service method. That needs touching every write
operation, and a write endpoint added later would stay open to read-only roles by default, which
breaks NFR-2. With the allowlist, a new endpoint is denied to these roles until someone allows it.

## 3. Forecast algorithm (FR-8..FR-11, FR-13)

```
forecast(user, routeNumber):
  today   = LocalDate.now(clock)                 // clock in Argentina time (app default TZ)
  loadDay = today + 1; if loadDay is SUNDAY: loadDay += 1
  refs    = [loadDay - 7, -14, -21, -28]

  candidates = active routes (branch = user.branch, or all branches when ADMIN)
               where RouteNumber.of(code) == routeNumber
  if candidates.size != 1: throw ForecastRouteNotFound(routeNumber)
  route = candidates[0]

  controls = EXIT controls of route with controlDate in refs and status != CANCELLED
  n = controls.size
  if n == 0: return (route.code, loadDay, basedOn = [], items = [])

  for each product appearing in any control:
     sum = Σ item.totalQuantity across controls (missing = 0)
     qty = round_half_up(sum / n)
  items = products with qty > 0, ordered by displayOrder asc (nulls last), then name
  return (route.code, loadDay, basedOn = sorted control dates, items)
```
- Quantity used: `totalQuantity` (units loaded on the truck). `fullQuantity` / `exchangeQuantity`
  describe how they came back and do not apply to an exit.
- `round_half_up` with `BigDecimal` (2.5 → 3), never float division.
- Inactive products still count if they were loaded (they were really on the truck), and the
  response shows them like any other.
- More than one route of the branch with the same number → treated as not found (it should not
  happen within a branch, and guessing would show the wrong truck).

## 4. API contract (panel and app build against this)

`GET /load-forecast?routeNumber=179` — roles CLARKISTA (own branch), ADMIN.
```json
{ "success": true, "message": "Load forecast retrieved successfully",
  "data": {
    "routeCode": "Rto 179",
    "loadDate": "2026-11-17",
    "basedOnDates": ["2026-10-20", "2026-10-27", "2026-11-03", "2026-11-10"],
    "items": [ { "productCode": "B20", "productName": "Bidón 20 L", "quantity": 120 } ]
  } }
```
- No data: `basedOnDates: []`, `items: []`, HTTP 200.
- 404 `ApiError` when the route is not found. 400 when `routeNumber` is missing or not a positive integer.
- 403 for any other role.

Roles in `/auth/login` and `/users`: `VISUALIZADOR`, `CLARKISTA`, both with `branchId` required.

## 5. Tests (`./mvnw test`, Git Bash)

- `RoleTest`: `isBranchScoped` / `isReadOnly` per role. (FR-1, FR-2)
- `UserServiceImplTest`: creating a VISUALIZADOR / CLARKISTA without a branch → `BranchRequiredForRoleException`. (FR-2)
- `ReadOnlyRoleAccessPolicyTest`: allowed and denied cases (writes, remito GETs, remito batch POST, other modules). (FR-5..7, FR-12)
- `SecurityConfigTest` (real filter chain): VISUALIZADOR → 200 on list/detail, 403 on POST/PATCH,
  approve, reject, correct, GET remito, POST remitos, GET /orders; CLARKISTA → 403 on /stock-controls;
  REPARTIDOR / CONTROLADOR → 403 on /load-forecast. (FR-5..7, FR-12, FR-13, NFR-2)
- `StockControlServiceImplTest`: viewer list forced to own branch; detail of another branch → not found. (FR-3, FR-4)
- `LoadDayTest`: Monday → Tuesday, Saturday → Monday, reference days. (FR-8, FR-11)
- `LoadForecastCalculatorTest`: average over controls found, missing product = 0, half-up,
  zero dropped, ordering, empty input. (FR-8, FR-10)
- `LoadForecastServiceImplTest` (mocked repositories, fixed `Clock`): route of another branch → 404,
  duplicated number → 404, admin across branches, no controls → empty. (FR-8..FR-11, FR-13)
- `LoadForecastControllerTest`: response shape, 400 on bad `routeNumber`. (FR-8)
- Migration: covered by the Postgres-backed tests when `LOOP_TEST_DB_URL` is set (inserting a user with each new role).
- NFR-1: one query for the controls with `join fetch`, checked in the repository test on Postgres.
