# Changelog

Todos los cambios notables de Loop se documentan en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/lang/es/).

## [Unreleased]

### Added
- Estadísticas de dispensers (`GET /dispenser-movements/stats?from&to`): devuelve ya calculados en la base el volumen de instalaciones y retiros, la serie por día, la actividad por día y hora, la productividad por usuario (incluido el ritmo entre movimientos), los totales por técnico y la calidad de integración con Aguas/Odoo con la lista de movimientos a corregir. Rango máximo de 92 días; si el rango es inválido responde 400. Los cancelados no se cuentan.
- Dispensers no normalizados en descargas (UNLOAD): los equipos que jMobile marca como no registrados en un cliente ya no se descartan. Se envían a Odoo a una ubicación específica de "equipos no normalizados", sin pasar por taller ni generar orden de reparación. Siguen sin mandarse a Aguas. El envío es en segundo plano, con reintentos automáticos, y el movimiento guarda el estado y el picking de Odoo aparte del de reparación.
- Listado de movimientos de dispenser: se puede filtrar por fecha de registro (`createdFrom`/`createdTo`), además de por la fecha del movimiento. Sirve para ver lo que se cargó en el día aunque la carga esté fechada para el próximo reparto.
- Validación de dispensers contra Odoo al registrar una carga (LOAD): antes de enviar a Aguas/Odoo, el backend valida las series y excluye automáticamente las que Odoo reporta como no disponibles (no existen, están en otra ubicación o sin stock), dejándolas marcadas como excluidas. La carga continúa con las series válidas; si ninguna está disponible, el movimiento se cierra sin enviarse. Si Odoo no responde, no se bloquea la carga.
- Salida de dispensers (carga al reparto): cuando un movimiento LOAD se confirma en Aguas, ahora también se registra en Odoo, moviendo el stock de expedición al reparto. La operación es "todo o nada" (si una serie no está disponible no se registra nada y se informa el detalle) y es idempotente: un reintento por corte de conexión no duplica el movimiento de stock. Se suman dos consultas para la app: equipos disponibles para cargar y validación de series escaneadas contra Odoo.
- El rol SUPERVISOR puede corregir un control de entrada (IN) ya enviado y aceptado por Aguas: la corrección se reenvía a Aguas pisando el registro anterior y queda auditada con el usuario que la hizo, un motivo obligatorio y la comparación entre el control previo y el corregido.
- Observabilidad del backend: métricas de rendimiento, errores y eventos clave de negocio (controles enviados/rechazados/corregidos a Aguas, movimientos de dispenser, llamadas a integraciones externas) ahora se pueden monitorear en tiempo real desde Grafana, alimentado por Prometheus. El stack se levanta junto con la app vía `docker compose up`.

### Changed
- Autenticación: el backend ya no consulta la tabla de usuarios en cada request; reutiliza los datos del usuario durante 2 minutos (configurable con `USER_CACHE_TTL`) y los refresca al editar, activar o desactivar al usuario.
- Índices nuevos en controles de stock (fecha + tipo + estado, y reparto + tipo + fecha) y en pedidos (fecha de creación), para que el dashboard, el chequeo de controles duplicados y el listado de pedidos sigan siendo rápidos cuando crezca el volumen.
- Los listados (controles de stock, repartos, usuarios, pedidos) cargan sus datos relacionados en lote en vez de hacer una consulta por fila: una página de 20 controles pasa de ~109 consultas a 6 y el listado de repartos de ~207 a 7. La respuesta no cambia.
- El listado de movimientos de dispenser carga los seriales en lote: una página ya no hace dos consultas extra por cada movimiento. Se suma un índice en la fecha de registro (`created_at`). La respuesta no cambia.
- Listados de usuarios (`GET /users`) y repartos (`GET /routes`): ahora se ordenan de forma natural, con los números como números y no como texto (Rto 1, Rto 2, Rto 3 … Rto 10 … Rto 100, en vez de Rto 1, Rto 10, Rto 12). Además aceptan un parámetro `search` que filtra sobre toda la base (no solo la página actual): en usuarios busca por nombre y username; en repartos por código, patente, sucursal y repartidor. Con esto el buscador encuentra un registro aunque esté en otra página. El parámetro `sort` de estos dos endpoints deja de tener efecto (el orden natural se aplica siempre).
- La integración de ingreso a reparación (UNLOAD) de dispensers ahora apunta a la misma instancia de Odoo que la salida al reparto; ambas quedan unificadas en un único entorno.
- Salida de dispensers a Odoo: si al despachar algunos equipos no están disponibles en expedición, ahora se despachan igual los que sí lo están (antes Odoo rechazaba el lote completo y no salía ninguno). Los equipos no disponibles quedan registrados en el detalle del error. Si ninguno está disponible, el despacho se cierra como error definitivo y deja de reintentarse.

### Fixed
- Un usuario desactivado ya no puede seguir usando la API con un token emitido antes de la desactivación.
- Endpoints de catálogo de Odoo (equipos disponibles y validación de series) y de Aguas (ubicaciones/estados): devolvían una respuesta corrupta (las propiedades internas del árbol JSON en vez del contenido), por lo que la app no veía ningún equipo/dato. Ahora devuelven el JSON real.
- Salida y vuelta de dispensers a Aguas: se corrige el rechazo HTTP 400 ("Todos los campos son requeridos") que dejaba los movimientos reintentando en bucle. La salida al reparto ahora envía los campos que Aguas exige (`esrecarga=1` y `accion=3`) y la vuelta a planta va con `esrecarga=0`. Además, cuando el movimiento se registró sin usuario de sesión, se usa el nombre del técnico como usuario para no mandar el campo vacío.

### Removed
-
