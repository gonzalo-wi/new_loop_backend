# Tasks 009 — Driver receipt photos

Status: approved (user, 2026-11-21, chat)

Each task: failing test first, then code, then `./mvnw test` green. One task at a time.

- [x] **T1. Pure image format resolution.** FR-6, FR-13
  - `settlements/domain/ReceiptImageFormat` with `resolve(declaredType, head)`.
  - Done when: `ReceiptImageFormatTest` covers every allowed declared type, case and MIME parameters,
    undeclared + valid/invalid bytes, PNG/JPEG mismatch both ways, PDF bytes, empty array and an unknown type;
    green.

- [x] **T2. Feature exceptions and their HTTP mapping.** FR-8, FR-11, FR-11a, FR-15a
  - `settlements/exception/*` (400, 413, 415, 422), `integrations/aguas/exception/AguasReceiptUploadException`
    (502), Spanish messages; `GlobalExceptionHandler` handlers incl. `MaxUploadSizeExceededException` → 413
    (receipt message on `/settlements/me/receipts`, generic Spanish elsewhere).
  - Done when: `GlobalExceptionHandlerTest` asserts status, `ApiError` shape and message for each new case; green.

- [x] **T3. Dedicated Aguas receipt client: request shape and status handling.** FR-1, FR-5, FR-6, FR-7, FR-16
  - `AguasReceiptClientConfig` (RestClient + JDK factory, properties with 5 s / 10 s defaults),
    `AguasReceiptClient.upload`, `ReceiptImage` with safe `toString`.
  - Done when: `AguasReceiptClientTest` (MockRestServiceServer) verifies URL, method, `reparto`, `fecha`,
    `imagen` filename and content type, 2xx with any body = success, 400/500/302 = `AguasReceiptUploadException`,
    exactly one request; green.

- [x] **T4. Aguas client transport failures, no redirects, no retry, no body in logs.** FR-16, FR-17, FR-18, NFR-3, NFR-4
  - Done when: `AguasReceiptClientTransportTest` (JDK `HttpServer`, real factory, small timeouts) proves:
    stalled server → exception within the bound; closed port → exception; 302 not followed; exactly 1 hit; a
    marker inside the image bytes never appears in captured log events; green.

- [x] **T5. Upload service with route checks and upload day.** FR-1, FR-3, FR-4, FR-8, FR-11, FR-13, FR-14, FR-15, FR-19, NFR-2
  - `RouteRepository.findByIdAndDriverIdAndActiveTrue`, `DriverReceiptService` + `DriverReceiptServiceImpl`.
  - Done when: `DriverReceiptServiceImplTest` covers 22:00 Argentina with a UTC fixed clock → same day,
    midnight boundaries, exactly 10 MB accepted / +1 byte → 413, 11 MB to a foreign route → 413 without reading
    the route, bad bytes to a foreign route → 415, foreign/inactive/missing route → same 404 message,
    no route number → 422, Aguas never called on any error, client failure → 502; green.

- [x] **T6. Request reader and endpoint.** FR-2, FR-3, FR-8, FR-10, FR-12, FR-15a
  - `ReceiptUploadRequestReader`, `POST /settlements/me/receipts` in `SettlementController` → 204.
  - Done when: `SettlementControllerReceiptTest` covers 204 with empty body, non-multipart → 415 (not 400),
    missing/malformed/repeated `routeId` → 400, missing/repeated `image` → 400, extra `fecha` part ignored,
    Spanish `ApiError` messages; green.

- [x] **T6a. Corrupt multipart body → 400 on the receipts endpoint only.** FR-8, FR-10a (change 2026-11-21)
  - Done when: a test sends `multipart/form-data` with a corrupt body to `POST /settlements/me/receipts` and gets
    400 with the FR-10 Spanish message in `ApiError`, a test shows another endpoint's answer to the same kind of
    request is unchanged, and `./mvnw test` is green.

- [x] **T7. Security lock-in.** FR-8, FR-9, NFR-1
  - No production change expected.
  - Done when: `SecurityConfigTest` has 401 without token, 403 for ADMIN, SUPERVISOR, CONTROLADOR, VISUALIZADOR,
    CLARKISTA and REPARTIDOR reaching the controller on `POST /settlements/me/receipts`;
    `ReadOnlyRoleAccessPolicyTest` asserts the endpoint is not allowed for the read-only roles; green.

- [x] **T8. Docs.** NFR-6
  - Done when: `API.md` row, `docs/DRIVER_FLOW.md` section 6 + summary row, `docs/AUTH.md` REPARTIDOR row,
    `CHANGELOG.md` `[Unreleased]` Added entry and `MEMORY.md` spec 009 entry are written.

- [x] **T9. Full suite and validation.** All
  - Done when: `./mvnw test` green (0 failures), and the FR-by-FR validation ends in `VERDICT: APPROVED`.
    The manual check on homologation stays with the user (real driver, real receipt, once the APK exists).
