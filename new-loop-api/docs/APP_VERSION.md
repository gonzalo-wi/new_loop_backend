# Versión de la app / distribución de APK

Permite que la app mobile detecte si hay una versión nueva antes de loguearse, y descargue el APK directo.

---

## 1. Consultar la última versión (usa la app)

```
GET /app/version
```

**Público — sin token.** Siempre devuelve la última versión publicada, sin envoltorio `{data, message}` (formato plano, tal cual lo necesita la app):

```json
{
  "latestVersion": "1.1.0",
  "apkUrl": "http://192.168.0.42:8095/app/download/loop-1.1.0.apk",
  "mandatory": false,
  "notes": "Buscar mi camión + arreglos de carga"
}
```

El `apkUrl` se arma solo con el host/puerto real por el que entró la request — no hace falta configurar una URL pública a mano.

Si todavía no se publicó ninguna versión: `404 Not Found`.

---

## 2. Publicar una versión nueva (lo hacés vos, el admin)

```
POST /app/version
Content-Type: multipart/form-data
```

| Campo       | Tipo          | Requerido | Descripción                                  |
|-------------|---------------|-----------|-----------------------------------------------|
| `version`   | text          | Sí        | Formato `x.y.z` (ej. `1.1.0`)                 |
| `mandatory` | text (bool)   | No        | `true`/`false`. Default `false`.              |
| `notes`     | text          | No        | Texto corto que ve el usuario en el aviso.    |
| `file`      | archivo       | Sí        | El `.apk`. Máximo 200MB.                      |

**Ejemplo con curl:**
```bash
curl -X POST http://localhost:8095/app/version \
  -H "Authorization: Bearer $TOKEN" \
  -F "version=1.1.0" \
  -F "mandatory=false" \
  -F "notes=Buscar mi camión + arreglos de carga" \
  -F "file=@loop-1.1.0.apk;type=application/vnd.android.package-archive"
```

### Response `201 Created`
Devuelve el mismo objeto que `GET /app/version`, ya con la versión recién publicada.

### Errores
- **`400`** — versión con formato inválido, falta el archivo, o el archivo no es `.apk`.
- **`401`** — sin token, o con uno vencido (vence a la medianoche).
- **`403`** — el usuario no es `ADMIN`.

> Cada publicación queda guardada como un registro nuevo (no se pisa el historial) — `GET /app/version` siempre trae la más reciente. Si publicás dos veces la misma versión, el archivo en disco se sobreescribe.

---

## 3. Descarga del APK

```
GET /app/download/{fileName}
```

**Público — sin token.** Descarga directa (no HTML), con `Content-Type: application/vnd.android.package-archive` y `Content-Disposition: attachment`. El `fileName` es el que viene en `apkUrl`, no hace falta armarlo a mano.

Si el archivo no existe en disco: `404 Not Found`.

---

## Dónde se guardan los archivos

Los APKs se guardan en disco, en la carpeta configurada por `APK_STORAGE_PATH` (default `./apk-storage`). En Docker está montada como volumen (`./apk-storage:/app/apk-storage`) para que **no se pierdan al reconstruir el contenedor**.

---

## Seguridad

`POST /app/version` pide token de un usuario **`ADMIN`** (spec 003). `GET /app/version` y `GET /app/download/{fileName}` siguen públicos porque la app busca actualizaciones antes de loguearse. Ver [AUTH.md](./AUTH.md#vencimiento-y-sesión).
