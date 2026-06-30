# Pedidos — Panel Admin

Gestión del catálogo de productos pedibles y visualización de pedidos.

---

## Índice

- [Productos pedibles](#productos-pedibles)
  - [Crear producto](#crear-producto)
  - [Listar productos](#listar-productos)
  - [Obtener producto por ID](#obtener-producto-por-id)
  - [Actualizar producto](#actualizar-producto)
  - [Desactivar producto](#desactivar-producto)
  - [Activar producto](#activar-producto)
- [Pedidos](#pedidos)
  - [Listar pedidos](#listar-pedidos)
  - [Obtener pedido por ID](#obtener-pedido-por-id)

---

## Productos pedibles

Catálogo de productos que los repartidores pueden pedir. Cada producto define si se puede pedir por **unidad**, por **bulto**, o ambas formas.

### Crear producto

```
POST /orderable-products
Authorization: Bearer <token>
```

#### Request body

```json
{
  "code": "SAB-POM",
  "name": "Saborizada Pomelo",
  "description": "Bebida saborizada sabor pomelo 500ml",
  "allowsUnit": true,
  "allowsBulk": true,
  "unitsPerBulk": 6
}
```

```json
{
  "code": "BOT-AZ-8L",
  "name": "Botellón Azul 8L",
  "description": "Botellón azul retornable de 8 litros",
  "allowsUnit": true,
  "allowsBulk": false
}
```

| Campo          | Tipo    | Requerido | Default  | Descripción                                              |
|----------------|---------|-----------|----------|----------------------------------------------------------|
| `code`         | string  | Sí        | —        | Código único del producto (máx. 50 caracteres)           |
| `name`         | string  | Sí        | —        | Nombre del producto                                      |
| `description`  | string  | No        | —        | Descripción (máx. 500 caracteres)                        |
| `allowsUnit`   | boolean | No        | `true`   | Permite pedir por unidad                                 |
| `allowsBulk`   | boolean | No        | `false`  | Permite pedir por bulto                                  |
| `unitsPerBulk` | integer | No        | —        | Cuántas unidades trae un bulto (requerido si `allowsBulk: true`) |

#### Response `201 Created`

```json
{
  "data": {
    "id": "uuid-del-producto",
    "code": "SAB-POM",
    "name": "Saborizada Pomelo",
    "description": "Bebida saborizada sabor pomelo 500ml",
    "allowsUnit": true,
    "allowsBulk": true,
    "unitsPerBulk": 6,
    "active": true,
    "createdAt": "2026-06-24T10:00:00",
    "updatedAt": "2026-06-24T10:00:00"
  },
  "message": "Orderable product created successfully"
}
```

#### Errores

**`409 Conflict`** — código ya existe
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Product code already exists: SAB-POM"
}
```

---

### Listar productos

```
GET /orderable-products
Authorization: Bearer <token>
```

#### Query params

| Param    | Tipo    | Descripción                                         |
|----------|---------|-----------------------------------------------------|
| `active` | boolean | Filtrar por estado: `true` (activos), `false` (inactivos). Sin filtro devuelve todos |
| `page`   | integer | Número de página, empieza en `0` (default: `0`)     |
| `size`   | integer | Resultados por página (default: `20`)               |
| `sort`   | string  | Ordenamiento (default: `name,asc`)                  |

**Ejemplo — solo activos:**
```
GET /orderable-products?active=true
```

#### Response `200 OK`

```json
{
  "data": {
    "content": [
      {
        "id": "uuid-del-producto",
        "code": "SAB-POM",
        "name": "Saborizada Pomelo",
        "description": "Bebida saborizada sabor pomelo 500ml",
        "allowsUnit": true,
        "allowsBulk": true,
        "unitsPerBulk": 6,
        "active": true,
        "createdAt": "2026-06-24T10:00:00",
        "updatedAt": "2026-06-24T10:00:00"
      }
    ],
    "totalElements": 10,
    "totalPages": 1,
    "size": 20,
    "number": 0
  }
}
```

---

### Obtener producto por ID

```
GET /orderable-products/{id}
Authorization: Bearer <token>
```

#### Response `200 OK`

Devuelve el mismo objeto que en la respuesta de creación.

#### Errores

**`404 Not Found`**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Orderable product not found: uuid-del-producto"
}
```

---

### Actualizar producto

```
PATCH /orderable-products/{id}
Authorization: Bearer <token>
```

Todos los campos son opcionales — solo se actualizan los que se envíen.

#### Request body

```json
{
  "name": "Saborizada Pomelo 500ml",
  "allowsBulk": true,
  "unitsPerBulk": 12
}
```

| Campo          | Tipo    | Descripción                            |
|----------------|---------|----------------------------------------|
| `code`         | string  | Nuevo código (máx. 50 caracteres)      |
| `name`         | string  | Nuevo nombre                           |
| `description`  | string  | Nueva descripción (máx. 500)           |
| `allowsUnit`   | boolean | Habilitar/deshabilitar pedido por unidad |
| `allowsBulk`   | boolean | Habilitar/deshabilitar pedido por bulto  |
| `unitsPerBulk` | integer | Unidades por bulto                     |

#### Response `200 OK`

Devuelve el producto actualizado con el mismo formato.

---

### Desactivar producto

```
PATCH /orderable-products/{id}/deactivate
Authorization: Bearer <token>
```

No requiere body. Responde `204 No Content`.

---

### Activar producto

```
PATCH /orderable-products/{id}/activate
Authorization: Bearer <token>
```

No requiere body. Responde `204 No Content`.

---

## Pedidos

El admin puede consultar todos los pedidos realizados por los repartidores.

### Listar pedidos

```
GET /orders
Authorization: Bearer <token>
```

#### Query params

| Param     | Tipo    | Descripción                                              |
|-----------|---------|----------------------------------------------------------|
| `routeId` | UUID    | Filtrar por reparto                                      |
| `status`  | string  | Filtrar por estado: `PENDING`, `IN_PROGRESS`, `COMPLETED`|
| `from`    | date    | Fecha desde (`YYYY-MM-DD`)                               |
| `to`      | date    | Fecha hasta (`YYYY-MM-DD`)                               |
| `page`    | integer | Número de página (default: `0`)                          |
| `size`    | integer | Resultados por página (default: `20`)                    |
| `sort`    | string  | Ordenamiento (default: `createdAt,desc`)                 |

**Ejemplo — pedidos de hoy:**
```
GET /orders?from=2026-06-24&to=2026-06-24
```

**Ejemplo — pedidos pendientes de un reparto:**
```
GET /orders?routeId=uuid-del-reparto&status=PENDING
```

#### Response `200 OK`

```json
{
  "data": {
    "content": [
      {
        "id": "uuid-del-pedido",
        "routeId": "uuid-del-reparto",
        "routeCode": "R01",
        "status": "PENDING",
        "orderDate": "2026-06-24",
        "observations": null,
        "items": [
          {
            "id": "uuid-del-item",
            "productId": "uuid-del-producto",
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
      }
    ],
    "totalElements": 5,
    "totalPages": 1,
    "size": 20,
    "number": 0
  }
}
```

---

### Obtener pedido por ID

```
GET /orders/{id}
Authorization: Bearer <token>
```

Devuelve el mismo objeto que aparece en el listado.

---

## Estados del pedido

| Estado       | Descripción                          |
|--------------|--------------------------------------|
| `PENDING`    | Creado, esperando ser procesado      |
| `IN_PROGRESS`| En preparación                       |
| `COMPLETED`  | Entregado / finalizado               |

### Flujo de estados

```
PENDING  →  IN_PROGRESS  →  COMPLETED
```
