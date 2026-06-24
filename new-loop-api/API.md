# LOOP API — Repartos

**Base URL (dev):** `http://localhost:8080`

---

## Estructura de respuestas

### Respuesta exitosa

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
      "totalElements": 10,
      "totalPages": 1
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
  "message": "Route not found with id: abc-123",
  "path": "/routes/abc-123"
}
```

---

## Routes (Repartos)

Base path: `/routes`

---

### POST /routes

Crea un nuevo reparto.

**Body:**

```json
{
  "code": "REP-001",
  "branchId": "550e8400-e29b-41d4-a716-446655440000",
  "driver": "Juan Pérez",
  "truckPlate": "ABC 123",
  "observations": "Reparto zona norte"
}
```

| Campo          | Tipo   | Requerido | Notas           |
|----------------|--------|-----------|-----------------|
| `code`         | string | Si        | Único, máx 50   |
| `branchId`     | UUID   | Si        | ID de sucursal  |
| `driver`       | string | No        | Máx 150         |
| `truckPlate`   | string | No        | Máx 20          |
| `observations` | string | No        | Máx 500         |

**Respuesta 201:**

```json
{
  "data": {
    "id": "660f9500-f30c-52e5-b827-557766551111",
    "code": "REP-001",
    "branchId": "550e8400-e29b-41d4-a716-446655440000",
    "branchName": "Ciudadela",
    "branchCode": "CIU",
    "driver": "Juan Pérez",
    "truckPlate": "ABC 123",
    "active": true,
    "observations": "Reparto zona norte",
    "createdAt": "2026-06-23T10:00:00",
    "updatedAt": "2026-06-23T10:00:00"
  },
  "message": "Route created successfully"
}
```

**Errores:**

| Status | Situación              | `message`                              |
|--------|------------------------|----------------------------------------|
| 400    | Campos inválidos       | Detalle del campo inválido             |
| 404    | Sucursal no encontrada | `"Branch not found with id: <id>"`    |
| 409    | `code` ya existe       | `"Route code already exists: REP-001"` |

---

### GET /routes

Lista repartos paginados.

**Query params:**

| Parámetro | Tipo   | Default   | Ejemplo        |
|-----------|--------|-----------|----------------|
| `page`    | int    | `0`       | `?page=1`      |
| `size`    | int    | `20`      | `?size=10`     |
| `sort`    | string | `code,asc`| `?sort=code,desc` |

**Respuesta 200:**

```json
{
  "data": {
    "content": [
      {
        "id": "660f9500-f30c-52e5-b827-557766551111",
        "code": "REP-001",
        "branchId": "550e8400-e29b-41d4-a716-446655440000",
        "branchName": "Ciudadela",
        "branchCode": "CIU",
        "driver": "Juan Pérez",
        "truckPlate": "ABC 123",
        "active": true,
        "observations": "Reparto zona norte",
        "createdAt": "2026-06-23T10:00:00",
        "updatedAt": "2026-06-23T10:00:00"
      }
    ],
    "page": {
      "size": 20,
      "number": 0,
      "totalElements": 5,
      "totalPages": 1
    }
  },
  "message": "Routes retrieved successfully"
}
```

---

### GET /routes/{id}

Obtiene un reparto por ID.

**Respuesta 200:** mismo objeto `RouteResponse` dentro de `data`.

**Errores:**

| Status | Situación      | `message`                              |
|--------|----------------|----------------------------------------|
| 404    | No encontrado  | `"Route not found with id: <id>"`     |

---

### PATCH /routes/{id}

Actualiza un reparto. Solo se actualizan los campos enviados (parcial).

**Body (todos opcionales):**

```json
{
  "branchId": "550e8400-e29b-41d4-a716-446655440000",
  "driver": "Carlos López",
  "truckPlate": "XYZ 999",
  "observations": "Cambio de ruta"
}
```

> `code` no se puede modificar.

**Respuesta 200:** objeto `RouteResponse` actualizado dentro de `data`.

**Errores:**

| Status | Situación              | `message`                              |
|--------|------------------------|----------------------------------------|
| 400    | Validación             | Detalle del campo inválido             |
| 404    | No encontrado          | `"Route not found with id: <id>"`     |
| 404    | Sucursal no encontrada | `"Branch not found with id: <id>"`    |

---

### PATCH /routes/{id}/deactivate

Desactiva un reparto. Los repartos inactivos no pueden usarse en controles.

**Body:** ninguno.

**Respuesta 200:**

```json
{
  "data": null,
  "message": "Route deactivated successfully"
}
```

**Errores:**

| Status | Situación     | `message`                          |
|--------|---------------|------------------------------------|
| 404    | No encontrado | `"Route not found with id: <id>"` |

---

### PATCH /routes/{id}/activate

Reactiva un reparto.

**Body:** ninguno.

**Respuesta 200:**

```json
{
  "data": null,
  "message": "Route activated successfully"
}
```

**Errores:**

| Status | Situación     | `message`                          |
|--------|---------------|------------------------------------|
| 404    | No encontrado | `"Route not found with id: <id>"` |

---

## Resumen de endpoints

| Método | Path                       | Descripción             |
|--------|----------------------------|-------------------------|
| POST   | `/routes`                  | Crear reparto           |
| GET    | `/routes`                  | Listar repartos         |
| GET    | `/routes/{id}`             | Obtener reparto         |
| PATCH  | `/routes/{id}`             | Actualizar reparto      |
| PATCH  | `/routes/{id}/deactivate`  | Desactivar reparto      |
| PATCH  | `/routes/{id}/activate`    | Activar reparto         |

---

> Swagger UI: `http://localhost:8080/swagger-ui.html`
