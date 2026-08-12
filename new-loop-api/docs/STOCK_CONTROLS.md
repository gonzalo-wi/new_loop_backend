# Controles de Stock

Módulo para registrar salidas y entradas de mercadería de los repartos.

---

## Índice

- [Crear control](#crear-control)
- [Listar controles](#listar-controles)
- [Obtener control por ID](#obtener-control-por-id)
- [Actualizar control](#actualizar-control)
- [Confirmar control](#confirmar-control)
- [Corregir control (SUPERVISOR)](#corregir-control-supervisor)
- [Ver remito en PDF](#ver-remito-en-pdf)
- [Referencia de campos](#referencia-de-campos)

---

## Crear control

```
POST /stock-controls
Authorization: Bearer <token>
```

Crea un nuevo control de salida (`EXIT`) o entrada (`ENTRY`).

### Request body

```json
{
  "type": "EXIT",
  "branchId": "uuid-de-la-sucursal",
  "routeId": "uuid-del-reparto",
  "controllerId": "uuid-del-controlador",
  "controlDate": "2026-06-25",
  "truckOrdered": true,
  "observations": "Sin novedades",
  "items": [
    {
      "productId": "uuid-del-producto",
      "totalQuantity": 10,
      "fullQuantity": 8,
      "exchangeQuantity": 2,
      "observations": "Observación opcional"
    }
  ]
}
```

| Campo          | Tipo    | Requerido | Default    | Descripción                                              |
|----------------|---------|-----------|------------|----------------------------------------------------------|
| `type`         | string  | Sí        | —          | `EXIT` (salida) o `ENTRY` (entrada)                      |
| `branchId`     | UUID    | Sí        | —          | ID de la sucursal                                        |
| `routeId`      | UUID    | Sí        | —          | ID del reparto                                           |
| `controllerId` | UUID    | No        | —          | ID del usuario controlador                               |
| `controlDate`  | date    | No        | Ver nota   | Fecha del control (`YYYY-MM-DD`)                         |
| `truckOrdered` | boolean | No        | `true`     | Si el camión está ordenado                               |
| `observations` | string  | No        | —          | Observaciones generales (máx. 500 caracteres)            |
| `items`        | array   | Sí        | —          | Al menos un producto (ver tabla de items)                |

> **Nota sobre `controlDate`:**
> - Si no se envía y el tipo es `EXIT` → se asigna el **día siguiente** (salteando domingos)
> - Si no se envía y el tipo es `ENTRY` → se asigna el **día de hoy**
> - Si se envía, se usa la fecha indicada

**Campos de cada item:**

| Campo              | Tipo    | Requerido | Descripción                                    |
|--------------------|---------|-----------|------------------------------------------------|
| `productId`        | UUID    | Sí        | ID del producto                                |
| `totalQuantity`    | integer | Sí        | Cantidad total (≥ 0)                           |
| `fullQuantity`     | integer | Sí        | Cantidad llena (≥ 0)                           |
| `exchangeQuantity` | integer | Sí        | Cantidad de cambio (≥ 0)                       |
| `observations`     | string  | No        | Observaciones del ítem (máx. 500 caracteres)   |

### Response `201 Created`

```json
{
  "data": {
    "id": "uuid-del-control",
    "type": "EXIT",
    "status": "CONTROLLED",
    "branchId": "uuid-de-la-sucursal",
    "branchName": "Ciudadela",
    "routeId": "uuid-del-reparto",
    "routeCode": "R01",
    "controllerId": "uuid-del-controlador",
    "controlDate": "2026-06-25",
    "truckOrdered": true,
    "observations": "Sin novedades",
    "items": [
      {
        "id": "uuid-del-item",
        "productId": "uuid-del-producto",
        "productCode": "PRD001",
        "productName": "Bidón 20L",
        "productUnit": "UN",
        "totalQuantity": 10,
        "fullQuantity": 8,
        "exchangeQuantity": 2,
        "differenceQuantity": null,
        "observations": "Observación opcional"
      }
    ],
    "confirmedAt": null,
    "createdAt": "2026-06-24T10:30:00",
    "updatedAt": "2026-06-24T10:30:00"
  },
  "message": "Stock control created successfully"
}
```

> `differenceQuantity` siempre viene `null` en controles de salida (`EXIT`). Se calcula en controles de entrada.

---

## Listar controles

```
GET /stock-controls
Authorization: Bearer <token>
```

Devuelve una lista paginada de controles. Todos los filtros son opcionales y se pueden combinar.

### Query params

| Param          | Tipo    | Descripción                                              |
|----------------|---------|----------------------------------------------------------|
| `type`         | string  | Filtrar por tipo: `EXIT` o `ENTRY`                       |
| `status`       | string  | Filtrar por estado (ver tabla de estados)                |
| `routeId`      | UUID    | Filtrar por reparto                                      |
| `controllerId` | UUID    | Filtrar por controlador                                  |
| `from`         | date    | Fecha de control desde (`YYYY-MM-DD`)                    |
| `to`           | date    | Fecha de control hasta (`YYYY-MM-DD`)                    |
| `page`         | integer | Número de página, empieza en `0` (default: `0`)          |
| `size`         | integer | Cantidad de resultados por página (default: `20`)        |
| `sort`         | string  | Ordenamiento (default: `createdAt,desc`)                 |

**Ejemplo — controles pendientes de aprobación para el repartidor:**
```
GET /stock-controls?routeId=uuid-del-reparto&status=PENDING_DRIVER_APPROVAL
```

**Ejemplo — controles de salida de hoy:**
```
GET /stock-controls?type=EXIT&from=2026-06-24&to=2026-06-24
```

### Response `200 OK`

```json
{
  "data": {
    "content": [ /* array de controles */ ],
    "totalElements": 42,
    "totalPages": 3,
    "size": 20,
    "number": 0
  },
  "message": "Stock controls retrieved successfully"
}
```

---

## Obtener control por ID

```
GET /stock-controls/{id}
Authorization: Bearer <token>
```

### Response `200 OK`

Devuelve el mismo objeto que en la respuesta de creación.

### Errores

**`404 Not Found`** — control no encontrado
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Stock control not found: uuid-del-control"
}
```

---

## Actualizar control

```
PATCH /stock-controls/{id}
Authorization: Bearer <token>
```

Solo se puede modificar un control en estado `CONTROLLED`. Todos los campos son opcionales — solo se actualizan los que se envíen.

> Si se envía `items`, **reemplaza todos los ítems existentes** por los nuevos.

### Request body

```json
{
  "controllerId": "uuid-del-controlador",
  "controlDate": "2026-06-25",
  "truckOrdered": false,
  "observations": "Nueva observación",
  "items": [
    {
      "productId": "uuid-del-producto",
      "totalQuantity": 12,
      "fullQuantity": 10,
      "exchangeQuantity": 2
    }
  ]
}
```

### Response `200 OK`

Devuelve el control actualizado con el mismo formato que la creación.

### Errores

**`409 Conflict`** — control no modificable (ya fue confirmado u otro estado)
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Stock control uuid no puede modificarse en estado: PENDING_DRIVER_APPROVAL"
}
```

---

## Confirmar control

```
POST /stock-controls/{id}/confirm
Authorization: Bearer <token>
```

Transiciona el control de `CONTROLLED` a `PENDING_DRIVER_APPROVAL`. A partir de este momento el repartidor puede verlo y aprobarlo o rechazarlo.

No requiere body.

### Response `200 OK`

```json
{
  "data": {
    "id": "uuid-del-control",
    "status": "PENDING_DRIVER_APPROVAL",
    "confirmedAt": "2026-06-24T14:22:00",
    ...
  },
  "message": "Stock control confirmed successfully"
}
```

### Errores

**`409 Conflict`** — el control no está en estado `CONTROLLED`
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Stock control uuid debe estar en estado CONTROLLED pero está en: PENDING_DRIVER_APPROVAL"
}
```

---

## Corregir control (SUPERVISOR)

```
POST /stock-controls/{id}/correct
Authorization: Bearer <token>
```

Permite corregir un control de **entrada (ENTRY)** que ya fue enviado a Aguas, pisando el envío anterior. Pensado para cuando se detecta un error después de que el control ya impactó en Aguas (ej: el chofer cargó mal una cantidad).

**Requiere rol `SUPERVISOR`.** Cualquier otro rol recibe `403 Forbidden`.

Solo aplica a controles `ENTRY` en estado `SENT_TO_AGUAS` o `AGUAS_ERROR`. No está disponible para controles `EXIT` ni para otros estados (por ejemplo, no se puede "corregir" algo que todavía no se envió — para eso está [Actualizar control](#actualizar-control)).

Al confirmarse, la app debe considerar que el reenvío a Aguas es **asíncrono**: el control puede quedar un instante en `SENT_TO_AGUAS` con el `aguasFormulario`/`aguasNroRemito` todavía viejos hasta que el reenvío termine. Si el reenvío falla, el control pasa a `AGUAS_ERROR` (el backend reintenta solo) y puede volver a corregirse.

### Request body

```json
{
  "reason": "El chofer cargó mal la cantidad de cambio del producto PRD001",
  "observations": "Corregido tras reclamo del chofer",
  "truckOrdered": true,
  "items": [
    {
      "productId": "uuid-del-producto",
      "totalQuantity": 12,
      "fullQuantity": 10,
      "exchangeQuantity": 2,
      "observations": "Observación opcional del ítem"
    }
  ]
}
```

| Campo          | Tipo    | Requerido | Descripción                                                        |
|----------------|---------|-----------|---------------------------------------------------------------------|
| `reason`       | string  | **Sí**    | Motivo de la corrección (máx. 500 caracteres). Queda en la auditoría |
| `items`        | array   | **Sí**    | Lista de ítems corregidos, no puede venir vacía. **Reemplaza todos los ítems existentes** (mismo formato que en [Crear control](#crear-control)) |
| `observations` | string  | No        | Observaciones generales del control (máx. 500 caracteres)          |
| `truckOrdered` | boolean | No        | Si el camión está ordenado                                         |

> A diferencia de [Actualizar control](#actualizar-control), no se puede cambiar `controllerId` ni `controlDate` en una corrección.

### Response `200 OK`

Devuelve el control corregido con el mismo formato que la creación (`aguasFormulario`/`aguasNroRemito` se actualizan una vez que el reenvío a Aguas termina).

```json
{
  "data": {
    "id": "uuid-del-control",
    "type": "ENTRY",
    "status": "SENT_TO_AGUAS",
    "observations": "Corregido tras reclamo del chofer",
    "items": [ /* ítems corregidos */ ],
    "aguasFormulario": "R202",
    "aguasNroRemito": 356104,
    "...": "..."
  },
  "message": "Stock control corrected successfully"
}
```

### Errores

**`403 Forbidden`** — el usuario autenticado no tiene rol `SUPERVISOR`
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Only users with role SUPERVISOR can correct a stock control"
}
```

**`404 Not Found`** — control no encontrado (se verifica solo si el usuario es SUPERVISOR)
```json
{ "status": 404, "error": "Not Found", "message": "Stock control not found: uuid-del-control" }
```

**`409 Conflict`** — el control no es corregible: es `EXIT`, o su estado no es `SENT_TO_AGUAS`/`AGUAS_ERROR`
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Stock control uuid cannot be corrected in status: PENDING_DRIVER_APPROVAL"
}
```

**`400 Bad Request`** — falta `reason`, o `items` viene vacío/ausente
```json
{ "status": 400, "error": "Bad Request", "message": "Reason is required" }
```

### Auditoría

Cada corrección queda registrada en `GET /audit` (ver [AUTH.md](./AUTH.md) o el módulo de auditoría) con:
- `action`: `CORRECT_STOCK_CONTROL`
- `username` y `userRole` del supervisor que corrigió
- `reason`: el motivo enviado en el request
- `oldValue`/`newValue`: snapshot completo del control antes y después de la corrección, para poder comparar qué cambió

---

## Ver remito en PDF

Solo disponible para controles de **salida (EXIT)** que ya fueron enviados a Aguas (necesitan tener `aguasFormulario` y `aguasNroRemito` cargados — ver [Referencia de campos](#referencia-de-campos)). Muestra el número de formulario, número de remito y el detalle de productos cargados.

Dos formas de pedirlo:

### Por ID del control

```
GET /stock-controls/{id}/remito
Authorization: Bearer <token>
```

### Por reparto y fecha

```
GET /stock-controls/remito?routeId={routeId}&date=2026-06-25
Authorization: Bearer <token>
```

Busca el control de salida (no cancelado) de ese reparto en esa fecha — como solo puede existir uno por reparto/fecha (regla de duplicados), la búsqueda es siempre unívoca.

### Response `200 OK`

Devuelve el archivo directo, no JSON:
```
Content-Type: application/pdf
Content-Disposition: inline; filename="remito-<id>.pdf"
```

El navegador lo abre directo (se puede ver o guardar desde ahí). El PDF incluye: formulario, número de remito, reparto, sucursal, fecha, controlador, observaciones, y la tabla de productos (código, nombre, total, llenos, cambio).

### Errores

**`404 Not Found`** — no existe el control (por ID), o no hay control de salida para ese reparto/fecha
```json
{ "status": 404, "error": "Not Found", "message": "No EXIT control found for route <routeId> on 2026-06-25" }
```

**`409 Conflict`** — el control existe pero todavía no tiene remito
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Remito not available for stock control <id>: the control has not been confirmed with Aguas yet"
}
```
> Pasa si el control es de tipo `ENTRY` (los remitos son solo de salidas), o si la salida todavía no se envió/confirmó con Aguas.

---

## Referencia de campos

### Estados del control (`status`)

| Estado                   | Descripción                                              |
|--------------------------|----------------------------------------------------------|
| `CONTROLLED`             | Creado, aún modificable                                  |
| `PENDING_DRIVER_APPROVAL`| Confirmado por el controlador, esperando al repartidor   |
| `ACCEPTED_BY_DRIVER`     | Aprobado por el repartidor                               |
| `REJECTED_BY_DRIVER`     | Rechazado por el repartidor                              |
| `WITH_DIFFERENCES`       | Aprobado con diferencias registradas                     |
| `SENT_TO_AGUAS`          | Enviado al sistema Aguas                                 |
| `AGUAS_ERROR`            | Error al enviar a Aguas                                  |
| `CANCELLED`              | Cancelado                                                |

### Flujo de estados

```
CONTROLLED
    └─► PENDING_DRIVER_APPROVAL  (POST /confirm)
            ├─► ACCEPTED_BY_DRIVER
            └─► REJECTED_BY_DRIVER
```

> Para controles `ENTRY`, una vez en `SENT_TO_AGUAS` o `AGUAS_ERROR`, un `SUPERVISOR` puede volver a corregirlos con `POST /{id}/correct` (ver [Corregir control](#corregir-control-supervisor)). El control permanece en `SENT_TO_AGUAS` (o pasa a `AGUAS_ERROR` si el reenvío falla), no hay un estado nuevo — solo se actualizan sus datos y se reenvía a Aguas.

### Campos de la respuesta del control

| Campo          | Tipo      | Descripción                                         |
|----------------|-----------|-----------------------------------------------------|
| `id`           | UUID      | ID del control                                      |
| `type`         | string    | `EXIT` o `ENTRY`                                    |
| `status`       | string    | Estado actual (ver tabla de estados)                |
| `branchId`     | UUID      | ID de la sucursal                                   |
| `branchName`   | string    | Nombre de la sucursal                               |
| `routeId`      | UUID      | ID del reparto                                      |
| `routeCode`    | string    | Código del reparto                                  |
| `controllerId` | UUID      | ID del controlador (puede ser `null`)               |
| `controlDate`  | date      | Fecha del control                                   |
| `truckOrdered` | boolean   | Si el camión está ordenado                          |
| `observations` | string    | Observaciones generales (puede ser `null`)          |
| `items`        | array     | Productos del control (ver tabla de items)          |
| `confirmedAt`  | datetime  | Fecha y hora de confirmación (null hasta confirmar) |
| `createdAt`    | datetime  | Fecha de creación                                   |
| `updatedAt`    | datetime  | Última actualización                                |

### Campos de cada item en la respuesta

| Campo                | Tipo    | Descripción                                          |
|----------------------|---------|------------------------------------------------------|
| `id`                 | UUID    | ID del ítem                                          |
| `productId`          | UUID    | ID del producto                                      |
| `productCode`        | string  | Código del producto                                  |
| `productName`        | string  | Nombre del producto                                  |
| `productUnit`        | string  | Unidad de medida                                     |
| `totalQuantity`      | integer | Cantidad total                                       |
| `fullQuantity`       | integer | Cantidad llena                                       |
| `exchangeQuantity`   | integer | Cantidad de cambio                                   |
| `differenceQuantity` | integer | Diferencia calculada (`null` en controles de salida) |
| `observations`       | string  | Observaciones del ítem (puede ser `null`)            |
