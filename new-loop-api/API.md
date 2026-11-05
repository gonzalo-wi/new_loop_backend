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
GET /stock-controls?createdFrom=2026-06-15&createdTo=2026-06-15 → cargados ese día
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
| `createdFrom`  | date   | `YYYY-MM-DD` | Fecha de creación desde (inclusive, desde las 00:00). |
| `createdTo`    | date   | `YYYY-MM-DD` | Fecha de creación hasta (inclusive, todo el día). |
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

Modifica un control existente. Solo se permiten cambios mientras el repartidor no lo aceptó: estados `CONTROLLED`, `PENDING_DRIVER_APPROVAL` o `REJECTED_BY_DRIVER`. Hasta ese momento nada se manda a Aguas.

Si el control estaba `REJECTED_BY_DRIVER`, guardarlo lo **reenvía al repartidor**: vuelve a `PENDING_DRIVER_APPROVAL`, se actualiza `confirmedAt` y se borra `driverRejectionReason`.

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

### POST /stock-controls/{id}/approve

El repartidor acepta un control de entrada. Pasa de `PENDING_DRIVER_APPROVAL` a `ACCEPTED_BY_DRIVER`, registra `approvedAt` y recién ahí se envía a Aguas (en segundo plano).

**Body:** ninguno.

**Errores:**

| Status | Situación | `message` |
|--------|-----------|-----------|
| 404    | No encontrado | `"Stock control not found with id: <id>"` |
| 409    | Estado inválido | `"Stock control <id> must be in status PENDING_DRIVER_APPROVAL but was <status>"` |

---

### POST /stock-controls/{id}/reject

El repartidor rechaza un control de entrada que espera su conformidad. Pasa de `PENDING_DRIVER_APPROVAL` a `REJECTED_BY_DRIVER` y guarda el motivo en `driverRejectionReason`. No se envía nada a Aguas: el controlador lo corrige con `PATCH /stock-controls/{id}`, que lo vuelve a mandar al repartidor.

**Body:**

```json
{ "reason": "Faltan 2 bidones de 20L" }
```

| Campo    | Tipo   | Requerido | Notas |
|----------|--------|-----------|-------|
| `reason` | string | Sí        | Máx 500 caracteres. Queda en el control y en la auditoría (`REJECT_STOCK_CONTROL`) |

**Respuesta 200:** objeto `StockControlResponse` con `status: "REJECTED_BY_DRIVER"` dentro de `data`.

**Errores:**

| Status | Situación | `message` |
|--------|-----------|-----------|
| 400    | Falta el motivo | `"Reason is required"` |
| 400    | Motivo de más de 500 caracteres | `"Reason cannot exceed 500 characters"` |
| 404    | No encontrado | `"Stock control not found with id: <id>"` |
| 409    | Estado inválido | `"Stock control <id> must be in status PENDING_DRIVER_APPROVAL but was <status>"` |

---

### GET /stock-controls/{id}

Obtiene un control por ID con el detalle completo de sus items.

**Respuesta 200:** mismo objeto `StockControlResponse` dentro de `data`.

**Errores:**

| Status | Situación | `message` |
|--------|-----------|-----------|
| 404    | No encontrado | `"Stock control not found with id: <id>"` |

---

### GET /integrations/pac/arrivals

Rutas que ya volvieron (control de entrada no cancelado) para una fecha, con la hora de llegada. Es de solo lectura y lo consume el backend de PAC. **No usa JWT**: pide HTTP Basic con las credenciales de la integración (`PAC_INTEGRATION_USERNAME` / `PAC_INTEGRATION_PASSWORD`). Detalle completo en `docs/PAC_ARRIVALS.md`.

**Query params:**

| Param  | Tipo | Descripción |
|--------|------|-------------|
| `date` | `YYYY-MM-DD` (opcional) | Fecha de control. Por defecto, hoy en hora de Argentina |

**Respuesta 200:**

```json
{
  "data": [
    {
      "routeNumber": 179,
      "routeCode": "Rto 179",
      "branchId": "3f2b8c1e-5a47-4d0e-9a53-0d6f1c2b7a10",
      "branchName": "Mar del Plata",
      "arrivedAt": "2026-11-02T15:20:00-03:00"
    }
  ],
  "message": "Arrivals retrieved successfully"
}
```

Ordenado por hora de llegada. Sin llegadas: `data: []`. Se omiten los controles cancelados y las rutas sin dígitos en el código; los números repetidos no se unifican.

**Errores:**

| Status | Situación | `message` |
|--------|-----------|-----------|
| 400    | `date` mal formada | `"Invalid value for parameter 'date'"` |
| 401    | Sin credenciales, incorrectas, un JWT de usuario o la integración deshabilitada | `"Invalid integration credentials"` |

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
| `confirmedAt` | datetime | Fecha/hora en que se mandó al repartidor (se actualiza en cada reenvío). |
| `approvedAt`  | datetime | Fecha/hora en que el repartidor lo aceptó. |
| `driverRejectionReason` | string | Motivo del rechazo del repartidor. Solo viene mientras el control está `REJECTED_BY_DRIVER`; si no, `null`. |
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
| PATCH  | `/stock-controls/{id}`         | Modificar control (antes de que el repartidor lo acepte; si estaba rechazado, lo reenvía) |
| POST   | `/stock-controls/{id}/approve` | El repartidor acepta → ACCEPTED_BY_DRIVER y se envía a Aguas |
| POST   | `/stock-controls/{id}/reject`  | El repartidor rechaza con motivo → REJECTED_BY_DRIVER |
| POST   | `/stock-controls/remitos`         | Varios remitos en un PDF, uno por hoja (ver `docs/STOCK_CONTROLS.md`) |
| POST   | `/stock-controls/remitos/printed` | Marca varios remitos como impresos |
| GET    | `/holidays`                    | Feriados desde el 1° del mes actual (o `?from=`) |
| POST   | `/holidays`                    | Agrega un feriado (solo ADMIN) |
| DELETE | `/holidays/{id}`               | Borra un feriado (solo ADMIN) |
| GET    | `/performance/routes`          | Estadísticas de repartos: estadía, llegadas por hora y día, salidas de la mañana, rechazos (ver `docs/PERFORMANCE.md`) |
| GET    | `/integrations/pac/arrivals`   | Rutas que ya volvieron en una fecha, para PAC (HTTP Basic de la integración, sin JWT) |

---

> Swagger UI: `http://localhost:8080/swagger-ui.html` (pide token desde la spec 003, ver `docs/AUTH.md`)
