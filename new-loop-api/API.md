# LOOP API — Stock Controls (Controles de Stock)

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
  "timestamp": "2026-06-24T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Stock control not found with id: abc-123",
  "path": "/stock-controls/abc-123"
}
```

---

## Conceptos clave

### Tipos de control (`type`)

| Valor   | Descripción |
|---------|-------------|
| `EXIT`  | Control de salida. Registra los productos que salen en el camión. La fecha de control es **el día siguiente hábil** por defecto. |
| `ENTRY` | Control de entrada. Registra los productos que vuelven con el camión. La fecha de control es **el día actual** por defecto. |

### Fecha de control (`controlDate`)

- Si no se envía `controlDate` en el body, el sistema la calcula automáticamente:
  - `EXIT`: mañana. Si mañana es domingo, salta al lunes (la operación es de lunes a sábado).
  - `ENTRY`: hoy.
- Si se envía `controlDate`, se usa esa fecha sin importar el tipo. Útil para controles fuera del flujo normal.

### Estados (`status`)

El estado inicial de todo control es `CONTROLLED`. Los demás estados se asignan en flujos posteriores (aprobación del repartidor, integración con Aguas, etc.).

| Valor                     | Descripción |
|---------------------------|-------------|
| `CONTROLLED`              | Registrado por el controlador |
| `PENDING_DRIVER_APPROVAL` | Esperando aprobación del repartidor |
| `ACCEPTED_BY_DRIVER`      | Aprobado por el repartidor |
| `REJECTED_BY_DRIVER`      | Rechazado por el repartidor |
| `WITH_DIFFERENCES`        | Tiene diferencias entre salida y entrada |
| `SENT_TO_AGUAS`           | Enviado al sistema Aguas |
| `AGUAS_ERROR`             | Error al enviar a Aguas |
| `CANCELLED`               | Cancelado |

---

## Endpoints

### POST /stock-controls

Crea un nuevo control (de salida o de entrada).

**Body:**

```json
{
  "type": "EXIT",
  "branchId": "550e8400-e29b-41d4-a716-446655440000",
  "routeId": "661f9500-f30c-52e5-b827-557766551111",
  "controllerId": "aa1d3900-j74g-96i9-f261-991100995555",
  "controlDate": "2026-06-25",
  "observations": "Sin novedades",
  "items": [
    {
      "productId": "772a0600-g41d-63f6-c938-668877662222",
      "totalQuantity": 50,
      "fullQuantity": 48,
      "exchangeQuantity": 2,
      "observations": "Dos recambios por rotura"
    }
  ]
}
```

**Campos del control:**

| Campo          | Tipo   | Requerido | Notas |
|----------------|--------|-----------|-------|
| `type`           | string | Si        | `EXIT` o `ENTRY` |
| `branchId`       | UUID   | Si        | ID de la sucursal |
| `routeId`        | UUID   | Si        | ID del reparto |
| `controllerId`   | UUID   | No        | ID del usuario que realiza el control |
| `controlDate`    | date   | No        | Formato `YYYY-MM-DD`. Si no se envía, el sistema lo calcula. |
| `observations`   | string | No        | Máx 500 caracteres |
| `items`          | array  | Si        | Al menos un producto requerido |

**Campos de cada item:**

| Campo              | Tipo    | Requerido | Notas |
|--------------------|---------|-----------|-------|
| `productId`        | UUID    | Si        | El producto debe estar activo |
| `totalQuantity`    | integer | Si        | No puede ser negativa |
| `fullQuantity`     | integer | Si        | No puede ser negativa |
| `exchangeQuantity` | integer | Si        | No puede ser negativa |
| `observations`     | string  | No        | Máx 500 caracteres |

> Un mismo producto no puede aparecer dos veces en el mismo control.

**Respuesta 201:**

```json
{
  "data": {
    "id": "883b1700-h52e-74g7-d049-779988773333",
    "type": "EXIT",
    "status": "CONTROLLED",
    "branchId": "550e8400-e29b-41d4-a716-446655440000",
    "branchName": "Ciudadela",
    "routeId": "661f9500-f30c-52e5-b827-557766551111",
    "routeCode": "REP-001",
    "controllerId": "aa1d3900-j74g-96i9-f261-991100995555",
    "controlDate": "2026-06-25",
    "observations": "Sin novedades",
    "items": [
      {
        "id": "994c2800-i63f-85h8-e150-880099884444",
        "productId": "772a0600-g41d-63f6-c938-668877662222",
        "productCode": "BID-20",
        "productName": "Bidón 20L",
        "productUnit": "unidad",
        "totalQuantity": 50,
        "fullQuantity": 48,
        "exchangeQuantity": 2,
        "differenceQuantity": null,
        "observations": "Dos recambios por rotura"
      }
    ],
    "createdAt": "2026-06-24T10:00:00",
    "updatedAt": "2026-06-24T10:00:00"
  },
  "message": "Stock control created successfully"
}
```

> `differenceQuantity` es siempre `null` en controles EXIT. Se calcula en el control ENTRY correspondiente.

**Errores:**

| Status | Situación | `message` |
|--------|-----------|-----------|
| 400    | Campos inválidos / cantidades negativas | Detalle del campo inválido |
| 400    | `items` vacío | `"At least one item is required"` |
| 400    | `type` inválido | `"Type is required (EXIT or ENTRY)"` |
| 404    | Sucursal no encontrada | `"Branch not found with id: <id>"` |
| 404    | Reparto no encontrado | `"Route not found with id: <id>"` |
| 404    | Producto no encontrado | `"Product not found with id: <id>"` |
| 409    | Producto inactivo | `"Product is inactive and cannot be used in a control: <code>"` |

---

### GET /stock-controls

Lista controles paginados, ordenados por fecha de creación descendente. Permite filtrar por tipo.

Todos los filtros son opcionales y combinables entre sí.

**Ejemplos de uso:**

```
GET /stock-controls                                        → todos los controles
GET /stock-controls?type=EXIT                             → solo salidas
GET /stock-controls?type=ENTRY                            → solo entradas
GET /stock-controls?from=2026-06-01&to=2026-06-30        → por rango de fecha
GET /stock-controls?routeId=661f9500-...                  → por reparto
GET /stock-controls?controllerId=772a0600-...             → por controlador
GET /stock-controls?type=EXIT&from=2026-06-01&routeId=... → combinados
```

**Query params:**

| Parámetro      | Tipo   | Formato      | Notas |
|----------------|--------|--------------|-------|
| `type`         | string | —            | `EXIT` o `ENTRY`. Si se omite, devuelve todos. |
| `routeId`      | UUID   | —            | Filtra por ID de reparto. |
| `controllerId` | UUID   | —            | Filtra por ID del usuario controlador. |
| `from`         | date   | `YYYY-MM-DD` | Fecha de control desde (inclusive). |
| `to`           | date   | `YYYY-MM-DD` | Fecha de control hasta (inclusive). |
| `page`         | int    | —            | Página (base 0). Default: `0`. |
| `size`         | int    | —            | Resultados por página. Default: `20`. |
| `sort`         | string | —            | Campo y dirección. Ej: `controlDate,desc`. |

**Respuesta 200:**

```json
{
  "data": {
    "content": [
      {
        "id": "883b1700-h52e-74g7-d049-779988773333",
        "type": "EXIT",
        "status": "CONTROLLED",
        "branchId": "550e8400-e29b-41d4-a716-446655440000",
        "branchName": "Ciudadela",
        "routeId": "661f9500-f30c-52e5-b827-557766551111",
        "routeCode": "REP-001",
        "controllerId": "aa1d3900-j74g-96i9-f261-991100995555",
        "controlDate": "2026-06-25",
        "observations": null,
        "items": [ "..." ],
        "createdAt": "2026-06-24T10:00:00",
        "updatedAt": "2026-06-24T10:00:00"
      }
    ],
    "page": {
      "size": 20,
      "number": 0,
      "totalElements": 3,
      "totalPages": 1
    }
  },
  "message": "Stock controls retrieved successfully"
}
```

---

### PATCH /stock-controls/{id}

Modifica un control existente. Solo se permiten cambios si el control está en estado `CONTROLLED`.

Todos los campos son opcionales. Si `items` se envía, **reemplaza toda la lista** de productos del control.

**Body:**

```json
{
  "controllerId": "aa1d3900-j74g-96i9-f261-991100995555",
  "controlDate": "2026-06-26",
  "observations": "Corrección de cantidades",
  "items": [
    {
      "productId": "772a0600-g41d-63f6-c938-668877662222",
      "totalQuantity": 52,
      "fullQuantity": 50,
      "exchangeQuantity": 2,
      "observations": null
    }
  ]
}
```

| Campo          | Tipo    | Requerido | Notas |
|----------------|---------|-----------|-------|
| `controllerId` | UUID    | No        | Reemplaza el controlador asignado |
| `controlDate`  | date    | No        | Formato `YYYY-MM-DD` |
| `observations` | string  | No        | Máx 500 caracteres |
| `items`        | array   | No        | Si se envía, reemplaza **todos** los items del control |

> No se puede modificar `type`, `branchId` ni `routeId`.

**Respuesta 200:** objeto `StockControlResponse` actualizado dentro de `data`.

**Errores:**

| Status | Situación | `message` |
|--------|-----------|-----------|
| 400    | Validación de items | Detalle del campo inválido |
| 404    | No encontrado | `"Stock control not found with id: <id>"` |
| 404    | Producto no encontrado | `"Product not found with id: <id>"` |
| 409    | Estado no permite modificación | `"Stock control <id> cannot be modified in status: <status>"` |
| 409    | Producto inactivo | `"Product is inactive and cannot be used in a control: <code>"` |

---

### POST /stock-controls/{id}/confirm

Confirma un control de salida. Cambia el estado a `PENDING_DRIVER_APPROVAL` y registra la fecha/hora de confirmación. A partir de este momento el repartidor puede visualizarlo y aceptarlo desde la app.

**Body:** ninguno.

**Respuesta 200:**

```json
{
  "data": {
    "id": "883b1700-h52e-74g7-d049-779988773333",
    "type": "EXIT",
    "status": "PENDING_DRIVER_APPROVAL",
    "confirmedAt": "2026-06-24T14:30:00",
    "..."
  },
  "message": "Stock control confirmed successfully"
}
```

**Errores:**

| Status | Situación | `message` |
|--------|-----------|-----------|
| 404    | No encontrado | `"Stock control not found with id: <id>"` |
| 409    | Estado inválido | `"Stock control <id> must be in status CONTROLLED but was <status>"` |

---

### GET /stock-controls/{id}

Obtiene un control por ID con el detalle completo de sus items.

**Respuesta 200:** mismo objeto `StockControlResponse` dentro de `data`.

**Errores:**

| Status | Situación | `message` |
|--------|-----------|-----------|
| 404    | No encontrado | `"Stock control not found with id: <id>"` |

---

## Referencia de campos

### StockControlResponse

| Campo         | Tipo     | Descripción |
|---------------|----------|-------------|
| `id`          | UUID     | ID del control |
| `type`        | string   | `EXIT` o `ENTRY` |
| `status`      | string   | Estado actual del control |
| `branchId`    | UUID     | ID de la sucursal |
| `branchName`  | string   | Nombre de la sucursal |
| `routeId`      | UUID     | ID del reparto |
| `routeCode`    | string   | Código del reparto |
| `controllerId` | UUID     | ID del usuario que realizó el control. `null` si no se envió. |
| `controlDate`  | date     | Fecha para la que aplica el control (`YYYY-MM-DD`) |
| `observations`| string   | Observaciones generales |
| `items`       | array    | Detalle por producto |
| `confirmedAt` | datetime | Fecha/hora de confirmación. `null` hasta que se confirme. |
| `createdAt`   | datetime | Fecha/hora de creación |
| `updatedAt`   | datetime | Fecha/hora de última modificación |

### StockControlItemResponse

| Campo                | Tipo    | Descripción |
|----------------------|---------|-------------|
| `id`                 | UUID    | ID del item |
| `productId`          | UUID    | ID del producto |
| `productCode`        | string  | Código del producto |
| `productName`        | string  | Nombre del producto |
| `productUnit`        | string  | Unidad de medida |
| `totalQuantity`      | integer | Cantidad total |
| `fullQuantity`       | integer | Cantidad de llenos |
| `exchangeQuantity`   | integer | Cantidad de recambios |
| `differenceQuantity` | integer | Diferencia calculada. `null` en EXIT, valor en ENTRY. |
| `observations`       | string  | Observaciones del item |

---

## Resumen de endpoints

| Método | Path                   | Descripción |
|--------|------------------------|-------------|
| POST   | `/stock-controls`      | Crear control (EXIT o ENTRY) |
| GET    | `/stock-controls`      | Listar controles paginados |
| GET    | `/stock-controls/{id}` | Obtener control por ID |
| PATCH  | `/stock-controls/{id}`         | Modificar control (solo en estado CONTROLLED) |
| POST   | `/stock-controls/{id}/confirm` | Confirmar control → pasa a PENDING_DRIVER_APPROVAL |

---

> Swagger UI: `http://localhost:8080/swagger-ui.html`
