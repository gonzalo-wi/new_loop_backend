# Dispensers no registrados — cambios a implementar en la app mobile

Handoff para el desarrollo de la app. El backend ya está implementado y desplegado; esto describe **qué tiene que cambiar del lado del cliente**.

Doc del módulo completo: [DISPENSERS.md](./DISPENSERS.md)

---

## Índice

- [Qué cambió y por qué](#qué-cambió-y-por-qué)
- [Resumen de cambios en el contrato](#resumen-de-cambios-en-el-contrato)
- [Los tres casos que devuelve el POST](#los-tres-casos-que-devuelve-el-post)
- [Qué hay que implementar](#qué-hay-que-implementar)
- [Casos borde](#casos-borde)
- [Cómo probarlo](#cómo-probarlo)
- [Checklist](#checklist)

---

## Qué cambió y por qué

Al registrar una **descarga** (`UNLOAD`), el backend ahora consulta un servicio externo (jMobile) que lista los dispensers marcados como **"no registrados"** para esa fecha. Los seriales escaneados que aparecen en esa lista **no se envían ni a Aguas ni a Odoo**: quedan guardados en el movimiento como excluidos, pero afuera del payload.

Esto pasa **durante el `POST`**, antes de responder. O sea que la respuesta del create ya trae el resultado real: la app no necesita esperar ni refrescar para saber si un dispenser quedó afuera.

Las **cargas (`LOAD`) no cambian en absoluto**. Toda esta lógica aplica solo a `UNLOAD`.

---

## Resumen de cambios en el contrato

**No cambia nada del request.** El body del `POST /dispenser-movements` y del `PUT /dispenser-movements/{id}` es exactamente el mismo de siempre.

En la **respuesta** hay dos novedades, ambas aditivas:

| Cambio | Detalle |
|---|---|
| Campo nuevo `excludedSerials` | `string[]`. Los seriales escaneados que quedaron afuera del envío. Vacío (`[]`) en el caso normal. Aparece en **todas** las respuestas de movimiento: POST, PUT, DELETE, `GET /{id}` y cada ítem del listado. |
| Valor nuevo de `status` | `SKIPPED_UNREGISTERED` — se sumó a los ya existentes (`REGISTERED`, `SENT_TO_AGUAS`, `AGUAS_ERROR`, `CANCELLED`). |

> ⚠️ **Lo primero a revisar:** si el `status` se deserializa a un enum estricto (`enumValueOf`, Gson/Moshi sin fallback, `when` exhaustivo sin `else`), el valor nuevo **rompe el parseo**. Hay que agregarlo o poner un fallback tolerante. Es el único cambio que puede romper algo existente.

---

## Los tres casos que devuelve el POST

Todos son `201 Created` con el envelope habitual `{ "data": ..., "message": ... }`. Lo que cambia es `status` + `excludedSerials`.

### Caso 1 — Normal (nada excluido)

```json
{
  "data": {
    "id": "e8868947-5776-48fc-a25f-6c8c78906905",
    "type": "UNLOAD",
    "routeCode": "199",
    "status": "REGISTERED",
    "serials": ["LM29H01181809", "11177762"],
    "excludedSerials": []
  },
  "message": "Dispenser movement registered successfully"
}
```

Comportamiento actual, sin cambios: se envía a Aguas en segundo plano y el estado pasa a `SENT_TO_AGUAS` o `AGUAS_ERROR` unos instantes después. Acá el "Enviando…" sigue siendo correcto.

### Caso 2 — Parcial (algunos excluidos, el resto se envía)

```json
{
  "data": {
    "id": "e8868947-5776-48fc-a25f-6c8c78906905",
    "type": "UNLOAD",
    "routeCode": "199",
    "status": "REGISTERED",
    "serials": ["LM29H01181809", "LM29H04160827"],
    "excludedSerials": ["LM29H04160827"]
  },
  "message": "Dispenser movement registered successfully"
}
```

`status` sigue siendo `REGISTERED` porque el movimiento **sí se envía**, pero solo con `LM29H01181809`. Hay que avisarle al usuario que `LM29H04160827` quedó afuera.

> `serials` siempre trae **todo lo que se escaneó**; `excludedSerials` es un subconjunto. Lo que realmente se envió es la diferencia: `serials - excludedSerials`.

### Caso 3 — Todo excluido (no se envió nada)

```json
{
  "data": {
    "id": "e8868947-5776-48fc-a25f-6c8c78906905",
    "type": "UNLOAD",
    "routeCode": "199",
    "status": "SKIPPED_UNREGISTERED",
    "serials": ["LM29H04160827"],
    "excludedSerials": ["LM29H04160827"],
    "aguasMovementId": null
  },
  "message": "Dispenser movement registered successfully"
}
```

**Este es el caso que hoy queda colgado en "Enviando…" para siempre.** No hubo llamada a Aguas ni a Odoo, y no va a haber ninguna después: es un estado **final**, no transitorio. No tiene sentido reintentar ni esperar.

El movimiento igual queda guardado en LOOP para trazabilidad.

---

## Qué hay que implementar

### 1. Aceptar el status nuevo

Agregar `SKIPPED_UNREGISTERED` al enum/parser de estados. Si hay un `when`/`switch` exhaustivo sobre el status, cubrir la rama nueva. Idealmente además dejar un fallback para valores desconocidos, así un status futuro no vuelve a romper la app.

### 2. Mostrarlo en la pantalla de estado

Sugerencia de etiquetas (ajustar al diseño existente):

| Estado | Etiqueta | Tratamiento |
|---|---|---|
| `REGISTERED` | "Enviando…" | transitorio, como hoy |
| `SENT_TO_AGUAS` | "Enviado ✓" | ok |
| `AGUAS_ERROR` | "Error — reintentando" | como hoy |
| `SKIPPED_UNREGISTERED` | "No enviado — dispenser no registrado" | **final**, no spinner |
| `CANCELLED` | "Cancelado" | como hoy |

Lo importante: `SKIPPED_UNREGISTERED` **no debe mostrar spinner ni "enviando"**, porque nunca va a cambiar solo.

### 3. Avisar al usuario apenas responde el POST

Después del `POST`, si `excludedSerials` no está vacío, mostrar un aviso con los seriales. Dos variantes según el caso:

- **Parcial** (`status: REGISTERED` + `excludedSerials` no vacío):
  > "Se registró la descarga, pero N dispenser(s) no están registrados en el sistema y no se enviaron: `LM29H04160827`."

- **Total** (`status: SKIPPED_UNREGISTERED`):
  > "Ningún dispenser se envió: todos figuran como no registrados. Revisá los seriales o avisá a sistemas."

Conviene que en el caso total sea un diálogo que requiera confirmación, no un toast que se pierda — es el caso en que el usuario cree que cargó algo y en realidad no se envió nada.

### 4. Mostrar los excluidos en el detalle y el listado

En la pantalla de detalle del movimiento, si `excludedSerials` no está vacío, listarlos aparte de los enviados (por ejemplo, los seriales excluidos tachados o con un ícono de advertencia). Sirve para que después puedan revisar qué pasó.

### 5. Revisar el timeout del cliente HTTP

El `POST` ahora hace una llamada externa antes de responder. El backend la corta a los **5 segundos** como máximo (y si falla, sigue de largo enviando todo). Verificar que el timeout de la app sea **cómodamente mayor a 5s** — si está en 5s o menos, un jMobile lento puede hacer que la app dé timeout aunque el movimiento se haya creado bien.

---

## Casos borde

**Cargas (`LOAD`)** — no se valida nada, `excludedSerials` siempre viene `[]`. No hace falta ninguna lógica especial.

**Corrección (`PUT /dispenser-movements/{id}`)** — cancela el anterior y crea uno nuevo, así que **revalida contra la lista del día**. La respuesta del PUT puede traer perfectamente `status: SKIPPED_UNREGISTERED` o `excludedSerials` con contenido. Tratarla igual que la del POST.

**Si un dispenser se regulariza más tarde** — el movimiento en `SKIPPED_UNREGISTERED` no se reintenta solo. El camino es hacer un `PUT` (corregir) sobre ese movimiento, que vuelve a consultar la lista y esta vez lo envía.

**Si jMobile está caído** — el backend no bloquea nada: manda el movimiento completo como antes (`excludedSerials: []`). Desde la app es indistinguible del caso normal, y está bien que así sea.

**Filtro por estado en el listado** — `GET /dispenser-movements?status=SKIPPED_UNREGISTERED` funciona. Si la app tiene un selector de estados, sumar la opción.

**Comparación de seriales** — el backend compara ignorando mayúsculas/minúsculas y espacios, pero los valores en `excludedSerials` vienen **tal cual los escaneó el usuario**, así que se pueden matchear directo contra `serials` para pintarlos en la UI.

---

## Cómo probarlo

La lista de no registrados del día se puede consultar directamente (fecha en el path, **no** como query param):

```
GET http://35.199.104.218:8080/jmobile/service/dispenserope/getDispenserNoRegistrado=fecha=2026-08-07
```

```json
{
  "success": true,
  "listado": [
    { "fecha": "2026-08-07 14:53:00.0", "nroSerie": "1518208243888", "nroReparto": 199, "nroCta": "1171320", "id": 1 },
    { "fecha": "2026-08-07 14:53:53.0", "nroSerie": "LM29H01181809", "nroReparto": 199, "nroCta": "1171320", "id": 2 },
    { "fecha": "2026-08-07 14:54:55.0", "nroSerie": "LM29H04160827", "nroReparto": 199, "nroCta": "1186256", "id": 3 }
  ]
}
```

Escenarios de prueba:

1. **Todo excluido** — descarga con un único serial que esté en `listado` → debe volver `SKIPPED_UNREGISTERED`.
2. **Parcial** — descarga con uno de la lista y uno cualquiera que no esté → `REGISTERED` + un serial en `excludedSerials`.
3. **Normal** — descarga con seriales que no estén en la lista → `REGISTERED` + `excludedSerials: []`.
4. **Carga** — cualquier `LOAD`, aunque use un serial de la lista → se envía igual, sin excluir nada.

---

## Checklist

- [ ] `SKIPPED_UNREGISTERED` parsea sin romper (y hay fallback para desconocidos)
- [ ] El estado se muestra como final, sin spinner
- [ ] Aviso al usuario cuando `excludedSerials` no está vacío (parcial y total)
- [ ] Los excluidos se ven en el detalle del movimiento
- [ ] La respuesta del `PUT` se trata igual que la del `POST`
- [ ] Timeout HTTP del cliente > 5s
- [ ] Los 4 escenarios de prueba pasan
