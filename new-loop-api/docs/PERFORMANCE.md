# Desempeño de los controladores

Métricas por usuario para el gerente de planta: controles de stock (rechazos del repartidor, ediciones, correcciones) y carga de dispensers (duración de la carga, tiempo entre camiones). Spec: `specs/001-operational-metrics/spec.md`.

Los datos salen de dos lugares que **no se borran nunca**:
- **Controles de stock**: la tabla `operation_events`, donde cada envío, edición, rechazo, aprobación y corrección queda registrado en el momento. La historia anterior se importa una sola vez desde la auditoría (ver [Importar la historia](#3-importar-la-historia)).
- **Dispensers**: la tabla `dispenser_movements`, más la duración de la carga que manda la app (`loadDurationSeconds`, ver `DISPENSERS.md`).

---

## Quién ve qué

| Rol          | Ve                                                                 |
|--------------|--------------------------------------------------------------------|
| `ADMIN`      | Todas las sucursales, o la que filtre con `branchId`               |
| `SUPERVISOR` | Solo su sucursal (se ignora el `branchId` que mande)               |
| Otros roles  | Nada: responde `403`                                               |

- Un supervisor **sin sucursal asignada** recibe `403` con un mensaje que lo explica: hay que asignarle una.
- Los movimientos de dispensers cuyo reparto no se pudo identificar (sin sucursal) solo los ve un admin sin filtro de sucursal.

---

## Reglas de negocio

- **Control mal hecho**: un control que el repartidor rechazó al menos una vez, que se editó después de enviarlo o que un supervisor corrigió. Se cuenta **una vez**, el día en que se envió, sin importar cuántas veces pasó. Los errores se miden por cantidad de veces, no por ítems ni unidades.
- **Tasa de error**: controles mal hechos ÷ controles enviados.
- **Aprobado al primer intento**: control de entrada que el repartidor aprobó sin rechazarlo antes.
- **Tiempo de respuesta del repartidor**: desde que el control le llegó (al crearlo, o al reenviarlo después de un rechazo) hasta que lo aprobó o lo rechazó.
- **Duración de la carga**: la mide la app, desde el primer serial escaneado hasta que el envío sale bien. Más de 30 minutos es una **carga lenta** (lo normal son 10 a 15).
- **Tiempo entre camiones**: entre un movimiento y el siguiente del mismo usuario, el mismo día. Un hueco de **60 minutos o más es una pausa**: no entra en el promedio ni en la mediana, se cuenta aparte.
- **Correcciones de un movimiento**: cuentan como un solo movimiento, en la hora y para el usuario del movimiento original. No cuentan como cancelación.
- **Fechas**: todo en hora de Argentina. La semana por defecto va de lunes a domingo.

---

## 1. Resumen

```
GET /performance/summary
Authorization: Bearer <token>
```

### Query params

| Param          | Tipo | Requerido | Default            | Descripción                                                         |
|----------------|------|-----------|--------------------|---------------------------------------------------------------------|
| `from`         | date | No        | lunes de esta semana   | Desde (`YYYY-MM-DD`)                                            |
| `to`           | date | No        | domingo de esta semana | Hasta (`YYYY-MM-DD`), incluido                                  |
| `branchId`     | UUID | No        | todas              | Solo admins: filtra por sucursal                                    |
| `userId`       | UUID | No        | todos              | Filtra por usuario responsable (el controlador)                    |
| `controlType`  | string | No      | ambos              | `EXIT` o `ENTRY`: filtra solo las métricas de controles            |
| `movementType` | string | No      | ambos              | `LOAD` o `UNLOAD`: filtra solo las métricas de dispensers          |

El rango máximo es de **92 días**. Si `to` es anterior a `from` o el rango es más largo, responde `400`.

### Response `200 OK`

```json
{
  "data": {
    "from": "2026-10-26",
    "to": "2026-11-01",
    "users": [
      {
        "userId": "uuid-del-controlador",
        "username": "jperez",
        "controls": {
          "exitsSubmitted": 12,
          "entriesSubmitted": 11,
          "edits": 3,
          "resubmissions": 2,
          "rejectionRounds": 2,
          "firstPassAcceptancePercent": 81.8,
          "avgDriverResponseMinutes": 6.5,
          "corrections": 1,
          "faultyControls": 3,
          "errorPercent": 13.0,
          "controlsWithIntegrationErrors": 0
        },
        "dispensers": {
          "loads": 20,
          "unloads": 18,
          "serials": 412,
          "excludedUnregistered": 3,
          "excludedUnavailableInOdoo": 1,
          "avgLoadMinutes": 12.4,
          "medianLoadMinutes": 11.0,
          "slowLoads": 1,
          "avgGapMinutes": 9.8,
          "medianGapMinutes": 8.5,
          "pauses": 2,
          "cancellations": 1,
          "movementsWithIntegrationErrors": 0
        }
      }
    ],
    "coverage": [
      {
        "branchId": "uuid-de-la-sucursal",
        "branchName": "Ciudadela",
        "controlDate": "2026-10-27",
        "expectedEntries": 45,
        "missingEntries": 1,
        "missingRoutes": ["Rto 112"]
      }
    ],
    "topReasons": [
      { "kind": "REJECTION", "reason": "Faltan bidones", "userId": "uuid-del-repartidor", "username": "chofer1", "count": 4 }
    ]
  },
  "message": "Performance metrics retrieved successfully"
}
```

### Campos de `users[]`

Un elemento por usuario con actividad en el rango. La actividad sin usuario conocido (registros viejos) va junta bajo `"username": "Sin usuario"` y `userId: null`.

**Duraciones en minutos y porcentajes de 0 a 100, redondeados a un decimal.** Un promedio, mediana o porcentaje es `null` cuando no hay datos para calcularlo (por ejemplo, un usuario que solo cargó dispensers tiene `errorPercent: null`). Los contadores sin actividad son `0`.

| Campo (`controls`)               | Qué es                                                               |
|----------------------------------|----------------------------------------------------------------------|
| `exitsSubmitted`                 | Controles de salida enviados                                         |
| `entriesSubmitted`               | Controles de entrada enviados                                        |
| `edits`                          | Veces que se editó un control después de enviarlo                    |
| `resubmissions`                  | De esas ediciones, cuántas fueron para reenviar un control rechazado |
| `rejectionRounds`                | Rechazos del repartidor (cada rechazo cuenta)                        |
| `firstPassAcceptancePercent`     | % de controles aprobados al primer intento. `null` sin aprobaciones  |
| `avgDriverResponseMinutes`       | Tiempo de respuesta promedio del repartidor. `null` sin respuestas   |
| `corrections`                    | Correcciones de un supervisor después de mandar a Aguas              |
| `faultyControls`                 | Controles mal hechos (ver reglas)                                    |
| `errorPercent`                   | Tasa de error. `null` sin controles enviados                         |
| `controlsWithIntegrationErrors`  | Controles que fallaron al menos una vez al mandarse a Aguas          |

| Campo (`dispensers`)             | Qué es                                                                 |
|----------------------------------|------------------------------------------------------------------------|
| `loads` / `unloads`              | Cargas y descargas (las canceladas no cuentan)                         |
| `serials`                        | Series escaneadas                                                      |
| `excludedUnregistered`           | Series de descargas excluidas por no estar registradas en jMobile      |
| `excludedUnavailableInOdoo`      | Series de cargas excluidas por no estar disponibles en Odoo            |
| `avgLoadMinutes` / `medianLoadMinutes` | Duración de la carga. `null` si la app no mandó ninguna duración |
| `slowLoads`                      | Cargas de más de 30 minutos                                            |
| `avgGapMinutes` / `medianGapMinutes` | Tiempo entre camiones sin contar pausas. `null` si ningún día tuvo dos movimientos, o si todos los huecos fueron pausas |
| `pauses`                         | Huecos de 60 minutos o más                                             |
| `cancellations`                  | Movimientos anulados (las correcciones no cuentan)                     |
| `movementsWithIntegrationErrors` | Movimientos que fallaron al menos una vez al mandarse a Aguas u Odoo   |

### `coverage[]`: repartos que volvieron sin control de entrada

Un elemento por sucursal y fecha. Cada control de salida de un reparto con fecha D espera un control de entrada de ese reparto con fecha D (todo camión que sale, vuelve). Solo se cuentan fechas que **ya terminaron**: lo de hoy no aparece porque los camiones pueden seguir en la calle.

| Campo             | Qué es                                                    |
|-------------------|-----------------------------------------------------------|
| `controlDate`     | Fecha del control                                         |
| `expectedEntries` | Repartos que salieron para esa fecha                      |
| `missingEntries`  | De esos, cuántos no tienen control de entrada            |
| `missingRoutes`   | Códigos de los repartos que faltan                        |

### `topReasons[]`

Los motivos de rechazo (`REJECTION`) y de corrección (`CORRECTION`) más frecuentes, hasta 50, por **quien los escribió** (el repartidor o el supervisor). Se agrupan sin importar mayúsculas ni espacios de más.

---

## 2. Tendencia de un usuario

```
GET /performance/users/{userId}
Authorization: Bearer <token>
```

Mismos query params que el resumen, salvo `userId` (va en la ruta).

### Response `200 OK`

```json
{
  "data": {
    "userId": "uuid-del-controlador",
    "username": "jperez",
    "from": "2026-10-26",
    "to": "2026-11-01",
    "days": [
      { "date": "2026-10-26", "controls": { "...": "igual que en el resumen" }, "dispensers": { "...": "igual que en el resumen" } }
    ],
    "reasons": [
      { "kind": "REJECTION", "reason": "Faltan bidones", "userId": "uuid-del-controlador", "username": "jperez", "count": 3 }
    ]
  },
  "message": "User performance trend retrieved successfully"
}
```

- `days` tiene **todos** los días del rango, en orden, con ceros los días sin actividad.
- `reasons` son los motivos de rechazo y corrección de los controles de ese usuario.

---

## 3. Importar la historia

```
POST /performance/import
Authorization: Bearer <token>
```

**Solo `ADMIN`.** Se corre **una vez**, el mismo día que se despliega la versión que empieza a registrar eventos: la auditoría se borra a los 365 días y cada día de demora pierde un día de historia.

- Toma los controles de stock de la auditoría hasta el momento en que empezó el registro en vivo, así nada se cuenta dos veces.
- Se puede volver a correr sin duplicar nada: lo que ya se importó queda como está.
- Las entradas que no se pueden leer se saltean y se informan.
- La historia importada no tiene errores de integración (no se registraban) ni duración de cargas (no se medía).
- Puede tardar varios minutos: es un solo pedido que no devuelve nada hasta terminar. Si hay un proxy delante con timeout corto, correrlo directo contra el backend (puerto 8095).

### Response `200 OK`

```json
{
  "data": {
    "cutoff": "2026-10-29T08:12:44",
    "controls": 5120,
    "imported": 18433,
    "importedBefore": 0,
    "skipped": 2,
    "skippedByReason": { "MISSING_FIELDS": 2 }
  },
  "message": "Audit history imported successfully"
}
```

| Campo             | Qué es                                                                  |
|-------------------|-------------------------------------------------------------------------|
| `cutoff`          | Hasta cuándo se importó: el primer evento registrado en vivo            |
| `controls`        | Controles con historia en la auditoría                                  |
| `imported`        | Eventos importados en esta corrida                                      |
| `importedBefore`  | Eventos que ya se habían importado en una corrida anterior              |
| `skipped`         | Entradas de la auditoría que no se pudieron leer                        |
| `skippedByReason` | `UNREADABLE_JSON` (el dato guardado está roto) o `MISSING_FIELDS` (le falta el tipo, la sucursal o el reparto) |
