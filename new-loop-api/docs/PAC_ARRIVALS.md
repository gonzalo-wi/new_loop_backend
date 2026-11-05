# Llegadas de rutas para PAC

Endpoint de solo lectura para que el backend de PAC (tesorería) sepa, para un día, qué rutas **ya volvieron** y a qué hora. Con eso PAC distingue una ruta que "todavía no depositó porque sigue en la calle" de una que "ya volvió y no depositó". Spec: `specs/004-pac-arrivals/`.

Una **llegada** es el control de **entrada** (ENTRY) de una ruta para esa fecha de control, que no esté cancelado. La hora de llegada es cuándo se cargó ese control, en hora de Argentina.

---

## Autenticación

No usa el JWT de los usuarios de Loop (la sesión vence a medianoche y PAC no tiene usuario). Usa **HTTP Basic** con credenciales propias de la integración, que se configuran en el servidor (ver "Configuración").

```
GET /integrations/pac/arrivals?date=2026-11-02
Authorization: Basic <base64(usuario:clave)>
```

- Estas credenciales **solo** sirven en `/integrations/pac/**`; en cualquier otro endpoint dan 401.
- Un JWT de usuario y las credenciales de un usuario de Loop por Basic dan **401** acá.
- Sin credenciales, o con credenciales incorrectas: `401` con el cuerpo de error habitual y `message: "Invalid integration credentials"`.
- Si `PAC_INTEGRATION_USERNAME` o `PAC_INTEGRATION_PASSWORD` están vacíos (o son solo espacios), el endpoint queda deshabilitado: responde 401 a todo y la aplicación arranca igual (con un WARN en el log).
- Las credenciales se comparan en tiempo constante.

---

## Request

| Parámetro | Tipo | Descripción |
|-----------|------|-------------|
| `date`    | `YYYY-MM-DD` (opcional) | Fecha de control. Si falta, se usa **hoy en hora de Argentina** (no la fecha UTC del servidor). Una fecha mal formada responde `400`; una fecha futura devuelve lista vacía. |

## Response `200 OK`

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

| Campo | Descripción |
|-------|-------------|
| `routeNumber` | Los dígitos del código de la ruta (`"Rto 179"` → `179`). PAC y Aguas usan los mismos números. |
| `routeCode` | Código de la ruta tal como está en Loop |
| `branchId` / `branchName` | Reparto (sucursal) de la ruta |
| `arrivedAt` | Hora de llegada con offset explícito (`-03:00`), sin fracciones de segundo |

Si no llegó ninguna ruta, `data` es `[]`. La lista viene ordenada por hora de llegada (y, si empatan, por código de ruta y reparto).

---

## Reglas

- Quedan afuera los controles **cancelados** y las rutas cuyo código **no tiene dígitos** (no hay número que mandar).
- Cualquier otro estado cuenta (rechazado por el repartidor, error de Aguas, etc.): el camión volvió igual.
- Los números de ruta pueden repetirse (el mismo número en dos repartos, o `"179"` y `"R-179"` en uno): se devuelve **una entrada por control**, sin unificar. PAC los distingue por reparto.
- No hay filtro por reparto: PAC filtra por número de ruta y mapea los repartos de su lado.
- Cada llamada **no** se registra en la auditoría (PAC consulta una vez por minuto). Los fallos se loguean con su causa.

## Limitación conocida

La hora de llegada es cuándo se **creó** el control de entrada. Si un control se cargó tarde (o se le cambió la fecha), la hora será posterior a la llegada real. Se acepta: es la única hora que Loop registra.

---

## Configuración

Las credenciales viven en el `.env` del servidor (nunca en el repo), junto al `docker-compose.yml`:

```
PAC_INTEGRATION_USERNAME=<usuario acordado con PAC>
PAC_INTEGRATION_PASSWORD=<clave larga y aleatoria>
```

Después de editar el `.env`, recrear el contenedor (un `restart` no recarga el `.env`):

```
docker compose up -d --force-recreate app
```

Verificar que quedaron cargadas sin imprimirlas:

```
docker exec loop-api-app sh -c 'printf %s "$PAC_INTEGRATION_USERNAME" | wc -c'
```

## Verificación con curl

```
# Con las credenciales: 200, las llegadas del día y el tiempo de respuesta
curl -u "$PAC_USER:$PAC_PASS" -w '\n%{http_code} %{time_total}s\n' \
  "http://192.168.0.42:8095/integrations/pac/arrivals?date=2026-11-02"

# Sin credenciales: 401
curl -i "http://192.168.0.42:8095/integrations/pac/arrivals"

# Con un JWT de usuario: 401
curl -i -H "Authorization: Bearer <token>" "http://192.168.0.42:8095/integrations/pac/arrivals"
```

## Sentido inverso: rendición del repartidor

LOOP también le pregunta a PAC, con estas mismas credenciales, la rendición de cada reparto
(`GET /api/integrations/loop/settlement` en PAC). Ver `DRIVER_FLOW.md` §5 y la spec 007.
