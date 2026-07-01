# Deploy con Docker

La API corre en un contenedor y apunta a una base PostgreSQL **externa** (ya existente). En el servidor queda expuesta en el puerto **8096** (`http://<servidor>:8096`).

## Requisitos
- Docker y Docker Compose en el servidor.
- El servidor debe tener acceso de red a los servidores de Aguas (`192.168.0.12` y `192.168.0.58`).
- Una base PostgreSQL accesible, con la **base de datos ya creada** (vacía). Flyway crea las tablas, pero **no** crea la base ni el usuario.

## Pasos

1. Copiar el proyecto al servidor (la carpeta que contiene `pom.xml`, `Dockerfile` y `docker-compose.yml`).

2. Asegurarse de que la base de datos exista (una sola vez):
   ```sql
   CREATE DATABASE loop_new_db;
   ```
   El usuario configurado debe tener permisos para crear tablas en esa base.

3. Crear el archivo `.env` a partir del ejemplo y completarlo:
   ```bash
   cp .env.example .env
   nano .env
   ```
   Completar `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (de tu base externa) y `JWT_SECRET`.

3. Construir y levantar:
   ```bash
   docker compose up -d --build
   ```

4. Ver los logs (Flyway corre las migraciones al arrancar):
   ```bash
   docker compose logs -f app
   ```

5. Probar:
   ```
   POST http://<servidor>:8096/auth/login
   ```
   La documentación Swagger queda en `http://<servidor>:8096/swagger-ui.html`.

## Comandos útiles

```bash
docker compose ps                 # estado de los contenedores
docker compose logs -f app        # logs de la API
docker compose restart app        # reiniciar solo la API
docker compose down               # bajar todo (la base persiste en el volumen)
docker compose up -d --build      # reconstruir tras cambios de código
```

## Notas
- **Puerto**: la app usa el **8096** del servidor (mapeado al 8080 interno). Cambiarlo en `docker-compose.yml` si hiciera falta (`"8096:8080"`).
- **Base de datos externa**: se apunta con `DB_URL` en el `.env`.
  - Si la base está en el **mismo servidor** que Docker: usar `jdbc:postgresql://host.docker.internal:5432/loop_new_db` (el `extra_hosts` del compose ya resuelve `host.docker.internal` en Linux).
  - Si está en **otro server de la LAN**: usar su IP, ej. `jdbc:postgresql://192.168.0.XX:5432/loop_new_db`.
  - La base PostgreSQL debe permitir conexiones desde la IP del servidor Docker (revisar `pg_hba.conf` / `listen_addresses` si es una instalación propia).
- **Migraciones**: Flyway las aplica solas al iniciar la app (sobre la base que exista).
- **Aguas**: las URLs se configuran por variables de entorno en el `.env` (`AGUAS_BASE_URL`, `AGUAS_EQUIPMENT_BASE_URL`).
