# Tasks 008 — Viewer and forklift operator (clarkista) roles (backend)

Status: approved (user, 2026-11-16, chat)

- [x] **T1. Roles and migration.** FR-1, FR-2
  - Done when: `Role` has VISUALIZADOR and CLARKISTA, both branch-scoped and read-only; V43 recreates
    `chk_user_role`; `RoleTest` and the user service tests (no branch → rejected) pass.
- [x] **T2. Read-only access policy.** FR-5, FR-6, FR-7, FR-12
  - Done when: `ReadOnlyRoleAccessPolicyTest` covers every allowed path and the denied writes, remito
    GETs, remito batch POST and other modules, and passes.
- [x] **T3. Wire the policy into the security chain.** FR-5, FR-6, FR-7, FR-12, FR-13, NFR-2
  - Done when: `SecurityConfigTest` shows VISUALIZADOR 200 on list/detail and 403 on every write and
    remito; CLARKISTA 403 outside `/load-forecast`; other roles unchanged.
- [x] **T4. Viewer detail scoped to its branch.** FR-3, FR-4
  - Done when: `StockControlServiceImplTest` shows the viewer list forced to its branch and a detail of
    another branch → not found; the other roles' behavior is unchanged.
- [x] **T5. Load day and calculator (pure domain).** FR-8, FR-10, FR-11
  - Done when: `LoadDayTest` and `LoadForecastCalculatorTest` pass (Sunday skip, reference days,
    average over controls found, half-up, zero dropped, ordering, empty).
- [ ] **T6. Forecast service and repository query.** FR-8, FR-9, FR-10, FR-11, FR-13, NFR-1
  - Done when: `LoadForecastServiceImplTest` (fixed `Clock`) covers own branch, another branch → 404,
    duplicated number → 404, admin, no controls → empty; `LoadForecastRepositoryTest` (Postgres) shows
    the query returns only the route's non-cancelled EXIT controls on the reference days with items
    and products fetched, and that V43 accepts both roles.
  - Pending: `LoadForecastRepositoryTest` written but not yet run against Postgres (needs `LOOP_TEST_DB_URL`).
- [x] **T7. Forecast controller and endpoint.** FR-8, FR-9, FR-12, FR-13
  - Done when: the response shape, 404, 400 and the GET-only rule (CLARKISTA POST → 403) are checked
    in `SecurityConfigTest`, which runs the real filter chain against the controller.
    (Deviation: no separate `LoadForecastControllerTest`; `SecurityConfigTest` covers it.)
- [x] **T8. Docs and full suite.** —
  - Done when: `API.md`, `docs/STOCK_CONTROLS.md` and `docs/AUTH.md` describe the roles and the
    endpoint; `CHANGELOG.md` has the entry; `./mvnw test` is green.
