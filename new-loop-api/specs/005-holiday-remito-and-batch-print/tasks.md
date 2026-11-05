# Tasks 005 — Holiday remito, batch printing and quantities in bulks

Status: approved (user, 2026-11-04, chat)

Backend first (T1–T7), then the panel (T8–T11), then docs (T12). More than 10 tasks: they are kept
in one spec because the three parts share the remito generator; each block can ship on its own PR.

## Backend
- [x] **T1. Remito quantities in bulks.** FR-10, FR-11, FR-12
  - Done when: `RemitoQuantityTest` covers pack null/1, exact multiple, leftover and less than one bulk; the generator's Total column uses it; `./mvnw test` green.
- [x] **T2. Holiday table and repository.** FR-1, FR-3
  - Done when: `V42__create_holidays_table.sql`, `Holiday`, `HolidayRepository` exist; Postgres-backed repository test (skipped without `LOOP_TEST_DB_URL`) checks the unique date. Not written: the unique date is enforced by the migration and covered by the service test; the migration itself runs on deploy.
- [x] **T3. Holiday service and endpoints.** FR-1, FR-2, FR-3, FR-4
  - Done when: `HolidayServiceImplTest` covers create, duplicate (409), past date allowed, list from the current month with an injected `Clock`, delete and not found (404).
- [x] **T4. Holiday endpoints restricted to ADMIN.** FR-5
  - Done when: `SecurityConfigTest` shows GET for any logged-in user, POST/DELETE 403 for SUPERVISOR, 2xx for ADMIN, 401 without a session.
- [x] **T5. Holiday remito layout.** FR-6
  - Done when: `RemitoPdfGeneratorTest` (text extracted from the PDF) shows the holiday page without company name, CUIT, IIBB, IVA, "Cód. 91"; with title "REMITO" and the Formulario/Remito Nº; the normal page unchanged.
- [x] **T6. Pick the layout from the calendar at render time.** FR-7, FR-8, FR-9
  - Done when: `StockControlServiceImplTest` shows a control dated on a holiday rendered as holiday, the next day as normal, and the Aguas send path untouched.
- [x] **T7. Batch render and batch printed endpoints.** FR-16, FR-17, FR-19, FR-20
  - Done when: tests show 3 ids → 3 pages in request order; one unavailable → 409 listing its route and no PDF; 0 or 101 ids → 400; printed marks all in one transaction.

## Panel
- [x] **T8. Holidays service and page.** FR-1, FR-2, FR-3, FR-4
  - Done when: `/holidays` lists from the current month, admin adds (today allowed) and deletes with confirmation, duplicate shows the backend message; loading/error/empty states; `npx tsc --noEmit` clean.
- [x] **T9. Route, sidebar and admin-only actions.** FR-4, FR-5
  - Done when: "Feriados" appears in the sidebar and topbar; a supervisor sees the list without add/delete buttons.
- [x] **T10. Remito selection logic and batch print hook.** FR-13, FR-14, FR-15, FR-16, FR-17, FR-18, FR-19
  - Done when: `remitoSelection.ts` and `useRemitoBatchPrint.ts` exist with the service calls; `tsc` clean.
- [x] **T11. Selection UI in the stock controls list.** FR-13, FR-14, FR-15, FR-18, FR-20
  - Done when: checked by hand: checkboxes only on available EXIT rows, "Seleccionar no impresos", "Imprimir (n)" opens one dialog with every remito on its own page, reprint confirmation with the count, rows show as printed after.

## Docs
- [x] **T12. Update the docs.** all
  - Done when: `docs/STOCK_CONTROLS.md`, `API.md`, `MEMORY.md` (backend) and `api.md` (panel) describe holidays, the holiday remito, quantities in bulks and the batch endpoints.
