# Autenticación

## Login

Autentica un usuario y devuelve un token JWT para usar en el resto de las llamadas.

```
POST /auth/login
```

No requiere autorización.

---

### Request

**Headers**
```
Content-Type: application/json
```

**Body**
```json
{
  "username": "juan.perez",
  "password": "mipassword"
}
```

| Campo      | Tipo   | Requerido | Descripción          |
|------------|--------|-----------|----------------------|
| `username` | string | Sí        | Nombre de usuario    |
| `password` | string | Sí        | Contraseña           |

---

### Response `200 OK`

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "name": "Juan Pérez",
  "username": "juan.perez",
  "role": "REPARTIDOR"
}
```

| Campo      | Tipo   | Descripción                              |
|------------|--------|------------------------------------------|
| `token`    | string | JWT para incluir en las siguientes llamadas |
| `type`     | string | Siempre `"Bearer"`                       |
| `id`       | UUID   | ID del usuario autenticado               |
| `name`     | string | Nombre completo del usuario              |
| `username` | string | Nombre de usuario                        |
| `role`     | string | Rol del usuario (ver tabla de roles)     |

---

### Roles posibles

| Rol                  | Descripción                        |
|----------------------|------------------------------------|
| `ADMIN`              | Administrador del sistema          |
| `SUPERVISOR`         | Supervisión y reportes             |
| `CONTROLADOR`        | Operario de planta: controles de stock, pedidos descartables y movimientos de dispensers. Requiere sucursal |
| `REPARTIDOR`         | Aprueba o rechaza controles        |
| `VISUALIZADOR`       | Solo lectura desde el panel: ve los controles de entrada y salida de su sucursal (listado, detalle y pendientes de llegada). Puede ver e imprimir los remitos de su sucursal (uno o en lote, y quedan marcados como impresos). No puede crear, editar, aprobar, rechazar ni corregir. Requiere sucursal |
| `CLARKISTA`          | Solo lectura desde la app (tablet): consulta el pronóstico de carga de un reparto de su sucursal (`GET /load-forecast`). Requiere sucursal |

> `VISUALIZADOR` y `CLARKISTA` tienen una lista cerrada de endpoints permitidos (`ReadOnlyRoleAccessPolicy`). Cualquier otro endpoint, incluidos los que se agreguen en el futuro, les responde `403` hasta que se los habilite explícitamente. El login y el chequeo de versión de la app siguen siendo públicos.

> `PICKER` y `CARGADOR_DISPENSERS` ya no existen: se unificaron en `CONTROLADOR`. Si se envía uno de ellos (u otro rol desconocido) al crear o editar un usuario, el backend responde `400`.

---

### Cómo usar el token

En todas las llamadas siguientes incluir el token en el header:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Vencimiento y sesión

- El token **vence a la medianoche siguiente, hora de Argentina** (claim `exp`). Todos se vuelven a loguear al empezar el día. Si alguien entra a las 23:50, el token le dura 10 minutos.
- **Todos los endpoints piden token**, salvo: `POST /auth/login`, `GET /health`, `GET /app/version` y `GET /app/download/{file}` (la app busca actualizaciones antes de loguearse).
- Sin token, o con uno vencido, inválido o de un usuario desactivado → **`401 Unauthorized`**. El panel y la app cierran la sesión y vuelven al login.
- Logueado pero sin el rol necesario → **`403 Forbidden`**. Ejemplo: `POST /app/version` (subir un APK) es solo para `ADMIN`.
- En los endpoints públicos un token vencido no molesta: se ignora (un token viejo guardado en el teléfono no bloquea el login).
- La API es **stateless**: no crea sesiones HTTP ni cookies (`JSESSIONID`), solo usa el token.
- Swagger UI (`/swagger-ui.html`, `/v3/api-docs`) también pide token, así que desde el navegador responde 401.

```json
{
  "timestamp": "2026-10-30T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication required: the session is missing, invalid or expired",
  "path": "/users"
}
```

---

### Errores

**`401 Unauthorized`** — usuario o contraseña incorrectos

```json
{
  "timestamp": "2026-06-24T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Bad credentials",
  "path": "/auth/login"
}
```

**`403 Forbidden`** — usuario desactivado

```json
{
  "timestamp": "2026-06-24T10:30:00",
  "status": 403,
  "error": "Forbidden",
  "message": "User account is disabled",
  "path": "/auth/login"
}
```

**`400 Bad Request`** — campos vacíos

```json
{
  "timestamp": "2026-06-24T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "username: Username is required",
  "path": "/auth/login"
}
```
