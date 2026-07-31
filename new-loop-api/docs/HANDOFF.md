# Handoff técnico — LOOP Backend

Documento de traspaso para quien continúe manteniendo este backend. Cubre arquitectura, despliegue,
integraciones externas y los problemas ya resueltos (para no volver a perder tiempo en ellos).

Para el detalle de cada endpoint/feature ya existe documentación específica en `docs/` — este documento
es el mapa general y el que junta el conocimiento que **no** está escrito en ningún otro lado (decisiones,
gotchas de despliegue, historial de bugs de integración).

---

## 1. Qué es LOOP

Backend para controlar la mercadería que sale y vuelve en los camiones de reparto.

- Antes de salir un camión, se carga un **control de salida (EXIT)** con los productos que lleva.
- Cuando vuelve, se carga un **control de entrada (ENTRY)** con lo que trajo, y el sistema calcula diferencias.
- El repartidor debe aprobar el control.
- Los controles de salida se envían automáticamente a un sistema externo llamado **Aguas** (el proveedor
  del agua envasada), que genera el remito oficial.
- Además hay gestión de productos, sucursales, rutas/camiones, pedidos de descartables (`orders`),
  movimientos de dispensers, ubicación GPS de camiones (Powerfleet) y distribución de la APK mobile.

Roles: `ADMIN`, `SUPERVISOR`, `CONTROLADOR`, `REPARTIDOR`, `PICKER`, `CARGADOR_DISPENSERS`.

---

## 2. Stack y arquitectura

- Java 21, Spring Boot, Maven, PostgreSQL, Flyway, Spring Security + JWT, OpenFeign, Lombok, OpenPDF.
- Organización **por módulo de negocio**, no por capa técnica. Cada módulo bajo
  `src/main/java/com/loop/new_loop_api/` sigue: `controller/ service/ repository/ entity/ dto/`.

Módulos actuales:

```
auth/           login, JWT
users/          usuarios, roles
branches/       sucursales
products/       catálogo de productos (retornable/descartable)
routes/         rutas/camiones
stockcontrols/  controles de entrada/salida, items, remito PDF
orders/         pedidos de productos descartables (picking)
dispensers/     movimientos de dispensers (integra con Odoo)
appupdate/      distribución de versiones de la APK mobile
audit/          auditoría de acciones sensibles
integrations/   clientes a sistemas externos (ver sección 4)
  common/       IntegrationLog, entidades/servicios compartidos
  aguas/        integración con Aguas (envío de controles)
  odoo/         integración con Odoo (ingreso de dispensers a reparar)
  powerFleet/   integración con Powerfleet (ubicación GPS)
fleet/          expone GET /fleet/location/{patente} usando integrations/powerFleet
common/         config, exception handler, response envelope (ApiResponse), seguridad
```

Convenciones a mantener (están en `CLAUDE.md`, leelo antes de tocar nada — el asistente de IA del repo
lo usa como fuente de verdad del proyecto):

- Controladores finos, lógica de negocio en el `*ServiceImpl`.
- Nunca exponer entidades JPA directamente — siempre DTOs de response.
- Toda integración externa pasa por `integrations/`, nunca se llama a un sistema externo desde un
  controller o directamente desde el service de negocio.
- Guardar localmente primero, después mandar a sistemas externos (patrón `ApplicationEventPublisher`
  + `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`, ver `stockcontrols/event/` y
  `integrations/aguas/listener/`).
- Todo intento de integración externa queda registrado en `integration_logs` vía `IntegrationLogRepository`.
- Cambios sensibles se auditan con `AuditService.register(...)`.

---

## 3. Cómo levantar el proyecto en local

1. Copiar `.env.example` a `.env` (en la raíz de `new-loop-api/`) y completar `DB_URL`, `DB_USERNAME`,
   `DB_PASSWORD`, `JWT_SECRET`. El resto de las variables tienen default funcional para desarrollo.
2. Tener PostgreSQL corriendo y la base creada (Flyway crea las tablas solo, no la base).
3. `./mvnw spring-boot:run` (o correr `NewLoopApiApplication` desde el IDE).
4. Sale por el puerto de `PORT` en `.env` (o 8080 si no está seteado).
5. Health check: `GET /health` (público, sin token).

No hay usuarios seed. El primer usuario admin se crea a mano contra el endpoint abierto `POST /users`
(ver sección 6 — **hoy este endpoint no requiere autenticación**, es el mecanismo actual para bootstrapear).

---

## 4. Integraciones externas — lo importante que no está en el código

### 4.1 Aguas (envío de controles de stock)

- Dos operaciones, **mismo host**, dos paths distintos:
  - `POST {base}/api/aguas/products/out` — control de salida (EXIT)
  - `POST {base}/api/aguas/products/in` — control de entrada (ENTRY)
- Host actual: `http://192.168.0.251:8083` (property `integrations.aguas.base-url`, env var `AGUAS_BASE_URL`).
- **Historial de cambios de host**: este sistema cambió de IP más de una vez
  (`192.168.0.12` → `192.168.0.251:8083`). Si vuelve a fallar con 404 genérico, lo primero a revisar
  es si Aguas cambió de host/puerto de nuevo — no asumir que es un bug nuestro.
- **Trampa ya pisada una vez**: `docker-compose.yml` tiene su propio default para `AGUAS_BASE_URL`
  (`${AGUAS_BASE_URL:-http://...}`) separado del default de `application.properties`. Si el `.env` del
  servidor no define la variable, gana el default de `docker-compose.yml` — y si ese quedó desactualizado,
  la app le pega a la IP vieja sin ningún error de config, solo un 404 en runtime. **Si cambia la URL de
  Aguas, actualizar en los dos lugares** (o mejor, definir `AGUAS_BASE_URL` explícitamente en el `.env`
  del servidor para no depender de ningún default).
- **El 404 de Aguas es ambiguo**: el backend de Aguas es Laravel. Su página de error 404 default es
  idéntica tanto para "la ruta no existe" como para un `abort(404)` de negocio dentro del controller
  (por ejemplo, sucursal con `codigo` que no matchea ninguna empresa registrada en Aguas). Ya pasó que
  un 404 que parecía "URL mal armada" en realidad era `sucursal.codigo` mal cargado en nuestra base
  (tiene que ser el código real de empresa en Aguas, ej. `"0106"`, no un código interno arbitrario).
  Antes de sospechar de la URL, revisar los datos de la sucursal/ruta involucrada.
- `route.code` se manda como `delivery_id` (entero) — **tiene que ser numérico**. Rutas con códigos tipo
  `"rto1"` rompen el envío con `422 delivery_id is required` (el parseo a entero falla en silencio y
  manda `null`). `UpdateRouteRequest` sí permite editar `code` — si el frontend "no deja" cambiarlo,
  no es un bug de permisos, es que antes faltaba ese campo en el DTO (ya arreglado).
- Reintentos automáticos: `AguasRetryScheduler`, hasta `integrations.aguas.max-retries` (5) cada
  `integrations.aguas.retry-interval-ms` (5 min). Ver estado de un envío en
  `GET /integration-logs?entityId={stockControlId}`.
- **Logging full de request/response** (headers incluidos) ya está habilitado a nivel `DEBUG` para
  `AguasClient` (`logging.level...AguasClient=DEBUG` en `application.properties`). Para verlo en el
  servidor: `docker compose logs -f app | grep -i aguas`.
- La respuesta exitosa de OUT trae `{"data":{"formulario":"...","nroremito":...}}`, que se guarda en
  `stock_controls.aguas_formulario` / `aguas_nro_remito` — usado para generar el remito PDF
  (`GET /stock-controls/{id}/remito`, ver `docs/STOCK_CONTROLS.md`).

### 4.2 Aguas — equipment/dispensers (movimientos de dispensers)

Integración **separada** de la anterior, otro host: `integrations.aguas-equipment.base-url`
(`AGUAS_EQUIPMENT_BASE_URL`, default `http://192.168.0.58`). No confundir ambas — comparten proveedor
pero son sistemas/hosts distintos.

### 4.3 Odoo

Usado solo para dar de alta el ingreso de un dispenser a reparar (movimientos tipo `UNLOAD`).
`integrations.odoo.base-url` / `integrations.odoo.api-key`. Implementado con `RestClient` (no Feign) —
Feign estaba mal codificando UTF-8/Content-Type contra esta API en particular, no vale la pena volver
a Feign acá sin probar bien antes.

### 4.4 Powerfleet (ubicación GPS)

`integrations.powerfleet.*`. Login con usuario/password devuelve un token con expiración; el token se
cachea en memoria (`FleetLocationServiceImpl`, `synchronized`, con margen de 5 min antes de vencer) para
no pedirlo en cada consulta. Expuesto vía `GET /fleet/location/{patente}`. Ver `docs/FLEET_LOCATION.md`.

---

## 5. Despliegue (servidor de homologación)

Estructura en el servidor (usuario `gwinazki@lamp`):

```
apps/loop/homologacion/
  app/apk-storage/     ← APKs publicadas (bind mount, fuera del código)
  backend/
    new_loop_backend/
      new-loop-api/    ← acá vive docker-compose.yml, acá se corren los comandos
  frontend/
  logs/
```

Deploy estándar:

```bash
cd apps/loop/homologacion/backend/new_loop_backend/new-loop-api
git pull
docker compose up -d --build --force-recreate
```

- `--build` es necesario si cambió código Java o el `Dockerfile` — `--force-recreate` solo no
  reconstruye el jar, solo recrea el contenedor con la imagen que ya existía (trampa fácil: parece
  que desplegaste pero la app sigue corriendo el jar viejo).
- El contenedor corre con `network_mode: host` (necesario para llegar a la base de datos y a Aguas,
  que están en la LAN — con la red bridge default de Docker daba `NoRouteToHostException`).
- El contenedor corre como usuario **no-root** (`USER app` en el Dockerfile). Cualquier volumen bind-mount
  nuevo (como `apk-storage`) tiene que tener permisos de escritura para ese usuario, si no falla con un
  `AccessDeniedException` que en el log de la app aparece como un mensaje sin descripción, solo el path
  del archivo. Fix rápido: `chmod 777` en la carpeta del host.
- Logs: `docker compose logs -f app` (el nombre de servicio en `docker-compose.yml` es `app`, el
  `container_name` es `loop-api-app` — para `docker compose logs`/`up`/`down` usar el nombre de
  servicio, no el `container_name`).
- Variables de entorno del contenedor: `docker-compose.yml` las toma de `.env` con un default de
  respaldo (`${VAR:-default}`). Ver sección 4.1 sobre el riesgo de que esos defaults queden desactualizados.

Timezone: la app fija `America/Argentina/Buenos_Aires` explícitamente en `main()`
(`NewLoopApiApplication.java`) porque el Postgres del servidor no reconoce el alias `America/Buenos_Aires`
que resuelve el JVM por default (falla el arranque con `FATAL: invalid value for parameter "TimeZone"`
si se saca eso).

---

## 6. Riesgos conocidos / deuda técnica (importante leer esto)

- **Seguridad: no hay ningún endpoint protegido.** `SecurityConfig` tiene `.anyRequest().permitAll()`.
  Todo el código de JWT/roles (`JwtAuthenticationFilter`, `AuthenticatedUser`, `@PreAuthorize`) existe
  pero no está aplicado a nivel de autorización real todavía. Esto es especialmente sensible en
  `POST /users` (cualquiera puede crear usuarios admin) y `POST /app/version` (cualquiera puede subir
  un APK). Esto se dejó así deliberadamente durante el desarrollo para no trabar el frontend/mobile
  mientras se armaban las features — **es lo primero que habría que cerrar** antes de un uso productivo real.
- **CI/CD**: no hay pipeline de deploy automático configurado. Hoy el deploy es manual (`git pull` +
  `docker compose up --build` a mano en el servidor, sección 5).
- **Rotar credenciales**: dado que alguien del equipo se está yendo, correspondería rotar
  `JWT_SECRET`, la password de la base, y las credenciales de Powerfleet/Aguas si las conocía, y
  revisar accesos SSH al servidor.

---

## 7. Troubleshooting rápido (casos ya vistos)

| Síntoma | Causa real | Ver |
|---|---|---|
| `NoRouteToHostException` a la base/Aguas desde Docker | Red bridge default no llega a la LAN | usar `network_mode: host` |
| `FATAL: invalid value for parameter "TimeZone"` | Postgres no reconoce `America/Buenos_Aires` | fijar `America/Argentina/Buenos_Aires` en `main()` |
| `Could not store the APK file: /app/apk-storage/...` | Carpeta bind-mount sin permiso de escritura para el usuario no-root del contenedor | `chmod`/`chown` la carpeta en el host |
| `413 Request Entity Too Large` (HTML con branding nginx) | Límite de nginx (`client_max_body_size`), no de Spring | agregar `client_max_body_size` en la config de nginx, además de (no en vez de) los límites de multipart de Spring |
| 404 HTML de Laravel al mandar a Aguas | Puede ser ruta inexistente, host desactualizado, **o** dato de negocio inválido (código de sucursal) | ver sección 4.1 |
| Frontend "no deja" editar un campo | Revisar si el DTO de `Update*Request` realmente tiene ese campo antes de asumir que es un bug de permisos | pasó con `code` en `UpdateRouteRequest` |

---

## 8. Documentación específica por feature

Todo en `docs/`:

- `AUTH.md` — login/JWT
- `MOBILE_GUIDE.md` — guía general para la app mobile
- `DRIVER_FLOW.md`, `ROUTES_MOBILE.md`, `PRODUCTS_MOBILE.md` — flujos del repartidor
- `STOCK_CONTROLS.md` — controles de entrada/salida, remito PDF
- `PENDING_ARRIVALS.md` — resumen de camiones pendientes de llegar
- `ORDERS_ADMIN.md` / `ORDERS_MOBILE.md` — pedidos de descartables
- `DISPENSERS.md` — movimientos de dispensers e integración con Odoo
- `FLEET_LOCATION.md` — ubicación GPS vía Powerfleet
- `APP_VERSION.md` — distribución de la APK mobile

---

## 9. Accesos a completar por el equipo

Este documento no incluye credenciales. Antes de que se vaya la persona saliente, confirmar que el
equipo tiene (y rotar lo que corresponda):

- Acceso SSH al servidor de homologación (`gwinazki@lamp`).
- Acceso al repo de GitHub (`gonzalo-wi/new_loop_backend`).
- El `.env` real del servidor (no versionado — vive solo en el servidor).
- Credenciales de Aguas y Powerfleet (hoy documentadas como default en `application.properties` /
  `.env.example` — considerar sacarlas de ahí y rotarlas).
