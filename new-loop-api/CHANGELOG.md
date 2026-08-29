# Changelog

Todos los cambios notables de Loop se documentan en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/lang/es/).

## [Unreleased]

### Added
- Salida de dispensers (carga al reparto): cuando un movimiento LOAD se confirma en Aguas, ahora también se registra en Odoo, moviendo el stock de expedición al reparto. La operación es "todo o nada" (si una serie no está disponible no se registra nada y se informa el detalle) y es idempotente: un reintento por corte de conexión no duplica el movimiento de stock. Se suman dos consultas para la app: equipos disponibles para cargar y validación de series escaneadas contra Odoo.
- El rol SUPERVISOR puede corregir un control de entrada (IN) ya enviado y aceptado por Aguas: la corrección se reenvía a Aguas pisando el registro anterior y queda auditada con el usuario que la hizo, un motivo obligatorio y la comparación entre el control previo y el corregido.
- Observabilidad del backend: métricas de rendimiento, errores y eventos clave de negocio (controles enviados/rechazados/corregidos a Aguas, movimientos de dispenser, llamadas a integraciones externas) ahora se pueden monitorear en tiempo real desde Grafana, alimentado por Prometheus. El stack se levanta junto con la app vía `docker compose up`.

### Changed
- La integración de ingreso a reparación (UNLOAD) de dispensers ahora apunta a la misma instancia de Odoo que la salida al reparto; ambas quedan unificadas en un único entorno.

### Fixed
- Salida y vuelta de dispensers a Aguas: se corrige el rechazo HTTP 400 ("Todos los campos son requeridos") que dejaba los movimientos reintentando en bucle. Ahora se envía el campo obligatorio `esrecarga` (siempre falso para estos movimientos) y, cuando el movimiento se registró sin usuario de sesión, se usa el nombre del técnico como usuario para no mandar el campo vacío.

### Removed
-
