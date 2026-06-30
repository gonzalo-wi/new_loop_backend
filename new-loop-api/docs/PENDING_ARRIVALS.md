# Repartos pendientes de llegar

Endpoint para visualizar, durante el control de entrada, cuántos repartos faltan llegar a la fábrica y cuáles son.

---

## Cómo funciona

Cada reparto que sale tiene un **control de SALIDA** (creado el día anterior para el día de hoy). Cuando el camión vuelve, se le hace un **control de ENTRADA**.

La diferencia entre salidas y entradas del día = los repartos que **todavía no llegaron**.

```
Salidas del día      →  cuántos repartos se esperan (totalExpected)
Entradas del día     →  cuántos ya volvieron (arrived)
Salidas sin entrada  →  los que faltan llegar (pending + pendingRoutes)
```

---

## Endpoint

```
GET /stock-controls/pending-arrivals
Authorization: Bearer <token>
```

### Query params

| Param  | Tipo | Requerido | Default | Descripción                          |
|--------|------|-----------|---------|--------------------------------------|
| `date` | date | No        | hoy     | Fecha a consultar (`YYYY-MM-DD`)     |

**Ejemplos:**
```
GET /stock-controls/pending-arrivals
GET /stock-controls/pending-arrivals?date=2026-06-29
```

---

## Response `200 OK`

```json
{
  "data": {
    "date": "2026-06-29",
    "totalExpected": 50,
    "arrived": 30,
    "pending": 20,
    "pendingRoutes": [
      {
        "routeId": "uuid-del-reparto",
        "routeCode": "9",
        "branchId": "uuid-de-la-sucursal",
        "branchName": "Ciudadela",
        "exitControlId": "uuid-del-control-de-salida",
        "controlDate": "2026-06-29"
      }
    ]
  },
  "message": "Pending arrivals retrieved successfully"
}
```

### Campos del resumen

| Campo           | Tipo    | Descripción                                              |
|-----------------|---------|----------------------------------------------------------|
| `date`          | date    | Fecha consultada                                         |
| `totalExpected` | integer | Total de repartos que salieron (se esperan de vuelta)   |
| `arrived`       | integer | Cuántos ya volvieron (tienen control de entrada)        |
| `pending`       | integer | Cuántos **faltan llegar**                                |
| `pendingRoutes` | array   | Lista de los repartos que faltan llegar                 |

### Campos de cada reparto pendiente (`pendingRoutes[]`)

| Campo           | Tipo   | Descripción                                       |
|-----------------|--------|---------------------------------------------------|
| `routeId`       | UUID   | ID del reparto                                    |
| `routeCode`     | string | Código del reparto (ej. `9`)                      |
| `branchId`      | UUID   | ID de la sucursal                                 |
| `branchName`    | string | Nombre de la sucursal                             |
| `exitControlId` | UUID   | ID del control de salida de ese reparto           |
| `controlDate`   | date   | Fecha del control                                 |

---

## Cómo usarlo en la app

1. En la pantalla de control de entrada, llamar el endpoint (pull-to-refresh o cada X segundos).
2. Mostrar el contador: **"Faltan llegar: {pending} de {totalExpected}"**.
3. Listar los repartos de `pendingRoutes` para que el controlador vea cuáles son los que faltan (por `routeCode` y `branchName`).
4. A medida que se van creando los controles de entrada, esos repartos desaparecen de la lista automáticamente.

> Los controles cancelados (`CANCELLED`) no se cuentan en ningún lado.
