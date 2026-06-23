# LOOP API — Documentación para Frontend

**Base URL (dev):** `http://localhost:8080`

---

## Estructura de respuestas

### Respuesta exitosa (la mayoría de endpoints)

```json
{
  "data": { ... },
  "message": "..."
}
```

### Respuesta paginada

```json
{
  "data": {
    "content": [ ... ],
    "page": {
      "size": 20,
      "number": 0,
      "totalElements": 42,
      "totalPages": 3
    }
  },
  "message": "..."
}
```

### Error

```json
{
  "timestamp": "2026-06-23T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Branch not found with id: abc-123",
  "path": "/branches/abc-123"
}
```

### Parámetros de paginación (disponibles en todos los GET de lista)

| Parámetro | Tipo   | Default    | Ejemplo         |
|-----------|--------|------------|-----------------|
| `page`    | int    | `0`        | `?page=1`       |
| `size`    | int    | `20`       | `?size=10`      |
| `sort`    | string | `name,asc` | `?sort=code,desc` |

---

## Auth

### POST /auth/login

Autentica un usuario y devuelve un JWT.

**Body:**

```json
{
  "username": "admin",
  "password": "secreto123"
}
```

| Campo      | Tipo   | Requerido |
|------------|--------|-----------|
| `username` | string | Si        |
| `password` | string | Si        |

**Respuesta 200:**

> Esta respuesta NO está envuelta en `ApiResponse`, viene directamente.

```json
{
  "token": "eyJhbGci...",
  "type": "Bearer",
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Juan Pérez",
  "username": "admin",
  "role": "ADMIN"
}
```

**Errores:**

| Status | Situación                         | `message`                      |
|--------|-----------------------------------|-------------------------------|
| 400    | Campo vacío                       | `"Username is required"`      |
| 401    | Usuario o contraseña incorrectos  | `"Invalid username or password"` |
| 401    | Usuario desactivado               | `"User account is inactive"`  |

---

## Branches (Sucursales)

Base path: `/branches`

### POST /branches

Crea una nueva sucursal.

**Body:**

```json
{
  "name": "Ciudadela",
  "code": "CIU",
  "address": "Av. Rivadavia 1234",
  "locality": "Ciudadela",
  "province": "Buenos Aires",
  "cuit": "30-12345678-9",
  "vatCondition": "Responsable Inscripto"
}
```

| Campo          | Tipo   | Requerido | Max   |
|----------------|--------|-----------|-------|
| `name`         | string | Si        | 150   |
| `code`         | string | Si        | 50    |
| `address`      | string | No        | 255   |
| `locality`     | string | No        | 100   |
| `province`     | string | No        | 100   |
| `cuit`         | string | No        | 20    |
| `vatCondition` | string | No        | 50    |

**Respuesta 201:**

```json
{
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Ciudadela",
    "code": "CIU",
    "address": "Av. Rivadavia 1234",
    "locality": "Ciudadela",
    "province": "Buenos Aires",
    "cuit": "30-12345678-9",
    "vatCondition": "Responsable Inscripto",
    "active": true,
    "createdAt": "2026-06-23T10:00:00",
    "updatedAt": "2026-06-23T10:00:00"
  },
  "message": "Branch created successfully"
}
```

**Errores:**

| Status | Situación                | `message`                              |
|--------|--------------------------|----------------------------------------|
| 400    | Campos inválidos         | Lista de errores de validación         |
| 409    | `code` ya existe         | `"Branch code already exists: CIU"`   |

---

### GET /branches

Lista sucursales paginadas.

**Query params:** `page`, `size`, `sort` (ver tabla arriba). Default sort: `name,asc`.

**Respuesta 200:**

```json
{
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Ciudadela",
        "code": "CIU",
        "address": "Av. Rivadavia 1234",
        "locality": "Ciudadela",
        "province": "Buenos Aires",
        "cuit": "30-12345678-9",
        "vatCondition": "Responsable Inscripto",
        "active": true,
        "createdAt": "2026-06-23T10:00:00",
        "updatedAt": "2026-06-23T10:00:00"
      }
    ],
    "page": {
      "size": 20,
      "number": 0,
      "totalElements": 3,
      "totalPages": 1
    }
  },
  "message": "Branches retrieved successfully"
}
```

---

### GET /branches/{id}

Obtiene una sucursal por ID.

**Respuesta 200:** mismo objeto `BranchResponse` dentro de `data`.

**Errores:**

| Status | Situación         | `message`                                        |
|--------|-------------------|--------------------------------------------------|
| 404    | No encontrada     | `"Branch not found with id: <id>"`              |

---

### PATCH /branches/{id}

Actualiza una sucursal. Solo se actualizan los campos enviados (parcial).

**Body (todos opcionales):**

```json
{
  "name": "Ciudadela Centro",
  "address": "Av. Rivadavia 5678",
  "locality": "Ciudadela",
  "province": "Buenos Aires",
  "cuit": "30-12345678-9",
  "vatCondition": "Monotributista"
}
```

> `code` no se puede modificar.

**Respuesta 200:** objeto `BranchResponse` actualizado dentro de `data`.

**Errores:**

| Status | Situación     | `message`                          |
|--------|--------------|------------------------------------|
| 400    | Validación   | Detalle del campo inválido         |
| 404    | No encontrada | `"Branch not found with id: <id>"` |

---

### PATCH /branches/{id}/deactivate

Desactiva una sucursal (la pone en `active: false`).

**Body:** ninguno.

**Respuesta 200:**

```json
{
  "data": null,
  "message": "Branch deactivated successfully"
}
```

**Errores:**

| Status | Situación     | `message`                          |
|--------|--------------|------------------------------------|
| 404    | No encontrada | `"Branch not found with id: <id>"` |

---

### PATCH /branches/{id}/activate

Reactiva una sucursal (la pone en `active: true`).

**Body:** ninguno.

**Respuesta 200:**

```json
{
  "data": null,
  "message": "Branch activated successfully"
}
```

**Errores:**

| Status | Situación     | `message`                          |
|--------|--------------|------------------------------------|
| 404    | No encontrada | `"Branch not found with id: <id>"` |

---

## Products (Productos)

Base path: `/products`

### POST /products

Crea un nuevo producto.

**Body:**

```json
{
  "code": "BID-20",
  "name": "Bidón 20L",
  "displayOrder": 1,
  "description": "Bidón retornable de 20 litros",
  "type": "RETORNABLE",
  "unit": "unidad",
  "packQuantity": 1
}
```

| Campo          | Tipo    | Requerido | Notas                              |
|----------------|---------|-----------|------------------------------------|
| `code`         | string  | Si        | Único, máx 50                      |
| `name`         | string  | Si        | Máx 150                            |
| `displayOrder` | integer | Si        | Mayor a 0                          |
| `description`  | string  | No        | Máx 500                            |
| `type`         | enum    | Si        | `RETORNABLE` o `DESCARTABLE`       |
| `unit`         | string  | Si        | Máx 50 (ej: `"unidad"`, `"pack"`) |
| `packQuantity` | integer | No        | 0 o positivo                       |

**Respuesta 201:**

```json
{
  "data": {
    "id": "660f9500-f30c-52e5-b827-557766551111",
    "code": "BID-20",
    "name": "Bidón 20L",
    "displayOrder": 1,
    "description": "Bidón retornable de 20 litros",
    "type": "RETORNABLE",
    "unit": "unidad",
    "packQuantity": 1,
    "active": true,
    "createdAt": "2026-06-23T10:00:00",
    "updatedAt": "2026-06-23T10:00:00"
  },
  "message": "Product created successfully"
}
```

**Errores:**

| Status | Situación        | `message`                                 |
|--------|-----------------|-------------------------------------------|
| 400    | Campos inválidos | Lista de errores de validación            |
| 409    | `code` ya existe | `"Product code already exists: BID-20"` |

---

### GET /products

Lista productos paginados.

**Query params:** `page`, `size`, `sort`. Default sort: `displayOrder,asc`.

**Respuesta 200:**

```json
{
  "data": {
    "content": [
      {
        "id": "660f9500-f30c-52e5-b827-557766551111",
        "code": "BID-20",
        "name": "Bidón 20L",
        "displayOrder": 1,
        "description": "Bidón retornable de 20 litros",
        "type": "RETORNABLE",
        "unit": "unidad",
        "packQuantity": 1,
        "active": true,
        "createdAt": "2026-06-23T10:00:00",
        "updatedAt": "2026-06-23T10:00:00"
      }
    ],
    "page": {
      "size": 20,
      "number": 0,
      "totalElements": 15,
      "totalPages": 1
    }
  },
  "message": "Products retrieved successfully"
}
```

---

### GET /products/{id}

Obtiene un producto por ID.

**Respuesta 200:** mismo objeto `ProductResponse` dentro de `data`.

**Errores:**

| Status | Situación     | `message`                             |
|--------|--------------|---------------------------------------|
| 404    | No encontrado | `"Product not found with id: <id>"` |

---

### PATCH /products/{id}

Actualiza un producto. Solo se actualizan los campos enviados (parcial).

**Body (todos opcionales):**

```json
{
  "name": "Bidón 20L Retornable",
  "displayOrder": 2,
  "description": "Nueva descripción",
  "type": "RETORNABLE",
  "unit": "pack",
  "packQuantity": 6
}
```

> `code` no se puede modificar.

**Respuesta 200:** objeto `ProductResponse` actualizado dentro de `data`.

**Errores:**

| Status | Situación     | `message`                              |
|--------|--------------|-----------------------------------------|
| 400    | Validación   | Detalle del campo inválido              |
| 404    | No encontrado | `"Product not found with id: <id>"`  |

---

### PATCH /products/{id}/deactivate

Desactiva un producto.

**Body:** ninguno.

**Respuesta 200:**

```json
{
  "data": null,
  "message": "Product deactivated successfully"
}
```

**Errores:**

| Status | Situación     | `message`                              |
|--------|--------------|-----------------------------------------|
| 404    | No encontrado | `"Product not found with id: <id>"`  |

---

### PATCH /products/{id}/activate

Reactiva un producto.

**Body:** ninguno.

**Respuesta 200:**

```json
{
  "data": null,
  "message": "Product activated successfully"
}
```

---

## Users (Usuarios)

Base path: `/users`

### POST /users

Crea un nuevo usuario. La contraseña se guarda hasheada (BCrypt).

**Body:**

```json
{
  "name": "Juan Pérez",
  "username": "jperez",
  "password": "miPassword123",
  "role": "CONTROLADOR"
}
```

| Campo      | Tipo   | Requerido | Notas                   |
|------------|--------|-----------|-------------------------|
| `name`     | string | Si        | Máx 150                 |
| `username` | string | Si        | Único, máx 100          |
| `password` | string | Si        | Mínimo 8 caracteres     |
| `role`     | enum   | Si        | Ver valores más abajo   |

**Roles disponibles:**

| Valor                | Descripción             |
|----------------------|-------------------------|
| `ADMIN`              | Administrador           |
| `CONTROLADOR`        | Controlador             |
| `REPARTIDOR`         | Repartidor              |
| `PICKER`             | Picker                  |
| `CARGADOR_DISPENSERS`| Cargador de dispensers  |
| `SUPERVISOR`         | Supervisor / Auditor    |

**Respuesta 201:**

```json
{
  "data": {
    "id": "770a0600-g41d-63f6-c938-668877662222",
    "name": "Juan Pérez",
    "username": "jperez",
    "role": "CONTROLADOR",
    "active": true,
    "createdAt": "2026-06-23T10:00:00",
    "updatedAt": "2026-06-23T10:00:00"
  },
  "message": "User created successfully"
}
```

> La contraseña nunca se devuelve en ninguna respuesta.

**Errores:**

| Status | Situación          | `message`                                   |
|--------|--------------------|---------------------------------------------|
| 400    | Campos inválidos   | Lista de errores de validación              |
| 409    | `username` ya existe | `"Username already exists: jperez"`       |

---

### GET /users

Lista usuarios paginados.

**Query params:** `page`, `size`, `sort`. Default sort: `name,asc`.

**Respuesta 200:**

```json
{
  "data": {
    "content": [
      {
        "id": "770a0600-g41d-63f6-c938-668877662222",
        "name": "Juan Pérez",
        "username": "jperez",
        "role": "CONTROLADOR",
        "active": true,
        "createdAt": "2026-06-23T10:00:00",
        "updatedAt": "2026-06-23T10:00:00"
      }
    ],
    "page": {
      "size": 20,
      "number": 0,
      "totalElements": 8,
      "totalPages": 1
    }
  },
  "message": "Users retrieved successfully"
}
```

---

### GET /users/{id}

Obtiene un usuario por ID.

**Respuesta 200:** mismo objeto `UserResponse` dentro de `data`.

**Errores:**

| Status | Situación     | `message`                           |
|--------|--------------|-------------------------------------|
| 404    | No encontrado | `"User not found with id: <id>"`  |

---

### PATCH /users/{id}

Actualiza un usuario. Solo se actualizan los campos enviados (parcial).

**Body (todos opcionales):**

```json
{
  "name": "Juan Carlos Pérez",
  "password": "nuevoPassword456",
  "role": "SUPERVISOR"
}
```

> `username` no se puede modificar.

**Respuesta 200:** objeto `UserResponse` actualizado dentro de `data`.

**Errores:**

| Status | Situación     | `message`                            |
|--------|--------------|--------------------------------------|
| 400    | Validación   | Detalle del campo inválido           |
| 404    | No encontrado | `"User not found with id: <id>"`  |

---

### PATCH /users/{id}/deactivate

Desactiva un usuario. El usuario no podrá iniciar sesión mientras esté inactivo.

**Body:** ninguno.

**Respuesta 200:**

```json
{
  "data": null,
  "message": "User deactivated successfully"
}
```

**Errores:**

| Status | Situación     | `message`                            |
|--------|--------------|--------------------------------------|
| 404    | No encontrado | `"User not found with id: <id>"`  |

---

### PATCH /users/{id}/activate

Reactiva un usuario.

**Body:** ninguno.

**Respuesta 200:**

```json
{
  "data": null,
  "message": "User activated successfully"
}
```

---

## Audit Logs (Auditoría)

Base path: `/audit-logs`

El módulo de auditoría registra automáticamente toda acción de escritura en el sistema (crear, actualizar, activar, desactivar). Es de **solo lectura** desde el frontend — los registros los genera el backend.

> Los registros se conservan **7 días**. El sistema los elimina automáticamente a medianoche.

---

### GET /audit-logs

Lista registros de auditoría paginados con filtros opcionales.

**Query params:**

| Parámetro    | Tipo      | Requerido | Ejemplo                                    | Descripción                         |
|--------------|-----------|-----------|--------------------------------------------|-------------------------------------|
| `entityName` | string    | No        | `Branch`                                   | Filtrar por entidad                 |
| `action`     | string    | No        | `CREATE_BRANCH`                            | Filtrar por tipo de acción          |
| `entityId`   | UUID      | No        | `550e8400-e29b-41d4-a716-446655440000`     | Historial de un registro específico |
| `from`       | datetime  | No        | `2026-06-01T00:00:00`                      | Fecha/hora desde (inclusive)        |
| `to`         | datetime  | No        | `2026-06-30T23:59:59`                      | Fecha/hora hasta (inclusive)        |
| `page`       | int       | No        | `0`                                        | Página (default: `0`)               |
| `size`       | int       | No        | `50`                                       | Registros por página (default: `50`)|
| `sort`       | string    | No        | `createdAt,desc`                           | Orden (default: `createdAt,desc`)   |

**Acciones posibles en `action`:**

| Valor                | Entidad   |
|----------------------|-----------|
| `CREATE_BRANCH`      | `Branch`  |
| `UPDATE_BRANCH`      | `Branch`  |
| `DEACTIVATE_BRANCH`  | `Branch`  |
| `ACTIVATE_BRANCH`    | `Branch`  |
| `CREATE_PRODUCT`     | `Product` |
| `UPDATE_PRODUCT`     | `Product` |
| `DEACTIVATE_PRODUCT` | `Product` |
| `ACTIVATE_PRODUCT`   | `Product` |
| `CREATE_USER`        | `User`    |
| `UPDATE_USER`        | `User`    |
| `DEACTIVATE_USER`    | `User`    |
| `ACTIVATE_USER`      | `User`    |

**Respuesta 200:**

```json
{
  "data": {
    "content": [
      {
        "id": "880b1700-h52e-74g7-d049-779988773333",
        "userId": null,
        "userRole": null,
        "action": "CREATE_BRANCH",
        "entityName": "Branch",
        "entityId": "550e8400-e29b-41d4-a716-446655440000",
        "oldValue": null,
        "newValue": "{\"id\":\"550e8400...\",\"name\":\"Ciudadela\",\"code\":\"CIU\",\"active\":true,...}",
        "reason": null,
        "source": "ADMIN_WEB",
        "ipAddress": null,
        "createdAt": "2026-06-23T17:37:31"
      }
    ],
    "page": {
      "size": 50,
      "number": 0,
      "totalElements": 12,
      "totalPages": 1
    }
  },
  "message": "Audit logs retrieved successfully"
}
```

**Descripción de campos:**

| Campo        | Tipo      | Descripción                                                                 |
|--------------|-----------|-----------------------------------------------------------------------------|
| `id`         | UUID      | ID del registro de auditoría                                                |
| `userId`     | UUID      | ID del usuario que realizó la acción (`null` hasta que se implemente JWT)   |
| `userRole`   | string    | Rol del usuario (`null` hasta que se implemente JWT)                        |
| `action`     | string    | Tipo de acción realizada                                                    |
| `entityName` | string    | Nombre de la entidad afectada (`Branch`, `Product`, `User`)                 |
| `entityId`   | UUID      | ID de la entidad afectada                                                   |
| `oldValue`   | string    | Estado anterior serializado en JSON (null en creaciones)                    |
| `newValue`   | string    | Estado nuevo serializado en JSON (null en activate/deactivate)              |
| `reason`     | string    | Motivo del cambio (reservado para futuro uso)                               |
| `source`     | string    | Origen de la acción (`ADMIN_WEB`, `MOBILE_APP`, `SYSTEM`)                  |
| `ipAddress`  | string    | IP del cliente (reservado para futuro uso)                                  |
| `createdAt`  | datetime  | Fecha y hora exacta de la acción                                            |

> `oldValue` y `newValue` son strings JSON. El frontend puede parsearlos con `JSON.parse()` para mostrar el detalle del cambio.

**Ejemplos de consultas útiles:**

```
# Todo el historial de una sucursal específica
GET /audit-logs?entityName=Branch&entityId=<uuid>

# Todas las acciones de hoy sobre usuarios
GET /audit-logs?entityName=User&from=2026-06-23T00:00:00&to=2026-06-23T23:59:59

# Solo creaciones en el último mes
GET /audit-logs?action=CREATE_BRANCH&from=2026-06-01T00:00:00

# Todo sin filtros (más recientes primero)
GET /audit-logs
```

---

## Resumen de endpoints

| Método | Path                         | Descripción                        |
|--------|------------------------------|------------------------------------|
| POST   | `/auth/login`                | Login                              |
| POST   | `/branches`                  | Crear sucursal                     |
| GET    | `/branches`                  | Listar sucursales                  |
| GET    | `/branches/{id}`             | Obtener sucursal                   |
| PATCH  | `/branches/{id}`             | Actualizar sucursal                |
| PATCH  | `/branches/{id}/deactivate`  | Desactivar sucursal                |
| PATCH  | `/branches/{id}/activate`    | Activar sucursal                   |
| POST   | `/products`                  | Crear producto                     |
| GET    | `/products`                  | Listar productos                   |
| GET    | `/products/{id}`             | Obtener producto                   |
| PATCH  | `/products/{id}`             | Actualizar producto                |
| PATCH  | `/products/{id}/deactivate`  | Desactivar producto                |
| PATCH  | `/products/{id}/activate`    | Activar producto                   |
| POST   | `/users`                     | Crear usuario                      |
| GET    | `/users`                     | Listar usuarios                    |
| GET    | `/users/{id}`                | Obtener usuario                    |
| PATCH  | `/users/{id}`                | Actualizar usuario                 |
| PATCH  | `/users/{id}/deactivate`     | Desactivar usuario                 |
| PATCH  | `/users/{id}/activate`       | Activar usuario                    |
| GET    | `/audit-logs`                | Listar registros de auditoría      |

---

> Swagger UI disponible en: `http://localhost:8080/swagger-ui.html`
