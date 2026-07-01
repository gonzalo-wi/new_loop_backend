# Deploy con Docker

La API corre en un contenedor junto a su propia base PostgreSQL. En el servidor queda expuesta en el puerto **8096** (`http://<servidor>:8096`).

## Requisitos
- Docker y Docker Compose en el servidor.
- El servidor debe tener acceso de red a los servidores de Aguas (`192.168.0.12` y `192.168.0.58`).

## Pasos

1. Copiar el proyecto al servidor (la carpeta que contiene `pom.xml`, `Dockerfile` y `docker-compose.yml`).

2. Crear el archivo `.env` a partir del ejemplo y completarlo:
   ```bash
   cp .env.example .env
   nano .env
   ```
   Completar `DB_PASSWORD` y `JWT_SECRET` con valores propios.

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
- **Base de datos**: PostgreSQL corre dentro del compose y **no** se expone al exterior. Los datos persisten en el volumen `loop_db_data`.
- **Migraciones**: Flyway las aplica solas al iniciar la app.
- **Aguas**: las URLs se configuran por variables de entorno en el `.env` (`AGUAS_BASE_URL`, `AGUAS_EQUIPMENT_BASE_URL`).
