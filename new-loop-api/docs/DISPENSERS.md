# Dispensers — Carga y descarga en camión

Módulo para registrar la carga y descarga de dispensers (equipos) en los camiones, escaneando sus números de serie. Cada movimiento se envía automáticamente a Aguas.

Usuarios: `CARGADOR_DISPENSERS` y `ADMIN`.

---

## Índice

- [Flujo completo](#flujo-completo)
- [1. Cargar catálogos de Aguas](#1-cargar-catálogos-de-aguas)
- [2. Registrar un movimiento](#2-registrar-un-movimiento)
- [3. Listar movimientos](#3-listar-movimientos)
- [4. Ver los movimientos del día](#4-ver-los-movimientos-del-día)
- [5. Ver detalle](#5-ver-detalle)
- [6. Corregir un movimiento (modificar)](#6-corregir-un-movimiento-modificar)
- [7. Cancelar un movimiento (eliminar)](#7-cancelar-un-movimiento-eliminar)
- [Referencia de campos](#referencia-de-campos)
- [Estados y envío a Aguas](#estados-y-envío-a-aguas)

---

## Flujo completo

```
1. App carga los catálogos de Aguas   →  GET /dispenser-movements/aguas/locations
                                          GET /dispenser-movements/aguas/states
2. Usuario ingresa el nro de reparto
3. Usuario escanea los seriales de los dispensers con la cámara
4. Elige ubicación y estado destino (de los catálogos)
5. Toca "Enviar"                       →  POST /dispenser-movements
6. El backend guarda y manda a Aguas automáticamente
```

Hay dos tipos de movimiento:
- **`LOAD`** — carga de dispensers al camión (Aguas: salida de camión)
- **`UNLOAD`** — descarga de dispensers (Aguas: vuelta de camión)

---

## 1. Cargar catálogos de Aguas

Antes de registrar, cargar las **ubicaciones** y **estados** de destino que ofrece Aguas, para poblar los selectores. Vienen separados por tipo de operación (`salida_camion` / `vuelta_camion`).

### Ubicaciones de destino
```
GET /dispenser-movements/aguas/locations
Authorization: Bearer <token>
```
```json
{
  "data": {
    "success": true,
    "data": {
      "vuelta_camion": [
        { "id": 40, "descripcion": "SEGUNDO PISO" },
        { "id": 47, "descripcion": "NAFA" },
        { "id": 49, "descripcion": "PLANTA BAJA" }
      ],
      "salida_camion": [
        { "id": 2, "descripcion": "EN CAMIONETA" }
      ]
    }
  }
}
```

### Estados de destino
```
GET /dispenser-movements/aguas/states
Authorization: Bearer <token>
```
```json
{
  "data": {
    "success": true,
    "data": {
      "vuelta_camion": [
        { "id": 4,  "descripcion": "EN REPARACION" },
        { "id": 14, "descripcion": "A" }
      ],
      "salida_camion": [
        { "id": 2,  "descripcion": "OPERATIVO" },
        { "id": 8,  "descripcion": "REVISAR ESTADO" }
      ]
    }
  }
}
```

> **En la app:** cuando el usuario elige `LOAD` (carga), mostrar las opciones de `salida_camion`. Cuando elige `UNLOAD` (descarga), mostrar las de `vuelta_camion`. El `id` elegido se manda como `locationId` / `stateId`.

---

## 2. Registrar un movimiento

```
POST /dispenser-movements
Authorization: Bearer <token>
```

### Request body

```json
{
  "type": "LOAD",
  "routeCode": "179",
  "technician": "MARTIN RODRIGUEZ",
  "locationId": 2,
  "stateId": 2,
  "movementDate": "2026-06-18",
  "serials": ["11177762", "LM29P05300840"]
}
```

| Campo          | Tipo    | Requerido | Default | Descripción                                                  |
|----------------|---------|-----------|---------|--------------------------------------------------------------|
| `type`         | string  | Sí        | —          | `LOAD` (carga) o `UNLOAD` (descarga)                        |
| `routeCode`    | string  | Sí        | —          | Número de reparto                                           |
| `technician`   | string  | Sí        | —          | Nombre del técnico                                          |
| `locationId`   | integer | No        | según tipo | ID de ubicación destino (del catálogo de Aguas)            |
| `stateId`      | integer | No        | según tipo | ID de estado destino (del catálogo de Aguas)               |
| `movementDate` | date    | No        | hoy        | Fecha del movimiento (`YYYY-MM-DD`)                        |
| `serials`      | array   | Sí        | —          | Números de serie escaneados (al menos uno)                 |

> **Defaults de `locationId` / `stateId` (si no se envían):**
>
> | Tipo     | `locationId`            | `stateId`               |
> |----------|-------------------------|-------------------------|
> | `LOAD`   | `2` — EN CAMIONETA      | `2` — OPERATIVO         |
> | `UNLOAD` | `49` — PLANTA BAJA      | `4` — EN REPARACION     |
>
> Se pueden mandar explícitos para sobrescribir el default (ej. en una bajada, elegir SEGUNDO PISO o NAFA).

> El **usuario** que registra lo toma el backend del token (no se manda). Se guarda como `registeredByUsername` y se envía a Aguas como `usuario`.

### Response `201 Created`

```json
{
  "data": {
    "id": "uuid-del-movimiento",
    "type": "LOAD",
    "routeCode": "179",
    "technician": "MARTIN RODRIGUEZ",
    "locationId": 2,
    "stateId": 2,
    "movementDate": "2026-06-18",
    "status": "REGISTERED",
    "serials": ["11177762", "LM29P05300840"],
    "registeredBy": "uuid-usuario",
    "registeredByUsername": "SORTIZ",
    "createdAt": "2026-06-18T09:12:00",
    "updatedAt": "2026-06-18T09:12:00"
  },
  "message": "Dispenser movement registered successfully"
}
```

> El movimiento se crea con `status: REGISTERED` y se manda a Aguas **en segundo plano**. El estado cambia a `SENT_TO_AGUAS` o `AGUAS_ERROR` unos instantes después — consultar el detalle o el listado para ver el estado final.

### Errores

**`400 Bad Request`** — faltan campos requeridos
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "serials: At least one dispenser serial is required"
}
```

---

## 3. Listar movimientos

```
GET /dispenser-movements
Authorization: Bearer <token>
```

### Query params

| Param       | Tipo    | Descripción                                              |
|-------------|---------|----------------------------------------------------------|
| `type`      | string  | Filtrar por `LOAD` o `UNLOAD`                           |
| `routeCode` | string  | Filtrar por número de reparto                           |
| `status`    | string  | `REGISTERED`, `SENT_TO_AGUAS`, `AGUAS_ERROR`            |
| `from`      | date    | Fecha desde (`YYYY-MM-DD`)                              |
| `to`        | date    | Fecha hasta (`YYYY-MM-DD`)                              |
| `page`      | integer | Página (default `0`)                                    |
| `size`      | integer | Tamaño (default `20`)                                   |
| `sort`      | string  | Orden (default `createdAt,desc`)                        |

**Ejemplo — movimientos con error de un reparto:**
```
GET /dispenser-movements?routeCode=179&status=AGUAS_ERROR
```

### Response `200 OK`
```json
{
  "data": {
    "content": [ /* array de movimientos */ ],
    "totalElements": 12,
    "totalPages": 1,
    "size": 20,
    "number": 0
  }
}
```

---

## 4. Ver los movimientos del día

Para que el usuario revise lo que fue cargando/descargando en el día (y pueda corregir), filtrar por la fecha de hoy:

```
GET /dispenser-movements?from=2026-06-18&to=2026-06-18
Authorization: Bearer <token>
```

Combinable con el tipo para separar cargas y descargas:

```
GET /dispenser-movements?from=2026-06-18&to=2026-06-18&type=LOAD     → cargas (salida) del día
GET /dispenser-movements?from=2026-06-18&to=2026-06-18&type=UNLOAD   → descargas (vuelta) del día
```

O por reparto puntual:
```
GET /dispenser-movements?from=2026-06-18&to=2026-06-18&routeCode=179
```

> Los movimientos cancelados quedan con `status: CANCELLED`. Si no querés mostrarlos, filtralos en el cliente o pedí solo los enviados/pendientes.

---

## 5. Ver detalle

```
GET /dispenser-movements/{id}
Authorization: Bearer <token>
```

Devuelve el mismo objeto que en la creación, con el estado actualizado.

---

## 6. Corregir un movimiento (modificar)

Aguas no permite editar un movimiento, así que "modificar" **elimina el anterior y crea uno nuevo** con los datos corregidos. Todo esto lo hace el backend en una sola llamada.

```
PUT /dispenser-movements/{id}
Authorization: Bearer <token>
```

Mismo body que el registro (`POST`):

```json
{
  "type": "LOAD",
  "routeCode": "179",
  "technician": "MARTIN RODRIGUEZ",
  "serials": ["11177762", "LM29P05300840"]
}
```

Qué hace internamente:
1. Borra el movimiento anterior en Aguas (usando el `aguasMovementId` guardado).
2. Marca el anterior como `CANCELLED`.
3. Crea uno nuevo con los datos corregidos y lo reenvía a Aguas.

Devuelve el **movimiento nuevo** (con su nuevo `id`).

### Errores
**`409 Conflict`** — no se pudo borrar el anterior en Aguas
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Could not delete dispenser movement {id} in Aguas. See integration logs for details."
}
```
En este caso el movimiento anterior **no** se canceló ni se creó el nuevo — se puede reintentar.

---

## 7. Cancelar un movimiento (eliminar)

```
DELETE /dispenser-movements/{id}
Authorization: Bearer <token>
```

- Si ya se envió a Aguas → lo borra en Aguas y marca el local como `CANCELLED`.
- Si nunca llegó a Aguas → solo lo marca `CANCELLED` local.

### Response `200 OK`
```json
{
  "data": {
    "id": "uuid-del-movimiento",
    "status": "CANCELLED",
    ...
  },
  "message": "Dispenser movement cancelled successfully"
}
```

### Errores
**`409 Conflict`** — ya estaba cancelado, o falló el borrado en Aguas.

---

## Referencia de campos

### Campos de la respuesta

| Campo                  | Tipo     | Descripción                                        |
|------------------------|----------|----------------------------------------------------|
| `id`                   | UUID     | ID del movimiento                                  |
| `type`                 | string   | `LOAD` o `UNLOAD`                                  |
| `routeCode`            | string   | Número de reparto                                  |
| `technician`           | string   | Nombre del técnico                                 |
| `locationId`           | integer  | Ubicación destino enviada a Aguas                  |
| `stateId`              | integer  | Estado destino enviado a Aguas                     |
| `movementDate`         | date     | Fecha del movimiento                               |
| `status`               | string   | Estado del envío (ver tabla)                       |
| `serials`              | array    | Números de serie escaneados                        |
| `aguasMovementId`      | string   | ID del movimiento en Aguas (`null` hasta enviarse) |
| `registeredBy`         | UUID     | ID del usuario que registró                        |
| `registeredByUsername` | string   | Usuario que registró (se envía a Aguas)            |
| `createdAt`            | datetime | Fecha de creación                                  |
| `updatedAt`            | datetime | Última actualización                               |

---

## Estados y envío a Aguas

| Estado          | Descripción                                            | Qué mostrar en la app        |
|-----------------|--------------------------------------------------------|------------------------------|
| `REGISTERED`    | Guardado local, enviando a Aguas                       | "Registrado / Enviando…"     |
| `SENT_TO_AGUAS` | Aceptado por Aguas correctamente                       | "Enviado ✓"                  |
| `AGUAS_ERROR`   | Falló el envío a Aguas (se reintenta automáticamente)  | "Error — reintentando"       |
| `CANCELLED`     | Cancelado (eliminado en Aguas)                         | "Cancelado"                  |

### Sobre el envío
- El registro se guarda **siempre**, aunque Aguas esté caído. El envío ocurre en segundo plano.
- Si falla, el sistema **reintenta automáticamente** cada 5 minutos (hasta 5 veces).
- Para ver el detalle técnico del envío (request, respuesta de Aguas, error), usar el módulo de integraciones:
  ```
  GET /integration-logs?entityId={movementId}
  ```
- Un envío fallido se puede reintentar manualmente:
  ```
  POST /integration-logs/{logId}/retry
  ```

### Mapeo con Aguas (referencia interna)
- `LOAD`   → `POST /api/aguas/registrar-salida-camion`
- `UNLOAD` → `POST /api/aguas/registrar-vuelta-camion`
