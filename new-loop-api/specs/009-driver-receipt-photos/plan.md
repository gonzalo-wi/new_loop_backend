# Plan 009 — Driver receipt photos

Status: approved (user, 2026-11-21, chat)

Spec: `spec.md` (approved 2026-11-21). Backend only (`new-loop-api`); the app screen is out of scope of this spec.

## Where things live (existing layered/feature architecture)
The feature extends `settlements/` (controller → service interface + impl → domain/dto/exception), and the
outbound call lives in `integrations/aguas/`, the same way spec 007 put PAC's client in `integrations/pac/` and
its use in `settlements/service/`. No new layer or pattern.

### `settlements/controller/SettlementController` (changed)
- New `@PostMapping("/me/receipts")` returning `204 No Content`. It takes the raw `HttpServletRequest` (not
  `@RequestPart`) so the error order of FR-8 and the Spanish messages are under our control instead of Spring's
  binding errors. Delegates to `ReceiptUploadRequestReader`, then to `DriverReceiptService`. No logic.

### `settlements/controller/ReceiptUploadRequestReader` (new, web-layer helper)
Turns the request into a `ReceiptUpload` or throws, in this order (FR-8 steps 4–5):
1. Not a `MultipartHttpServletRequest` (or content type not `multipart/form-data`) → `UnsupportedReceiptImageException`
   with the "not multipart" message (415, FR-12).
2. `routeId`: `getParameterValues("routeId")` missing/empty, more than one value, or not a UUID →
   `InvalidReceiptUploadException` (400, FR-10).
3. `image`: `getFiles("image")` empty → 400; more than one → 400 (FR-10). A present 0-byte file is not
   "missing": it goes on and ends as 415 in the service (spec edge case).
4. Every other part and parameter (including `fecha`) is ignored (FR-2, FR-3).
- Returns `ReceiptUpload(UUID routeId, String declaredType, long size, InputStreamSource content)`. The content
  is read later, after the size check, so a big file is never loaded into memory just to be rejected.

### `settlements/domain/ReceiptImageFormat` (new, pure enum)
- `JPEG("image/jpeg", "jpg")`, `PNG("image/png", "png")`.
- `static Optional<ReceiptImageFormat> resolve(String declaredType, byte[] head)`:
  - declared type normalized: strip MIME parameters after `;`, trim, lower-case (FR-13, spec Definitions).
  - real format from magic bytes: `FF D8 FF` → JPEG; `89 50 4E 47 0D 0A 1A 0A` → PNG; else none (empty array →
    none).
  - undeclared (`null`, blank, `application/octet-stream`) → real format.
  - `image/jpeg|image/jpg|image/pjpeg` → JPEG only if the real format is JPEG; `image/png` → PNG only if real is
    PNG; any other declared type → empty.
- No Spring, no I/O. FR-13, FR-6 (filename extension and content type come from here).

### `settlements/service/iService/DriverReceiptService` + `service/DriverReceiptServiceImpl` (new)
Separate from `DriverSettlementService` (single responsibility: one reads PAC, the other forwards to Aguas).
Not `@Transactional` (same reason as 007: no DB connection held while Aguas answers up to 15 s); the route read
runs in the repository's own short transaction.

```
upload(ReceiptUpload u):
  user = currentUserProvider.current() filtered REPARTIDOR else ForbiddenException   // defence; the chain already 403s
  if u.size > MAX_IMAGE_BYTES (10 * 1024 * 1024)        -> ReceiptImageTooLargeException      (413, FR-11)
  bytes  = u.content.readAllBytes()
  format = ReceiptImageFormat.resolve(u.declaredType, bytes) else UnsupportedReceiptImageException (415, FR-13)
  route  = routeRepository.findByIdAndDriverIdAndActiveTrue(u.routeId, user.id())
           else NotFoundException("Reparto no encontrado")                                  (404, FR-14)
  number = RouteNumber.of(route.code) else RouteWithoutNumberException                       (422, FR-15)
  day    = LocalDate.ofInstant(clock.instant(), ARGENTINA)                                    (FR-3, FR-4)
  aguasReceiptClient.upload(number, day, new ReceiptImage(format, bytes))                   (FR-1, 502 inside)
```
- `ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires")` explicit, so the date does not depend on the JVM
  default zone or on the zone of the injected `Clock` (tests use a UTC fixed clock to prove it). NFR-2.
- Order of checks = FR-8 steps 6–9; nothing touches Aguas before all pass.

### `routes/repository/RouteRepository` (changed)
- New derived query `Optional<Route> findByIdAndDriverIdAndActiveTrue(UUID id, UUID driverId)`: one query, and
  "missing", "foreign", "unassigned" and "inactive" collapse into the same empty result (FR-14). Same active rule
  as 007's `findByDriverIdAndActiveTrueOrderByCodeAsc`, so the screen and the upload see the same routes.

### `settlements/exception/` (new package, like `loadforecast/exception/`)
`InvalidReceiptUploadException` (400), `ReceiptImageTooLargeException` (413), `UnsupportedReceiptImageException`
(415), `RouteWithoutNumberException` (422). Messages are Spanish constants, e.g. "La imagen supera los 10 MB",
"Solo se aceptan imágenes JPEG o PNG", "El reparto no tiene número", "Falta el reparto o la imagen".
404 reuses `common/exception/NotFoundException` with a Spanish message.

### `integrations/aguas/client/AguasReceiptClient` (new) + `AguasReceiptClientConfig` (new)
- `AguasReceiptClientConfig` builds a dedicated `RestClient` bean (qualifier `aguasReceiptRestClient`) with
  base URL `${integrations.aguas.base-url}` (FR-7) and a `JdkClientHttpRequestFactory` over an `HttpClient` with
  `connectTimeout` 5 s and `followRedirects(Redirect.NEVER)` (FR-17); request (read) timeout 10 s, so connect +
  response stays within 15 s total whichever way the JDK counts them (FR-16, NFR-3). A static
  `restClient(baseUrl, connectTimeout, readTimeout)` factory is reused by tests with small timeouts.
- Properties in `application.properties`:
  `integrations.aguas.receipts.connect-timeout-ms=${AGUAS_RECEIPTS_CONNECT_TIMEOUT_MS:5000}`,
  `integrations.aguas.receipts.read-timeout-ms=${AGUAS_RECEIPTS_READ_TIMEOUT_MS:10000}`. No `.env` change needed.
- `upload(long reparto, LocalDate fecha, ReceiptImage image)`:
  - body built with `MultipartBodyBuilder`: `reparto` (string), `fecha` (`yyyy-MM-dd`), `imagen` = byte
    resource with filename `comprobante-<reparto>-<fecha>.<jpg|png>` and part content type from the format (FR-6).
  - sent with `.exchange((req, res) -> res.getStatusCode())`, so a non-2xx never becomes a Spring exception whose
    message could carry bodies; 2xx → return (FR-5); anything else, including 3xx → `AguasReceiptUploadException`
    (FR-16). `RestClientException`/`IOException`/timeouts → same exception with the cause.
  - one call, no retry wrapper; the JDK client does not retry a POST (FR-18).
  - logs once at WARN: "Aguas receipt upload failed for route {} on {}: {}" with status or cause; never the body
    (NFR-4). Success logs at INFO with route number and day only.
- `ReceiptImage(ReceiptImageFormat format, byte[] bytes)` record with `toString()` overridden to print the format
  and size only, so the bytes never reach a log by accident (NFR-4).
- `integrations/aguas/exception/AguasReceiptUploadException` (new): 502, Spanish message "No se pudo enviar la
  foto a Aguas. Probá de nuevo."

### `common/exception/GlobalExceptionHandler` (changed)
- Handlers for the four `settlements/exception` types and `AguasReceiptUploadException`, all through the
  existing `buildResponse` → `ApiError` (FR-15a).
- `MaxUploadSizeExceededException` → 413 (FR-11a). It is raised by the `DispatcherServlet` before a handler is
  chosen, so it must be global. Message: the Spanish "La imagen supera los 10 MB" when the request URI is
  `/settlements/me/receipts`; for any other URI a generic Spanish "El archivo supera el tamaño máximo permitido"
  (today those requests fall into the generic 500).
- 401/403 stay in `JsonAuthErrorHandler` (shared, unchanged, English messages), as FR-15a allows.

### Security — no change to `SecurityConfig` or `ReadOnlyRoleAccessPolicy`
`/settlements/**` is already `hasRole("REPARTIDOR")`, matched before the read-only allowlist, so every other role
(VISUALIZADOR and CLARKISTA included) gets 403 and anonymous gets 401 (FR-8 steps 1–2, FR-9). Tests lock it in.

### Multipart limit
Global `spring.servlet.multipart.max-file-size/max-request-size=256MB` unchanged (FR-11: other endpoints keep
their limits). The 10 MB rule is the service's `MAX_IMAGE_BYTES`.

## Decisions
- **RestClient + JDK HttpClient for Aguas, not a second Feign client.** RestClient (already used by the Odoo
  services) has no body logging at all and gives direct control of redirects and timeouts on the JDK client.
  Discarded: a Feign client with its own config at `Logger.Level.BASIC/NONE` — it works, but the package-wide
  `logging.level...AguasClient=DEBUG` and `AguasFeignConfig` (FULL) sit next to it and one config slip would log
  the image; Feign multipart also needs extra encoder setup.
- **Raw request reader instead of `@RequestPart`.** Spring's binding raises `MissingServletRequestPartException`
  / `HttpMediaTypeNotSupportedException` with English messages and its own order, and silently takes the first
  of repeated parts. Discarded: global handlers for those exceptions, which would change other endpoints too.
- **Same controller, new service.** `/settlements/**` already carries the REPARTIDOR rule, so no security change.
  Discarded: a new controller under another prefix (needs new matchers and allowlist care).
- **Size check before reading the bytes, in the service.** Keeps the order testable without a servlet and never
  loads an oversize file. Discarded: checking in the reader (then the service tests could not prove the
  413-before-404 precedence).
- **Explicit Argentina zone instead of `LocalDate.now(clock)`.** Same result in production (the JVM default is
  Argentina) but does not depend on it. Discarded: relying on the JVM default as 007 does.
- **Path-based message for `MaxUploadSizeExceededException`.** Discarded: the "10 MB" message for every endpoint,
  wrong for the APK upload.

## Tests (stand-in for Aguas, no new dependency)
- `ReceiptImageFormatTest` (pure): each allowed declared type, case and parameters (`IMAGE/JPEG`,
  `image/jpeg; charset=binary`), undeclared + valid/invalid bytes, PNG declared as JPEG and vice versa, PDF bytes,
  empty array, unknown declared type.
- `DriverReceiptServiceImplTest` (Mockito for repository, client, current user; fixed clock):
  - 2026-11-22T01:00Z (22:00 Argentina) with a UTC clock → `fecha` 2026-11-21; 23:59:59 and 00:00:00 boundaries.
  - exactly 10,485,760 bytes accepted; one more → 413 and the repository is never called (precedence: 11 MB to a
    foreign route → 413); invalid bytes for a foreign route → 415 before 404.
  - foreign, inactive, missing route → same `NotFoundException` message; `RouteNumber` null → 422; client never
    called on any error; client exception propagates as 502.
- `AguasReceiptClientTest` with `MockRestServiceServer` bound to the `RestClient.Builder`: URL
  `/api/set-file-reparto-comprobantes`, POST, multipart fields `reparto`, `fecha`, `imagen`, filename and part
  content type; 200/201/204 with any body → success; 400/500/302 → `AguasReceiptUploadException`; exactly one
  request expected.
- `AguasReceiptClientTransportTest` with the JDK's built-in `com.sun.net.httpserver.HttpServer` and the real
  `JdkClientHttpRequestFactory` (small timeouts): stalled server → exception within the bound; closed port →
  exception; 302 to a second handler → exception and the second handler gets 0 hits; first handler gets exactly
  1 hit (no retry). Log check with a Logback `ListAppender` on the root logger: a marker string embedded in the
  image bytes never appears in any log event (NFR-4).
- `SettlementControllerReceiptTest` (`@WebMvcTest(SettlementController)`, `GlobalExceptionHandler` imported,
  filters off, service mocked): 204 empty body; non-multipart → 415 (not 400); missing/malformed/repeated
  `routeId` → 400; missing/repeated `image` → 400; extra `fecha` part ignored (service gets no date); every
  error body is `ApiError` with a Spanish `message`.
- `GlobalExceptionHandlerTest`: `MaxUploadSizeExceededException` → 413 with the receipt message on the receipts
  URI and the generic one elsewhere; the five new exceptions → their status.
- `SecurityConfigTest`: `POST /settlements/me/receipts` 401 without token; 403 for ADMIN, SUPERVISOR,
  CONTROLADOR, VISUALIZADOR, CLARKISTA; REPARTIDOR reaches the controller. `ReadOnlyRoleAccessPolicyTest`:
  `allows(VISUALIZADOR|CLARKISTA, "POST", "/settlements/me/receipts")` is false.
- Commands (CLAUDE.md): `./mvnw test` (Git Bash). None of these tests needs Postgres. The real Aguas is never
  called by tests or by the assistant; the manual check on homologation is the user's.

## Docs (NFR-6)
- `API.md`: row `POST /settlements/me/receipts` next to `/settlements/me`.
- `docs/DRIVER_FLOW.md`: new section "6. Fotos de comprobantes" (request, 204, error table 400/413/415/404/422/502
  with the Spanish messages), and a row in "Resumen de endpoints".
- `docs/AUTH.md`: REPARTIDOR row adds "sube fotos de comprobantes".
- `CHANGELOG.md` `[Unreleased]` → Added; `MEMORY.md` spec 009 state and decisions.

## FR coverage
| FR / NFR | Where |
|---|---|
| FR-1, FR-5 | `DriverReceiptServiceImpl`, `AguasReceiptClient` |
| FR-2, FR-3, FR-10, FR-12 | `ReceiptUploadRequestReader`, controller |
| FR-4, NFR-2 | service day computation with injected `Clock` |
| FR-6 | `ReceiptImageFormat` + client multipart |
| FR-7 | `AguasReceiptClientConfig` (existing base-url property) |
| FR-8 | security chain (1–2), handler (3), reader (4–5), service (6–9), client (502) |
| FR-9 | existing `SecurityConfig` matcher, tests |
| FR-11, FR-13, FR-14, FR-15 | service + exceptions |
| FR-11a | `GlobalExceptionHandler` |
| FR-15a | exceptions' Spanish messages + `ApiError` |
| FR-16, FR-17, FR-18, NFR-3 | client config and client |
| FR-19 | nothing persisted (no entity, no migration) |
| NFR-1 | existing JWT chain; base URL only server-side |
| NFR-4 | dedicated RestClient, `ReceiptImage.toString`, log test |
| NFR-5 | Tests section |
| NFR-6 | Docs section |

## Open questions
None. Resolved 2026-11-21 (user, chat): 401/403 keep the shared English messages of `JsonAuthErrorHandler`;
Spanish applies to the errors this feature raises (400/413/415/404/422/502). FR-15a updated accordingly.
