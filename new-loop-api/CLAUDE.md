Loop — Reglas del proyecto

Backend Java de gestión y control de mercadería. Este archivo se carga automáticamente en cada sesión y en cada subagente — acá van las reglas que aplican siempre, sin importar quién esté trabajando.

Estándares de código (aplican a todo el código Java del proyecto)
Seguir la arquitectura ya existente en el repo. No introducir un patrón o capa nueva sin que se pida explícitamente.
Nada de valores mágicos: usar constantes, enums o config.
Principios SOLID.
Código limpio: métodos cortos, nombres descriptivos, responsabilidad única por clase.
Excepciones tipadas del dominio, no genéricas.
Pensar en escalabilidad: evitar acoplar lógica de negocio a detalles de infraestructura.
Flujo de trabajo para nuevas funcionalidades o cambios

Cuando se pida implementar algo en el backend Java, seguir este pipeline en orden, usando los subagentes correspondientes:

loop-java-dev — implementa la funcionalidad.
loop-java-tester — escribe y corre los tests, valida que todo pase en verde.
loop-java-reviewer — revisa el código ya testeado y da el veredicto (bloqueantes / sugerencias / ok para commitear).
Solo si el reviewer dio OK: mostrarle al usuario en la conversación principal un resumen de qué se hizo, el resultado de los tests, y un mensaje de commit propuesto. Esperar confirmación explícita del usuario en el chat antes de correr git add / git commit. Nunca commitear sin esa aprobación explícita, incluso si el review salió limpio.
Después del commit (o al cerrar la tarea), agregar una entrada en CHANGELOG.md describiendo el cambio (ver formato abajo).

Si el reviewer marca bloqueantes, volver a loop-java-dev para corregir y repetir el ciclo desde el paso 2 — no saltear el testing después de una corrección.

Changelog

Cada funcionalidad, fix o cambio relevante que se commitea debe sumar una entrada en CHANGELOG.md, sección [Unreleased], categorizada en Added / Changed / Fixed / Removed, con una descripción breve y clara (no el mensaje de commit textual, una frase entendible para alguien que no vio el código).
## LOOP system map (all three repos)

LOOP is split across three repos on this machine. Before searching, use this map to pick the right one.

| Repo | Path | What lives there |
|---|---|---|
| `new-loop-api` | `C:\Users\gwinazki.ELJUMILLANO\Desktop\new-loop-api\new-loop-api` (git root is the parent folder) | Spring Boot backend: REST API, PDFs (remito), Aguas / Odoo / Powerfleet integrations, audit log, APK hosting (`/app/version`) |
| `new-loop-front` | `C:\Users\gwinazki.ELJUMILLANO\Desktop\new-loop-front` | React admin panel (Vite). API contract in `api.md` |
| `react-nati` | `C:\Users\gwinazki.ELJUMILLANO\Desktop\react-nati` | Expo / React Native app for warehouse operators and drivers. Release runbook in `docs/RELEASE.md` |

Where common things live:
- **Exit remito PDF** — built by the backend (`stockcontrols/pdf/RemitoPdfGenerator.java`, A5 page). Front and app only download it.
- **Stock control dates** — EXIT `controlDate` = next delivery day (Sunday skipped), ENTRY = today. Backend `resolveControlDate`, app `computeDefaultDate`. `GET /stock-controls`: `from`/`to` filter `controlDate`; `createdFrom`/`createdTo` filter the creation day (what the panel uses for "today").
- **Corrections of controls sent to Aguas** — `POST /stock-controls/{id}/correct`: ENTRY only, roles SUPERVISOR and ADMIN, reason required; re-sends to Aguas.
- **Truck GPS** — backend `fleet/` + `integrations/powerFleet/`; front `deliveries/components/TruckLocationModal.tsx`; app `app/(tabs)/fleet.tsx` (opens maps via `geo:` on Android).
- **App auto-update** — backend `/app/version` + `/app/download/{file}`; app `features/app-update/`; panel `features/app-version/`.

Checks per repo:
- Backend: `./mvnw test` (Git Bash). Postgres-backed tests are skipped unless `LOOP_TEST_DB_URL=jdbc:postgresql://localhost...`. If JUnit reports "ClassSelector ... resolution failed", run `./mvnw clean test` (stale `target/`).
- Front: `npx tsc --noEmit`. `npm run lint` is currently broken (ESLint 9 without `eslint.config.js`); there is no unit-test runner yet.
- App: `npx jest` and `npx tsc --noEmit` (lint has the same ESLint 9 issue).

Server and operations (192.168.0.42):
- Backend API on port 8095, Docker Compose service `app`, container `loop-api-app` (`network_mode: host`). Compose dir: `/opt/apps/loop/homologacion/backend/new_loop_backend/new-loop-api`. The front (dev proxy and build) and the APK (`eas.json`) both point to `http://192.168.0.42:8095`.
- Secrets (DB, JWT, Aguas, `POWERFLEET_USERNAME` / `POWERFLEET_PASSWORD`) live in the `.env` next to that compose file, never in the repo. After editing it: `docker compose up -d --force-recreate app` (`restart` does not reload `.env`). Code changes need `docker compose up -d --build app`.
- Check an env value without printing it: `docker exec loop-api-app sh -c 'printf %s "$VAR" | wc -c'`.
- Logs: `docker logs loop-api-app`. External integration failures are logged with their cause (e.g. `Powerfleet location lookup failed`).
- Users are in Argentina (UTC-3): never use `toISOString()` for calendar dates (it rolls to tomorrow from 21:00).
