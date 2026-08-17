# Observabilidad - Loop backend

Prometheus + Grafana para el backend `new-loop-api`. Se levantan como parte del
mismo `docker-compose.yml` de la raíz del repo (junto al servicio `app`), todos
con `network_mode: host` para ser consistentes con cómo la app ya resuelve la
red del server.

## Contenido de esta carpeta

- `prometheus/prometheus.yml` — template de scrape config. Un contenedor init
  (`prometheus-config` en el compose) renderiza `${PORT}`, `${ACTUATOR_USERNAME}`
  y `${ACTUATOR_PASSWORD}` desde el entorno antes de que arranque Prometheus
  (la imagen oficial de Prometheus no trae shell/envsubst).
- `grafana/provisioning/datasources/datasource.yml` — datasource de Prometheus
  (`http://localhost:9090`, vía host networking) provisionado automáticamente.
- `grafana/provisioning/dashboards/dashboards.yml` — provider que carga los
  dashboards JSON de `grafana/provisioning/dashboards/json/`.
- `grafana/provisioning/dashboards/json/loop-backend.json` — dashboard base:
  request rate, error rate, latencia p95/p99, y las métricas de negocio de
  Aguas/dispensers. Vive dentro del árbol de `provisioning/` (y no en una
  carpeta hermana) para que un único bind mount de solo lectura
  (`./observability/grafana/provisioning:/etc/grafana/provisioning:ro`)
  alcance para todo — montar un segundo volumen anidado dentro de un mount
  `:ro` falla en Docker (no puede crear el mountpoint).

## Cómo levantar el stack

1. Completar `.env` en la raíz del repo (copiar de `.env.example`) con, como
   mínimo:
   - `PORT` (puerto donde escucha `app`, default `8096`)
   - `ACTUATOR_USERNAME` / `ACTUATOR_PASSWORD` (credenciales dedicadas para que
     Prometheus scrapee `/actuator/**` vía HTTP Basic — no son las del login
     de usuarios de la app)
   - `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` (usuario admin de Grafana)
2. Desde la raíz del repo:
   ```bash
   docker compose up -d
   ```
   Esto levanta `app`, `prometheus-config` (init, renderiza el scrape config y
   termina), `prometheus` y `grafana`.
3. Grafana queda disponible en `http://<host>:3000`, login con
   `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`. El datasource de Prometheus
   y el dashboard "Loop Backend - Overview" ya están provisionados, sin
   click-ops manual.
4. Prometheus queda disponible en `http://<host>:9090` para consultas ad-hoc
   o verificar el estado del target (`Status > Targets`).

## Verificación manual

- Métricas crudas del backend (requiere las credenciales de `ACTUATOR_USERNAME`/
  `ACTUATOR_PASSWORD`):
  ```bash
  curl -u "$ACTUATOR_USERNAME:$ACTUATOR_PASSWORD" http://localhost:8096/actuator/prometheus
  ```
- Healthcheck público (sin auth, pensado para Docker/infra):
  ```bash
  curl http://localhost:8096/actuator/health
  ```
- Target de Prometheus arriba (`UP`): `http://<host>:9090/targets`
- Dashboard cargado: `http://<host>:3000/dashboards`, carpeta general,
  "Loop Backend - Overview".

## Notas

- Los datos de Prometheus (`prometheus-data`) y Grafana (`grafana-data`)
  persisten en volúmenes named de Docker; sobreviven a `docker compose down`
  (no a `docker compose down -v`).
- No se hardcodean credenciales en ningún archivo de esta carpeta: todo sale
  de variables de entorno definidas en `.env`.
