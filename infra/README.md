# Despliegue de LOGYX (contenedores)

Producción local / staging en una máquina con Docker Compose: PostgreSQL 16, MinIO (S3), backend Quarkus (JVM) y web
Astro (Node). Decisiones: ADR-0006 (roles de BD, outbox, S3) y `docs/security.md`.

## Arranque
```bash
cp infra/.env.example infra/.env        # cambia TODAS las contraseñas (infra/.env no se versiona)
infra/scripts/gen-jwt-keys.sh           # par RSA del JWT en infra/secrets/ (no se versiona)
docker compose -f infra/docker-compose.yml --env-file infra/.env build backend   # una imagen a la vez (memoria)
docker compose -f infra/docker-compose.yml --env-file infra/.env build web
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d
# Web: http://localhost:8580 · API: http://localhost:8680 (/q/health/ready)
docker compose -f infra/docker-compose.yml --env-file infra/.env down -v   # apaga y borra volúmenes
```
No hay datos demo (el seed es solo del perfil dev): registra una cuenta desde `/registro`.

## Servicios
| Servicio | Imagen | Notas |
|---|---|---|
| `db` | `postgres:16-alpine` | `db/init-roles.sh` crea en el primer arranque `logyx_owner` (dueño, Flyway + SystemTx), `logyx_app` (NOLOGIN, RLS) y `logyx_api` (LOGIN, miembro solo de `logyx_app`) y la BD `logyx`. Solo red interna |
| `minio` | `chainguard/minio@sha256:…` | MinIO ya no publica `minio/minio` en Docker Hub. Healthcheck con bash (la imagen no trae curl). Solo red interna |
| `backend` | `backend/Dockerfile` | multi-stage (Maven en `eclipse-temurin:21-jdk-alpine` → `21-jre-alpine`), usuario 10001, healthcheck `/q/health/ready`. Flyway migra al arrancar. Documentos en S3 (`LOGYX_DOCUMENTS_STORAGE=s3`, crea el bucket) |
| `web` | `web/Dockerfile` | Astro standalone en `node:22-alpine`, usuario `node`, `API_URL=http://backend:8080`. `COOKIE_SECURE=false` solo porque el compose local es http |

## Variables del backend (prod)
| Variable | Uso |
|---|---|
| `DB_URL` | JDBC de la BD (ambos datasources) |
| `DB_ADMIN_USER` / `DB_ADMIN_PASSWORD` | `logyx_owner`: Flyway y `SystemTx` |
| `DB_APP_USER` / `DB_APP_PASSWORD` | `logyx_api`: Hibernate (todo lo demás) |
| `JWT_PUBLIC_KEY` / `JWT_PRIVATE_KEY` | rutas a los PEM (compose: `/run/secrets/jwt_*`) |
| `LOGYX_DOCUMENTS_STORAGE` | `local` (disco, `LOGYX_DOCUMENTS_DIR`) o `s3` |
| `LOGYX_S3_ENDPOINT`, `LOGYX_S3_REGION`, `LOGYX_S3_BUCKET`, `LOGYX_S3_ACCESS_KEY`, `LOGYX_S3_SECRET_KEY`, `LOGYX_S3_PATH_STYLE`, `LOGYX_S3_CREATE_BUCKET`, `LOGYX_S3_PREFIX`, `LOGYX_S3_SSE` | adaptador S3/MinIO |
| `CORS_ENABLED` / `CORS_ORIGINS` | CORS desactivado por omisión en prod (la web es SSR) |
| `TRUSTED_PROXIES` | redes desde las que se acepta `X-Forwarded-For` (rate limiting por IP) |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_START_TLS` (`REQUIRED` por omisión), `SMTP_TLS`, `MAIL_FROM` | correo de restablecer contraseña e invitaciones (V14, ADR-0009) |
| `PASSWORD_RESET_URL` | página `/restablecer` de la web; el correo agrega `?token=…` |
| `MAILER_MOCK` | solo pruebas: `true` no envía correos (los guarda en memoria; si la imagen se compila con `-Dlogyx.dev-mailbox=true` se leen en `GET /api/v1/dev/mailbox?to=`) |

## Producción real
- TLS en un proxy (Caddy/Nginx/LB) delante de `web` (y de `backend` si la app Android lo usa directamente); quita
  `COOKIE_SECURE=false`. HSTS lo envían la API (siempre en prod) y la web (si llega por https).
- BD gestionada: ejecuta `db/init-roles.sh` una vez como administrador con `LOGYX_OWNER_PASSWORD`, `LOGYX_APP_PASSWORD`,
  `LOGYX_DB`, `POSTGRES_USER` (y `PGHOST`/`PGPASSWORD`).
- S3 real: `LOGYX_S3_PATH_STYLE=false`, `LOGYX_S3_CREATE_BUCKET=false`, `LOGYX_S3_SSE=true`, usuario IAM limitado al bucket.
- Backups de `pgdata` y del bucket. Varias réplicas del backend funcionan (outbox con `SKIP LOCKED`), pero el rate limit
  es por instancia.
