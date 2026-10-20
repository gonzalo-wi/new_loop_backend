# Flujo del Repartidor

Documentación para la app móvil del repartidor (`REPARTIDOR`).

---

## Cómo funciona

No hay notificaciones push. El repartidor abre la app y hace **pull-to-refresh** para ver si tiene controles pendientes de aprobación.

**Flujo completo:**

```
Controlador crea el control de entrada  →  status: PENDING_DRIVER_APPROVAL  ← acá aparece en la app
Repartidor aprueba                      →  status: ACCEPTED_BY_DRIVER → se envía a Aguas
Repartidor rechaza (con motivo)         →  status: REJECTED_BY_DRIVER (no se envía nada)
Controlador corrige y guarda            →  status: PENDING_DRIVER_APPROVAL ← le vuelve a aparecer
```

Solo los controles de **entrada** pasan por el repartidor; las salidas se envían a Aguas al crearse.

---

## 1. Ver controles pendientes

Al abrir la app (o al hacer refresh), llamar este endpoint para traer los controles que están esperando la aprobación del repartidor.

```
GET /stock-controls?routeId={routeId}&status=PENDING_DRIVER_APPROVAL
Authorization: Bearer <token>
```

> El `routeId` es el reparto asignado al repartidor. Se recomienda guardarlo al momento del login si el backend lo devuelve, o tenerlo hardcodeado por usuario.

### Response `200 OK`

```json
{
  "data": {
    "content": [
      {
        "id": "uuid-del-control",
        "type": "ENTRY",
        "status": "PENDING_DRIVER_APPROVAL",
        "branchId": "uuid-sucursal",
        "branchName": "Ciudadela",
        "routeId": "uuid-reparto",
        "routeCode": "R01",
        "controllerId": "uuid-controlador",
        "controlDate": "2026-06-25",
        "truckOrdered": true,
        "observations": "Sin novedades",
        "items": [
          {
            "id": "uuid-item",
            "productId": "uuid-producto",
            "productCode": "PRD001",
            "productName": "Bidón 20L",
            "productUnit": "UN",
            "totalQuantity": 10,
            "fullQuantity": 8,
            "exchangeQuantity": 2,
            "differenceQuantity": null,
            "observations": null
          }
        ],
        "confirmedAt": "2026-06-24T14:22:00",
        "approvedAt": null,
        "driverRejectionReason": null,
        "createdAt": "2026-06-24T10:30:00",
        "updatedAt": "2026-06-24T14:22:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0
  }
}
```

Si `content` está vacío → no hay controles pendientes.

---

## 2. Ver detalle de un control

```
GET /stock-controls/{id}
Authorization: Bearer <token>
```

Devuelve el mismo objeto que aparece en el listado.

---

## 3. Aprobar un control

```
POST /stock-controls/{id}/approve
Authorization: Bearer <token>
```

No requiere body.

Transiciona el control de `PENDING_DRIVER_APPROVAL` a `ACCEPTED_BY_DRIVER`.

### Response `200 OK`

```json
{
  "data": {
    "id": "uuid-del-control",
    "status": "ACCEPTED_BY_DRIVER",
    "approvedAt": "2026-06-24T15:10:00",
    ...
  },
  "message": "Stock control approved successfully"
}
```

### Errores

**`404 Not Found`** — control no encontrado

**`409 Conflict`** — el control no está en estado `PENDING_DRIVER_APPROVAL`
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Stock control {id} debe estar en estado PENDING_DRIVER_APPROVAL pero está en: ACCEPTED_BY_DRIVER"
}
```

---

## 4. Rechazar un control

```
POST /stock-controls/{id}/reject
Authorization: Bearer <token>
Content-Type: application/json

{ "reason": "Faltan 2 bidones de 20L" }
```

El motivo es obligatorio (máx. 500 caracteres). Transiciona el control de `PENDING_DRIVER_APPROVAL` a `REJECTED_BY_DRIVER` y lo saca de la lista de pendientes. No se envía nada a Aguas. El controlador ve el motivo, corrige el control y al guardarlo vuelve a `PENDING_DRIVER_APPROVAL`.

### Errores

**`400 Bad Request`** — falta el motivo o supera 500 caracteres

**`404 Not Found`** — control no encontrado

**`409 Conflict`** — el control no está en estado `PENDING_DRIVER_APPROVAL` (por ejemplo, ya fue aprobado o rechazado)

---

## Resumen de endpoints

| Método | Endpoint                          | Descripción                              |
|--------|-----------------------------------|------------------------------------------|
| `GET`  | `/stock-controls?routeId=...&status=PENDING_DRIVER_APPROVAL` | Ver controles pendientes del repartidor |
| `GET`  | `/stock-controls/{id}`            | Ver detalle de un control                |
| `POST` | `/stock-controls/{id}/approve`    | Aprobar un control                       |
| `POST` | `/stock-controls/{id}/reject`     | Rechazar un control con motivo           |
