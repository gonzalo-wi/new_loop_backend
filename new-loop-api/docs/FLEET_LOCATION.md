# Ubicación de camiones (Powerfleet)

Endpoint para consultar en tiempo real dónde está un camión, usando su patente. Pensado para dos consumidores distintos: el **panel admin** (cualquier reparto) y la **app mobile** (el propio camión del repartidor logueado).

---

## Endpoint

```
GET /fleet/location/{licensePlate}
Authorization: Bearer <token>
```

No hace falta ningún dato más — la patente es todo lo que se necesita.

### Response `200 OK`
```json
{
  "data": {
    "licensePlate": "AB123CD",
    "lat": -34.6037,
    "lng": -58.3816,
    "address": "Av. Corrientes 1234, CABA",
    "speed": 45,
    "engineOn": true,
    "stateIcon": "moving",
    "driver": "Juan Pérez",
    "gpsDateTime": "2026-07-16T18:20:00",
    "direction": 180
  },
  "message": "Truck location retrieved successfully"
}
```

| Campo         | Tipo    | Descripción                                              |
|---------------|---------|-----------------------------------------------------------|
| `licensePlate`| string  | Patente consultada                                         |
| `lat` / `lng` | number  | Coordenadas — usar directo en el mapa                      |
| `address`     | string  | Dirección aproximada (geocoding de Powerfleet)              |
| `speed`       | integer | Velocidad actual (km/h)                                    |
| `engineOn`    | boolean | Si el motor está encendido                                 |
| `stateIcon`   | string  | Estado del vehículo (para elegir el ícono en el mapa)      |
| `driver`      | string  | Nombre del conductor según Powerfleet (puede no coincidir con el usuario de LOOP) |
| `gpsDateTime` | string  | Fecha/hora del último reporte GPS                           |
| `direction`   | integer | Rumbo en grados (0-360), para rotar el ícono del camión     |

### Errores

**`404 Not Found`** — no se encontró esa patente en la flota
```json
{ "status": 404, "error": "Not Found", "message": "No location found for truck with license plate: AB123CD" }
```

**`502 Bad Gateway`** — Powerfleet no respondió o hubo un error de comunicación
```json
{ "status": 502, "error": "Bad Gateway", "message": "Could not retrieve truck location from Powerfleet" }
```
> Mostrar algo tipo "No se pudo obtener la ubicación, intentá de nuevo" — no es un error del reparto, es el proveedor externo caído.

---

## 1. Panel Admin — buscar cualquier reparto en el mapa

El admin elige un reparto de una lista o buscador, y el frontend arma el flujo:

```
1. GET /routes  (o el buscador que ya tengan)     →  obtener la lista de repartos
2. El admin elige un reparto                       →  usar su campo `truckPlate`
3. GET /fleet/location/{truckPlate}                →  mostrar en el mapa
```

**Ejemplo:**
```js
const route = await getRoute(routeId);        // trae truckPlate
if (!route.truckPlate) {
  // mostrar "Este reparto no tiene patente cargada"
  return;
}
const location = await getFleetLocation(route.truckPlate);
// pintar location.lat / location.lng en el mapa
```

> Si el reparto no tiene `truckPlate` cargado (campo puede venir `null`), ni siquiera hacer la llamada — mostrar aviso en la UI.

**Refresco:** para que se vea como "en vivo", conviene volver a pedir la ubicación cada 15-30 segundos mientras el admin tiene el mapa abierto (polling simple, no hace falta websocket).

---

## 2. App Mobile — "Buscar mi camión"

El repartidor ya tiene su reparto guardado en sesión desde el login (ver [ROUTES_MOBILE.md](ROUTES_MOBILE.md)). El botón "Buscar mi camión" no pide nada al usuario — usa los datos que ya tiene guardados.

```
1. Login                                          →  ya guardado: routeId
2. (si no se guardó antes) GET /routes/{routeId}  →  obtener truckPlate
3. Botón "Buscar mi camión" toca                   →  GET /fleet/location/{truckPlate}
4. Mostrar el mapa centrado en location.lat/lng
```

**Recomendación:** guardar el `truckPlate` en el mismo storage local donde ya guardan `routeId` / `routeCode` (ver el flujo de [ROUTES_MOBILE.md](ROUTES_MOBILE.md)), así no hay que pedir el reparto de nuevo cada vez que se toca el botón.

```js
// al login / al cargar el reparto
await AsyncStorage.setItem('truckPlate', route.truckPlate);

// al tocar "Buscar mi camión"
const plate = await AsyncStorage.getItem('truckPlate');
if (!plate) {
  // "Tu reparto no tiene patente cargada, contactá al admin"
  return;
}
const location = await getFleetLocation(plate);
```

### Casos a manejar en la UI mobile
- **Sin `truckPlate` cargado** → mensaje claro, no intentar la llamada.
- **`404`** → "No se encontró el camión en el sistema de flota" (la patente puede estar mal cargada o el vehículo no tiene GPS activo).
- **`502`** → "No se pudo conectar con el sistema de flota, probá de nuevo".
- **Éxito** → centrar mapa en `lat`/`lng`, mostrar `address` como texto, usar `direction` para rotar el ícono del camión si el mapa lo soporta.

---

## Notas técnicas
- El token de autenticación contra Powerfleet lo maneja el backend (se cachea y renueva solo) — el frontend/mobile nunca ven ni manejan credenciales de Powerfleet.
- La búsqueda es **case-insensitive** en la patente (`ab123cd` funciona igual que `AB123CD`).
- No hay caché de ubicación en el backend — cada request pega en vivo a Powerfleet. Evitar pedir la ubicación en loops muy cortos (menos de ~10s) para no saturar el proveedor.
