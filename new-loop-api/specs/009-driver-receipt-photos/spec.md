# Spec 009 — Driver receipt photos

Status: approved (user, 2026-11-21, chat)

<!-- Keep the "Status:" line and the [NEEDS CLARIFICATION] markers literal: commands search for them. -->

## Context and goal
Drivers hand in checks and withholdings with their route's settlement, and Aguas needs a photo of each
payment receipt. Until now drivers uploaded those photos from an old Vue front that sent them straight to
Aguas, one image per request, with the route number and today's date and no authentication. That front is
being retired. This spec moves the upload into the mobile app's "Mi rendición" screen (spec 007): the
logged-in driver takes or picks a photo for one of their routes and LOOP's backend forwards it to Aguas.
LOOP adds what the old front lacked: only an authenticated driver can upload, only for their own active
route, the date is decided by the server in Argentina time (never by the phone), and Aguas failures are
reported to the driver instead of being lost.

Related: spec 007 (driver settlement, same screen and same route number rule). The settlement screen
already lists only the driver's active routes, so the routes a driver sees there are exactly the routes
this spec lets them upload for.

## Users / actors
- **Driver** (mobile app, role REPARTIDOR): uploads receipt photos for their own routes.
- **LOOP backend**: validates the request and forwards each photo to Aguas.
- **Aguas**: receives the photo with the route number and date, as it did from the old front.
- **Operations**: Aguas' address is the base URL LOOP already configures for Aguas
  (`integrations.aguas.base-url`, default `http://192.168.0.58`).

## User stories
- US-1. As a driver, I want to photograph a check or withholding receipt from the app and send it for my
  route, so that Aguas gets it without me using a separate web page.
- US-2. As a driver, I want to know right away if a photo did not reach Aguas, so that I can send that
  photo again without resending the ones that already arrived.

## Contract
- **LOOP endpoint**: `POST /settlements/me/receipts`, multipart/form-data with:
  - `routeId`: the route's UUID (exactly one).
  - `image`: one image file (exactly one).
  - Any other part (including a client-sent `fecha`) is ignored.
  - Success: `204 No Content`, no body.
  - Errors (4xx and 502): the project's standard error body `ApiError`
    (`timestamp`, `status`, `error`, `message`, `path`). The `message` is in Spanish for the errors this
    feature raises (400, 413, 415, 404, 422, 502); 401 and 403 keep the app-wide shared messages (English).
- **Aguas operation**: `POST {integrations.aguas.base-url}/api/set-file-reparto-comprobantes`,
  multipart/form-data with:
  - `reparto`: the route number.
  - `fecha`: the upload day, `YYYY-MM-DD`.
  - `imagen`: the image, with filename `comprobante-<reparto>-<fecha>.<jpg|png>` and content type
    `image/jpeg` or `image/png` according to its real format.

## Definitions
- **Driver's route**: a route whose assigned driver is the logged-in user (as in spec 007).
- **Active route**: a route marked active in LOOP.
- **Route number**: the value spec 007 uses for the same route (`RouteNumber.of(route code)`); "no route
  number" means that computation gives no value, for whatever reason (no digits, too many digits...).
- **Upload day**: today's calendar date in Argentina time (America/Argentina/Buenos_Aires), taken from the
  server clock at the moment the request is received.
- **Receipt photo**: one image file of a check or withholding receipt. The document type is not
  distinguished.
- **Allowed declared types**: `image/jpeg`, `image/jpg`, `image/pjpeg`, `image/png`, compared
  case-insensitively and ignoring MIME parameters (`IMAGE/JPEG` and `image/jpeg; charset=binary` are
  allowed). A declared type of `application/octet-stream`, or no declared type, is "undeclared".
- **Real format**: JPEG if the file starts with bytes `FF D8 FF`; PNG if it starts with
  `89 50 4E 47 0D 0A 1A 0A`; otherwise none.
- **Size limit**: 10 MB = 10 × 1024 × 1024 = 10,485,760 bytes. A file of exactly that size is within it.

## Functional requirements (EARS)

### Upload
- FR-1: WHEN a driver sends a valid upload for one of their active routes, THE SYSTEM SHALL forward it to
  the Aguas operation of the Contract as one request with `reparto`, `fecha` and `imagen`, and WHEN Aguas
  answers 2xx, THE SYSTEM SHALL answer `204 No Content` with no body.
- FR-2: THE SYSTEM SHALL accept exactly one `image` part and exactly one `routeId` part per upload
  request, and SHALL ignore any other part.
- FR-3: THE SYSTEM SHALL set `fecha` from the upload day only; the request SHALL take no date parameter,
  and any date the client sends (as a part, query parameter or header) SHALL be ignored.
- FR-4: WHEN an upload is received between 21:00 and 23:59:59 Argentina time, THE SYSTEM SHALL send that
  same calendar day, not the next one.
- FR-5: WHEN Aguas answers with any 2xx status, THE SYSTEM SHALL treat the upload as successful, whatever
  the response body contains.
- FR-6: THE SYSTEM SHALL send the `imagen` part with filename `comprobante-<reparto>-<fecha>.jpg` or `.png`
  and content type `image/jpeg` or `image/png`, both according to the real format (not the declared type
  or the client's filename).
- FR-7: THE SYSTEM SHALL use the existing Aguas base URL property for the Aguas address; no new address
  setting is added.

### Validation and error precedence
- FR-8: THE SYSTEM SHALL evaluate errors in this order and answer only the first that applies; none of them
  SHALL call Aguas:
  1. `401` — the request is not authenticated.
  2. `403` — the requester's role is not REPARTIDOR (FR-9).
  3. `413` — the request body exceeds the global upload limit (256 MB); this is detected while parsing,
     before the parts are known (FR-11a).
  4. `415` — the request is not multipart/form-data (FR-12).
  5. `400` — the multipart body is corrupt or cannot be parsed, `routeId` is missing, malformed (not a
     UUID) or repeated, or `image` is missing or repeated (FR-10).
  6. `413` — the image is larger than the size limit (FR-11).
  7. `415` — the declared type is not allowed, or the real format is not JPEG/PNG or does not match the
     declared type (FR-13).
  8. `404` — the route does not exist, is not the driver's, or is not active (FR-14).
  9. `422` — the route has no route number (FR-15).
  Only after all checks pass SHALL THE SYSTEM call Aguas, whose failures answer `502` (FR-16).
- FR-9: IF the requester's role is not REPARTIDOR, THEN THE SYSTEM SHALL deny the upload (403). This
  includes the read-only roles VISUALIZADOR and CLARKISTA, which SHALL stay denied (they are not added to
  the read-only roles' allowlist).
<!-- FR-10 changed 2026-11-21 (user, chat): a corrupt multipart body answers 400 on this endpoint only. -->
- FR-10: IF `routeId` is missing, is not a valid UUID or is sent more than once, or `image` is missing or
  sent more than once, THEN THE SYSTEM SHALL reject it with 400.
- FR-10a: IF a request to `POST /settlements/me/receipts` declares a multipart Content-Type but its body is
  corrupt or cannot be parsed as multipart, THEN THE SYSTEM SHALL reject it with 400 and the same Spanish
  message as FR-10. Other endpoints SHALL keep their current behavior for such requests.
- FR-11: IF the image is larger than 10,485,760 bytes, THEN THE SYSTEM SHALL reject it with 413 and an
  "image larger than 10 MB" message. Other endpoints' upload limits SHALL not change.
- FR-11a: IF the request body exceeds the global upload limit (256 MB), THEN THE SYSTEM SHALL map that
  rejection to 413 with the same message as FR-11, whenever the servlet container lets the application
  answer. This is best effort: the container may close the connection instead, and that is accepted.
- FR-12: IF the request is not multipart/form-data, THEN THE SYSTEM SHALL reject it with 415 (a
  non-multipart request is answered 415, not 400 for missing parts).
- FR-13: IF the declared type of the image is present and is neither an allowed declared type nor
  undeclared, or the real format is none (including an empty file), or the real format does not match a
  declared JPEG/PNG type, THEN THE SYSTEM SHALL reject it with 415. WHEN the type is undeclared, the real
  format alone SHALL decide.
- FR-14: IF the route does not exist, is assigned to another driver or to none, or is not active, THEN THE
  SYSTEM SHALL answer 404 with the same message in every one of these cases, so other drivers' routes are
  not revealed.
- FR-15: IF the route has no route number, THEN THE SYSTEM SHALL reject the upload with 422 and a message
  saying the route has no route number.
<!-- FR-15a changed 2026-11-21 (user, chat): 401/403 stay with the shared English messages. -->
- FR-15a: THE SYSTEM SHALL return every error (4xx and 502) in the project's standard `ApiError` body, and
  SHALL write the `message` in Spanish for every error this feature raises (400, 413, 415, 404, 422, 502).
  The 401 and 403 answers come from the app-wide authentication handling and keep its shared (English)
  messages.

### Aguas failures
- FR-16: IF the Aguas call fails at transport level (no complete answer within 15 seconds in total, refused
  connection, DNS failure, connection reset during the upload) or Aguas answers any non-2xx status
  (including 3xx), THEN THE SYSTEM SHALL answer 502 with a Spanish message saying the photo did not reach
  Aguas and can be sent again, and SHALL log the failure with its cause, the route number and the upload
  day.
- FR-17: THE SYSTEM SHALL NOT follow redirects from Aguas.
- FR-18: THE SYSTEM SHALL make exactly one attempt to deliver each photo to Aguas and SHALL NOT retry
  automatically (in the request or later), so a photo is never duplicated in Aguas by LOOP.

### Persistence
- FR-19: THE SYSTEM SHALL NOT store the photo, nor a record of the upload, in LOOP's database.

## Non-functional requirements
- NFR-1 (security): The upload requires an authenticated LOOP session; Aguas' address never reaches the app.
- NFR-2 (time): The upload day is computed from an injectable clock so the 22:00 case is testable.
- NFR-3 (latency): When Aguas does not answer, an upload SHALL answer 502 in at most 17 seconds, measured
  from the moment LOOP has received the full request body (not from the start of the phone's upload).
- NFR-4 (logs): Logs never contain the image bytes. The Aguas call for receipts SHALL use its own HTTP
  client that never logs request bodies (the existing Aguas client configuration logs full requests, so it
  is not reused for this call).
- NFR-5 (tests): Automated tests SHALL cover at least:
  - 401 unauthenticated; REPARTIDOR allowed; other roles, including VISUALIZADOR and CLARKISTA, get 403.
  - Foreign route and inactive route answer 404 with the same message.
  - Route with no route number answers 422.
  - Upload day at 22:00 Argentina time stays today; a client-sent date is ignored.
  - The fields sent to Aguas (`reparto`, `fecha`, `imagen`), the `imagen` filename and content type.
  - Aguas down, Aguas timeout and Aguas non-2xx (including 3xx) answer 502, with one attempt and no retry.
  - Missing, malformed or repeated `routeId`, and missing or repeated `image`, answer 400; an extra
    unknown part (e.g. `fecha`) is ignored.
  - A multipart Content-Type with a corrupt body answers 400 with the FR-10 message on this endpoint.
  - Exactly 10 MB is accepted; over 10 MB answers 413.
  - The global multipart-size exception is mapped to 413 with the same message (tested at the
    exception-handler level; no end-to-end 256 MB request is required).
  - Non-multipart request, declared type not allowed, content not JPEG/PNG, content not matching the
    declared type and empty file answer 415; an undeclared type with valid JPEG/PNG content is accepted;
    `IMAGE/JPEG` and `image/jpeg; charset=binary` are accepted.
  - Precedence cases, e.g. an 11 MB image for a foreign route answers 413, not 404; a non-multipart
    request answers 415, not 400.
  - Error responses use the `ApiError` body, with a Spanish `message` for 400/413/415/404/422/502.
  - Image bytes never appear in the logs.
- NFR-6 (docs): When implemented, the API contract, CHANGELOG and MEMORY.md SHALL be updated, as the
  project does for every feature.

## Edge cases
- A driver with two routes the same day: each photo is sent with the route number of the route chosen.
- Several photos for the same route and day: each is a separate upload and Aguas receives all of them.
- The same photo sent twice by the driver (e.g. after a timeout where Aguas actually got it): LOOP forwards
  it again; deduplication is not LOOP's responsibility.
- A route reassigned to another driver during the day: from then on only the new driver can upload for it.
- A route deactivated during the day: uploads for it answer 404 (and it also leaves the settlement screen).
- Upload at 23:59:59 vs 00:00:00 Argentina time: the date follows the server clock at reception.
- Aguas answers 2xx with an unexpected or empty body: success (FR-5).
- Aguas answers 3xx: 502, the redirect is not followed (FR-16, FR-17).
- Aguas answers slowly and the app gives up first: Aguas may still have received the photo; the driver may
  resend it (accepted duplicate, see above).
- A file declared as JPEG/PNG whose content is not an image (e.g. a renamed PDF): 415.
- A real PNG declared as JPEG, or the other way around: 415.
- A real JPEG declared as `application/octet-stream` or with no type: accepted, sent as `image/jpeg`.
- An empty file (0 bytes): 415.
- An image of exactly 10,485,760 bytes: accepted; one byte more: 413.
- A request over 256 MB: 413 with the same message as an image over 10 MB when the container lets LOOP
  answer; otherwise the connection may be closed (FR-11a, best effort).
- A client that sends its own `fecha` part, or any other unknown part: ignored.
- Two `routeId` parts, or two `image` parts: 400.
- `Content-Type: multipart/form-data` with a truncated or garbled body (e.g. missing or wrong boundary):
  400 with the FR-10 message (FR-10a); the same request to another endpoint keeps its current answer.

## Assumptions
- Aguas' success response body is unknown; any 2xx means success.
- Aguas' own size limit is unknown; photos arrive compressed from the phone, so they are expected to be
  well under 10 MB. If Aguas rejects a photo for size, it is reported as an Aguas failure (FR-16).
- Aguas' receipt-upload operation requires no authentication, as with the old front.
- Aguas accepts the `imagen` filename format of the Contract (the old front's filename is not known).

## Out of scope
- The app screen itself (camera/gallery picker, queue, per-photo retry UI): covered by the app work on
  "Mi rendición".
- Distinguishing checks from withholdings, or any metadata beyond route number and date.
- Listing, viewing or deleting photos already sent.
- Uploading for a past or future day.
- Automatic retries or an outbox for failed uploads.
- Image compression or resizing in the backend.
- Access for controllers, supervisors, admins or read-only roles.

## Completion criteria
- Every FR with a green test (`./mvnw test`), with Aguas replaced by a test double: the assistant never
  sends data to the real Aguas.
- Manual check on homologation (which holds real production data), done by the user with a real driver
  and a real receipt once the APK exists: the photo appears in Aguas with the right route number and date.

## Open questions
None. Answered by the user (2026-11-21, chat): status codes 400 / 415 / 413; magic bytes are checked
and a mismatch is 415; Aguas timeout 15 seconds total per photo. QA review resolutions approved by the
user the same day (contract, 204, error precedence, allowed types, size boundary, Aguas failure rules,
dedicated non-logging client, `imagen` filename, Spanish messages, tests, completion check).
