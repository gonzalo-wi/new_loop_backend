# Changelog

Todos los cambios notables de Loop se documentan en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/lang/es/).

## [Unreleased]

### Added
- El rol SUPERVISOR puede corregir un control de entrada (IN) ya enviado y aceptado por Aguas: la corrección se reenvía a Aguas pisando el registro anterior y queda auditada con el usuario que la hizo, un motivo obligatorio y la comparación entre el control previo y el corregido.
- Observabilidad del backend: métricas de rendimiento, errores y eventos clave de negocio (controles enviados/rechazados/corregidos a Aguas, movimientos de dispenser, llamadas a integraciones externas) ahora se pueden monitorear en tiempo real desde Grafana, alimentado por Prometheus. El stack se levanta junto con la app vía `docker compose up`.

### Changed
-

### Fixed
-

### Removed
-
