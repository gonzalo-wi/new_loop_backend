# Changelog

Todos los cambios notables de Loop se documentan en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/lang/es/).

## [Unreleased]

### Added
- Validación de dispensers contra Odoo al registrar una carga (LOAD): antes de enviar a Aguas/Odoo, el backend valida las series y excluye automáticamente las que Odoo reporta como no disponibles (no existen, están en otra ubicación o sin stock), dejándolas marcadas como excluidas. La carga continúa con las series válidas; si ninguna está disponible, el movimiento se cierra sin enviarse. Si Odoo no responde, no se bloquea la carga.
- Salida de dispensers (carga al reparto): cuando un movimiento LOAD se confirma en Aguas, ahora también se registra en Odoo, moviendo el stock de expedición al reparto. La operación es "todo o nada" (si una serie no está disponible no se registra nada y se informa el detalle) y es idempotente: un reintento por corte de conexión no duplica el movimiento de stock. Se suman dos consultas para la app: equipos disponibles para cargar y validación de series escaneadas contra Odoo.
- El rol SUPERVISOR puede corregir un control de entrada (IN) ya enviado y aceptado por Aguas: la corrección se reenvía a Aguas pisando el registro anterior y queda auditada con el usuario que la hizo, un motivo obligatorio y la comparación entre el control previo y el corregido.
- Observabilidad del backend: métricas de rendimiento, errores y eventos clave de negocio (controles enviados/rechazados/corregidos a Aguas, movimientos de dispenser, llamadas a integraciones externas) ahora se pueden monitorear en tiempo real desde Grafana, alimentado por Prometheus. El stack se levanta junto con la app vía `docker compose up`.

### Changed
- La integración de ingreso a reparación (UNLOAD) de dispensers ahora apunta a la misma instancia de Odoo que la salida al reparto; ambas quedan unificadas en un único entorno.
- Salida de dispensers a Odoo: si al despachar algunos equipos no están disponibles en expedición, ahora se despachan igual los que sí lo están (antes Odoo rechazaba el lote completo y no salía ninguno). Los equipos no disponibles quedan registrados en el detalle del error. Si ninguno está disponible, el despacho se cierra como error definitivo y deja de reintentarse.

### Fixed
- Endpoints de catálogo de Odoo (equipos disponibles y validación de series) y de Aguas (ubicaciones/estados): devolvían una respuesta corrupta (las propiedades internas del árbol JSON en vez del contenido), por lo que la app no veía ningún equipo/dato. Ahora devuelven el JSON real.
- Salida y vuelta de dispensers a Aguas: se corrige el rechazo HTTP 400 ("Todos los campos son requeridos") que dejaba los movimientos reintentando en bucle. La salida al reparto ahora envía los campos que Aguas exige (`esrecarga=1` y `accion=3`) y la vuelta a planta va con `esrecarga=0`. Además, cuando el movimiento se registró sin usuario de sesión, se usa el nombre del técnico como usuario para no mandar el campo vacío.

### Removed
-
