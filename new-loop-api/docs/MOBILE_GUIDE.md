# LOOP — Guía Mobile (React Native)

Documentación completa para la app del repartidor.

**Base URL:** `http://<servidor>:8080`

Todos los endpoints (excepto login) requieren el header:
```
Authorization: Bearer <token>
Content-Type: application/json
```

---

## Índice

1. [Login](#1-login)
2. [Buscar el reparto del repartidor](#2-buscar-el-reparto-del-repartidor)
3. [Ver controles pendientes de aprobación](#3-ver-controles-pendientes-de-aprobación)
4. [Ver detalle de un control](#4-ver-detalle-de-un-control)
5. [Aprobar un control](#5-aprobar-un-control)
6. [Ver productos para el control de stock](#6-ver-productos-para-el-control-de-stock)
7. [Pedidos — Ver catálogo de productos pedibles](#7-pedidos--ver-catálogo-de-productos-pedibles)
8. [Pedidos — Crear pedido](#8-pedidos--crear-pedido)
9. [Pedidos — Ver mis pedidos](#9-pedidos--ver-mis-pedidos)
10. [Referencia de estados](#10-referencia-de-estados)
11. [Manejo de errores](#11-manejo-de-errores)

---

## 1. Login

```
POST /auth/login
```

No requiere Authorization header.

### Body
```json
{
  "username": "juan.perez",
  "password": "mipassword"
}
```

### Response `200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "id": "uuid-del-usuario",
  "name": "Juan Pérez",
  "username": "juan.perez",
  "role": "REPARTIDOR"
}
```

**Guardar en AsyncStorage:**
- `token` → para el header Authorization
- `id` → para buscar el reparto
- `name` → para mostrar en pantalla
- `role` → para navegar a la pantalla correcta

### Errores
| Status | Motivo |
|--------|--------|
| `401` | Usuario o contraseña incorrectos |
| `403` | Usuario desactivado |
| `400` | Campos vacíos |

---

## 2. Buscar el reparto del repartidor

Después del login, buscar el reparto que tiene asignado al usuario.

```
GET /routes
Authorization: Bearer <token>
```

### Response `200 OK`
```json
{
  "data": {
    "content": [
      {
        "id": "uuid-del-reparto",
        "code": "R01",
        "branchId": "uuid-sucursal",
        "branchName": "Ciudadela",
        "branchCode": "CIU",
        "driverId": "uuid-del-repartidor",
        "driverName": "Juan Pérez",
        "truckPlate": "AB123CD",
        "active": true,
        "observations": null
      }
    ],
    "totalElements": 5,
    "totalPages": 1,
    "size": 20,
    "number": 0
  },
  "message": "Routes retrieved successfully"
}
```

**Cómo encontrar el reparto del usuario logueado:**
```js
const myRoute = data.content.find(r => r.driverId === userId)
```

**Guardar en AsyncStorage:**
- `routeId` → para filtrar controles y pedidos
- `routeCode` → para mostrar en pantalla
- `branchId` → para crear controles

Si `myRoute` es undefined → el usuario no tiene reparto asignado todavía, mostrar mensaje.

---

## 3. Ver controles pendientes de aprobación

Llamar al abrir la app y en cada pull-to-refresh. Si hay resultados, mostrar badge o pantalla de alerta al repartidor.

```
GET /stock-controls?routeId={routeId}&status=PENDING_DRIVER_APPROVAL
Authorization: Bearer <token>
```

### Response `200 OK`
```json
{
  "data": {
    "content": [
      {
        "id": "uuid-del-control",
        "type": "EXIT",
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

## 4. Ver detalle de un control

```
GET /stock-controls/{id}
Authorization: Bearer <token>
```

Devuelve el mismo objeto que en el listado.

### Errores
| Status | Motivo |
|--------|--------|
| `404` | Control no encontrado |

---

## 5. Aprobar un control

```
POST /stock-controls/{id}/approve
Authorization: Bearer <token>
```

No requiere body.

### Response `200 OK`
```json
{
  "data": {
    "id": "uuid-del-control",
    "status": "ACCEPTED_BY_DRIVER",
    "approvedAt": "2026-06-24T15:10:00"
  },
  "message": "Stock control approved successfully"
}
```

### Errores
| Status | Motivo |
|--------|--------|
| `404` | Control no encontrado |
| `409` | El control no está en estado `PENDING_DRIVER_APPROVAL` |

---

## 6. Ver productos para el control de stock

Cargar al abrir la pantalla de nuevo control. Vienen ordenados por `displayOrder`.

```
GET /products?size=200&sort=displayOrder,asc
Authorization: Bearer <token>
```

### Response `200 OK`
```json
{
  "data": {
    "content": [
      {
        "id": "uuid-producto",
        "code": "PRD001",
        "name": "Bidón 20L",
        "displayOrder": 1,
        "description": "Bidón retornable de 20 litros",
        "type": "RETORNABLE",
        "unit": "UN",
        "packQuantity": 1,
        "active": true
      }
    ]
  }
}
```

Mostrar solo los que tengan `active: true`.

El `id` de cada producto es el `productId` que va en los items del control.

---

## 7. Pedidos — Ver catálogo de productos pedibles

```
GET /orderable-products?active=true
Authorization: Bearer <token>
```

### Response `200 OK`
```json
{
  "data": {
    "content": [
      {
        "id": "uuid-producto-pedible",
        "code": "SAB-POM",
        "name": "Saborizada Pomelo",
        "description": "Bebida saborizada 500ml",
        "allowsUnit": true,
        "allowsBulk": true,
        "unitsPerBulk": 6,
        "active": true
      },
      {
        "id": "uuid-producto-pedible-2",
        "code": "BOT-AZ-8L",
        "name": "Botellón Azul 8L",
        "description": "Botellón retornable 8 litros",
        "allowsUnit": true,
        "allowsBulk": false,
        "unitsPerBulk": null,
        "active": true
      }
    ]
  }
}
```

**Lógica de UI por producto:**
- `allowsUnit: true` → mostrar campo "Unidades"
- `allowsBulk: true` → mostrar campo "Bultos" + texto "`unitsPerBulk` unidades por bulto"
- `allowsBulk: false` → ocultar campo "Bultos"

---

## 8. Pedidos — Crear pedido

```
POST /orders
Authorization: Bearer <token>
```

### Body
```json
{
  "routeId": "uuid-del-reparto",
  "orderDate": "2026-06-24",
  "observations": "Urgente para mañana",
  "items": [
    {
      "productId": "uuid-producto-pedible",
      "unitQuantity": 3,
      "bulkQuantity": 1
    },
    {
      "productId": "uuid-producto-pedible-2",
      "unitQuantity": 5,
      "bulkQuantity": null
    }
  ]
}
```

| Campo | Requerido | Descripción |
|-------|-----------|-------------|
| `routeId` | Sí | El `routeId` guardado al iniciar sesión |
| `orderDate` | No | Default: hoy |
| `observations` | No | Máx. 500 caracteres |
| `items` | Sí | Al menos un producto |
| `items[].productId` | Sí | ID del producto pedible |
| `items[].unitQuantity` | No | Cantidad en unidades (≥ 0) |
| `items[].bulkQuantity` | No | Cantidad en bultos (≥ 0) |

> Al menos uno de `unitQuantity` o `bulkQuantity` debe ser mayor a 0 por item.

### Response `201 Created`
```json
{
  "data": {
    "id": "uuid-del-pedido",
    "routeId": "uuid-del-reparto",
    "routeCode": "R01",
    "status": "PENDING",
    "orderDate": "2026-06-24",
    "items": [
      {
        "id": "uuid-item",
        "productId": "uuid-producto-pedible",
        "productCode": "SAB-POM",
        "productName": "Saborizada Pomelo",
        "allowsUnit": true,
        "allowsBulk": true,
        "unitsPerBulk": 6,
        "unitQuantity": 3,
        "bulkQuantity": 1
      }
    ],
    "createdAt": "2026-06-24T08:00:00",
    "updatedAt": "2026-06-24T08:00:00"
  },
  "message": "Order created successfully"
}
```

### Errores
| Status | Motivo |
|--------|--------|
| `404` | Reparto o producto no encontrado |
| `409` | Producto no permite ese tipo de cantidad (unit/bulk) |
| `400` | Campos requeridos faltantes |

---

## 9. Pedidos — Ver mis pedidos

```
GET /orders?routeId={routeId}
Authorization: Bearer <token>
```

Filtros opcionales:
```
GET /orders?routeId={routeId}&status=PENDING
GET /orders?routeId={routeId}&from=2026-06-24&to=2026-06-24
```

### Response `200 OK`
```json
{
  "data": {
    "content": [ /* array de pedidos */ ],
    "totalElements": 3,
    "totalPages": 1,
    "size": 20,
    "number": 0
  }
}
```

---

## 10. Referencia de estados

### Controles de stock
| Estado | Descripción | Acción disponible |
|--------|-------------|-------------------|
| `CONTROLLED` | Creado, no listo para el repartidor | — |
| `PENDING_DRIVER_APPROVAL` | Confirmado, esperando aprobación | **Aprobar** |
| `ACCEPTED_BY_DRIVER` | Aprobado por el repartidor | — |
| `REJECTED_BY_DRIVER` | Rechazado | — |
| `CANCELLED` | Cancelado | — |

### Pedidos
| Estado | Descripción |
|--------|-------------|
| `PENDING` | Enviado, sin procesar |
| `IN_PROGRESS` | En preparación |
| `COMPLETED` | Finalizado |

---

## 11. Manejo de errores

Todos los errores tienen el mismo formato:

```json
{
  "timestamp": "2026-06-24T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Bad credentials",
  "path": "/auth/login"
}
```

| Status | Qué hacer en la app |
|--------|---------------------|
| `400` | Mostrar mensaje de validación al usuario |
| `401` | Redirigir al login (token expirado o credenciales incorrectas) |
| `403` | Mostrar "Sin permisos" |
| `404` | Mostrar "No encontrado" |
| `409` | Mostrar el `message` del error al usuario |
| `500` | Mostrar "Error del servidor, intentá de nuevo" |

---

## Flujo completo del repartidor

```
App abre
  └─► POST /auth/login
        └─► Guardar token, userId, role
              └─► GET /routes → encontrar routeId donde driverId === userId
                    └─► Guardar routeId, branchId

Pantalla principal (pull-to-refresh)
  └─► GET /stock-controls?routeId={routeId}&status=PENDING_DRIVER_APPROVAL
        ├─► Sin resultados → "No hay controles pendientes"
        └─► Con resultados → mostrar lista
              └─► Repartidor toca un control
                    └─► GET /stock-controls/{id} → ver detalle
                          └─► Repartidor aprueba
                                └─► POST /stock-controls/{id}/approve

Pantalla de pedidos
  └─► GET /orderable-products?active=true → cargar catálogo
        └─► Repartidor arma el pedido
              └─► POST /orders
```
