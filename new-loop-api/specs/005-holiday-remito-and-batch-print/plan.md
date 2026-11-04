# Plan 005 — Holiday remito, batch printing and quantities in bulks

Status: approved (user, 2026-11-04, chat)

Spec: [spec.md](spec.md). Repos: backend (`new-loop-api`) and panel (`new-loop-front`). The app
needs no change (FR-9): it downloads the PDF the backend renders.

## Backend

### Holiday calendar (new `holidays/` package, same layering as `branches/`)
| File | Responsibility | FR |
|---|---|---|
| `db/migration/V42__create_holidays_table.sql` | `holidays(id uuid pk, holiday_date date unique not null, description varchar(120), created_at timestamp)` | FR-1, FR-3 |
| `holidays/entity/Holiday.java` | JPA entity | FR-1 |
| `holidays/repository/HolidayRepository.java` | `existsByDate`, `findByDateGreaterThanEqualOrderByDateAsc` | FR-3, FR-4, FR-7 |
| `holidays/dto/{CreateHolidayRequest,HolidayResponse}.java` | `date` required (`@NotNull`), `description` optional (max 120) | FR-1 |
| `holidays/exception/{HolidayAlreadyExistsException,HolidayNotFoundException}.java` | 409 / 404 through the existing `@RestControllerAdvice` | FR-3 |
| `holidays/service/iService/HolidayService.java` + `HolidayServiceImpl.java` | `list(from)`, `create`, `delete`, `isHoliday(LocalDate)` | FR-1..FR-4, FR-7 |
| `holidays/controller/HolidayController.java` | `GET /holidays?from=` (any logged-in user; default = first day of the current month in Argentina, from the injected `Clock`), `POST /holidays`, `DELETE /holidays/{id}` | FR-1, FR-2, FR-4 |
| `common/config/SecurityConfig.java` | `POST /holidays` and `DELETE /holidays/**` → `hasRole("ADMIN")` | FR-5 |

No date restriction on create, so today and past dates are allowed (FR-2).

### Remito rendering
| File | Responsibility | FR |
|---|---|---|
| `stockcontrols/pdf/RemitoQuantity.java` (new, pure) | `format(int units, Integer packQuantity)`: pack ≤ 1 or null → `"12"`; exact multiple → `"2"`; otherwise → `"15 u"` | FR-10..FR-12 |
| `stockcontrols/pdf/RemitoPdfGenerator.java` | `generate(List<RemitoPage> pages)` renders every remito into one `Document`, each one after `document.newPage()`. `RemitoPage(StockControl control, boolean holiday)`. Holiday → no company header / legal table, title "REMITO", banner kept. The Total column uses `RemitoQuantity`. `generate(control, holiday)` stays as a one-page shortcut | FR-6, FR-10..FR-12, FR-16 |
| `stockcontrols/service/StockControlServiceImpl.java` | `buildRemito` asks `HolidayService.isHoliday(control.getControlDate())` at render time | FR-7, FR-9 |

### Batch endpoints
| Endpoint | Behaviour | FR |
|---|---|---|
| `POST /stock-controls/remitos` body `{ "ids": [..] }` → `application/pdf` | 1–100 ids (`@Size`), else 400. Loads all, keeps the request order. If any is missing or not available, 409 with every failing route code and reason, and no PDF | FR-16, FR-19, FR-20 |
| `POST /stock-controls/remitos/printed` body `{ "ids": [..] }` | Marks them all printed in one transaction; same availability check | FR-17 |

Pseudocode of the batch render:
```
controls = ids.map(findById or collect "not found")
failures = controls.filter(not available).map(route code + reason)
if failures not empty -> throw RemitoBatchNotAvailableException(failures)   // 409
holidays = holidayService.holidaysIn(distinct control dates)                 // one query
return generator.generate(controls.map(c -> RemitoPage(c, holidays.contains(c.controlDate))))
```

## Panel
| File | Responsibility | FR |
|---|---|---|
| `features/holidays/{types.ts, services/holidays.service.ts}` | API calls | FR-1, FR-4 |
| `features/holidays/pages/HolidaysPage.tsx` | List from the current month; admin sees "Agregar feriado" (date + description) and delete with confirmation. Loading / error / empty states | FR-1..FR-4 |
| `app/router/index.tsx`, sidebar, `Topbar.tsx` | Route `/holidays` ("Feriados"), sidebar entry for every panel user; create/delete buttons only for ADMIN | FR-4, FR-5 |
| `features/stock/lib/remitoSelection.ts` (pure) | `selectable(control)`, `selectAllNotPrinted(list)`, `alreadyPrintedCount(selection)` | FR-13..FR-15, FR-18 |
| `features/stock/hooks/useRemitoBatchPrint.ts` | Fetches the merged PDF, prints it through the hidden iframe (same trick as `RemitoActions`), then marks printed | FR-16, FR-17, FR-19 |
| `features/stock/components/StockControlsPage.tsx` + new `RemitoBatchBar.tsx` | Checkbox column on EXIT rows with remito available, "Seleccionar no impresos", "Imprimir (n)", reprint confirmation | FR-13..FR-18 |
| `features/stock/services/stock-controls.service.ts` | `fetchRemitosPdf(ids)`, `markRemitosPrinted(ids)` | FR-16, FR-17 |

"Available" in the panel = `type === 'EXIT'` and `aguasFormulario` and `aguasNroRemito` present,
the same rule the backend applies. The selection lives on the current page of the list (FR-20 keeps
it to one day's list; the list's page size is raised so a day fits, capped at 100).

## Decisions
- **Holiday decided at render time**, not stored on the control: adding a holiday late still
  changes reprints (FR-7). Discarded: a flag on the control set when it is created.
- **One merged PDF from the backend** for the batch: one fetch and one print dialog, page breaks
  guaranteed. Discarded: merging PDFs in the browser (needs a new dependency) or N print dialogs.
- **Batch "printed" endpoint** instead of N calls to the existing one: one transaction, no half
  marked batch.
- **Calendar readable by every panel user**, editable only by ADMIN (FR-5).

## Tests
- Backend (`./mvnw test`): `RemitoQuantityTest` (unit); `HolidayServiceImplTest` (duplicate, list
  from date); `RemitoPdfGeneratorTest` extracts text with OpenPDF's `PdfTextExtractor`: holiday page
  has no CUIT/company name, normal page has them, quantities "2" / "15 u", 3 controls → 3 pages;
  `StockControlServiceImplTest` for holiday lookup and batch failures; `SecurityConfigTest` for
  401/403 on `/holidays`.
- Panel (`npx tsc --noEmit`, no unit runner): `remitoSelection.ts` kept pure; checked by hand in the
  browser against a local backend.
