# LOGYX — Guía para agentes (humanos e IAs)

> Archivo canónico de contexto. `CLAUDE.md` solo lo importa. Léelo completo antes de tocar código.
> Si cambias una decisión de arquitectura, actualiza este archivo **y** el ADR correspondiente.

## Qué es
Marketplace B2B de fletes para PYMEs del corredor Lima–Sierra Sur (Perú). Las PYMEs publican
cargas, los transportistas compiten en una **subasta inversa**, la PYME acepta una oferta, se
genera un **envío** y el **conductor** lo ejecuta parada por parada.
Especificación funcional completa: repo hermano `../ProyectoEPE-LOGYX/docs/software/` (SRS, modelo de datos).

## Mapa del monorepo
| Carpeta | Stack | Guía local |
|---|---|---|
| `backend/` | Quarkus 3.40 · Java 21 · PostgreSQL 16 · Flyway · JWT RSA | `backend/AGENTS.md` |
| `web/` | Astro 7 (SSR, Node) · Tailwind 4 · React islands + shadcn/ui · MapLibre | `web/AGENTS.md` |
| `android/` | Kotlin · Jetpack Compose · Material 3 · Ktor · Koin · MapLibre | `android/AGENTS.md` |
| `ios/` | SwiftUI (fase posterior; reutilizará `core:model`/`core:network` vía KMP) | `ios/README.md` |
| `docs/api/openapi.yaml` | **Contrato de la API (fuente de verdad)** — generado por el backend | — |
| `docs/architecture/` | Visión general y capas | `overview.md` |
| `docs/adr/` | Decisiones de arquitectura numeradas | — |
| `docs/design/DESIGN.md` | Sistema de diseño (tokens, componentes, pantallas por rol) | — |
| `docs/domain/business-rules.md` | Reglas RN01–RN12 → dónde se implementan | — |
| `docs/STATUS.md` | Estado de cada módulo/pantalla. **Actualízalo al terminar una tarea.** | — |
| `docs/security.md` | Modelo de amenazas y checklist de despliegue | — |
| `infra/` | `docker-compose.yml` de producción local (postgres, minio, backend, web), roles de BD, claves JWT | `infra/README.md` |
| `.github/` | CI: backend, web, android, contrato OpenAPI (`scripts/openapi-compat.py`), e2e nightly/manual; dependabot | — |

## Reglas de oro
1. **El contrato manda.** Web y móvil consumen `docs/api/openapi.yaml`. Si el backend cambia un
   endpoint, regenera el contrato (`cd backend && ./mvnw package -DskipTests`) y avisa en `docs/STATUS.md`.
2. **Aislamiento por organización (ADR-0001, ADR-0006).** Nunca recibas `organizationId` desde el cliente: sale del JWT
   (`TenantContext`). Todo acceso a datos de usuario va dentro de `@Transactional` para que se active RLS. La app se
   conecta como `logyx_api` (solo `logyx_app`); lo que corre sin usuario va por `SystemTx` (datasource privilegiado).
   Los eventos que deben llegar (avisos) pasan por el outbox (`OutboxEvent` + `@Observes @Outboxed`).
   **Tiempo real (ADR-0007):** `GET /api/v1/events/stream` (SSE) proyecta el outbox con destinatario explícito por fila;
   sus eventos son avisos con payload mínimo (ids/estados): los clientes vuelven a pedir por REST y conservan un polling
   lento de respaldo. Web: proxy `/eventos` + `web/src/lib/realtime.ts`; Android: cliente SSE en `:core:network`.
   **Trabajos «en nombre de» una organización** (plantillas recurrentes, ADR-0013): corren con `SyntheticTenant` (token en memoria con `org_id` y el autor), no con `SystemTx`; la RLS sigue activa.
   **Superficies públicas sin JWT** (cotizador `/public/pricing/*`, seguimiento por enlace `/public/tracking/{token}` → web `/s/<token>`,
   ADR-0011; «pedir acceso a la empresa» desde el registro `/public/organization-access-requests`, Ola 2 X): límite por IP, `SystemTx` acotado y **lista blanca** de campos; nunca reutilices un DTO autenticado ahí.
   **Precio final de un envío** (Ola 2 X, ADR-0012) = acordado + ajustes aceptados; la comisión se calcula sobre él con la tasa guardada al adjudicar.
3. **Respuestas** siempre `{ data, meta }`; errores `{ error: { code, message, field }, meta }` con códigos `LOGYX-XXX-NNN`.
4. **Idioma:** dominio, UI y mensajes en español; identificadores de código en inglés.
5. **Una carpeta por feature/módulo**, con la misma forma interna en todo el monorepo (ver guías locales).
   Si creas un módulo nuevo, copia la forma de uno existente (`marketplace` es la referencia).
6. **Tests antes de declarar terminado:** backend `./mvnw test`, web `npm run check && npm run build`,
   android `./gradlew assembleDebug testDebugUnitTest`. Contrato: `scripts/check-contract.sh` (falla si se quitan
   paths/schemas/campos respecto de `origin/main`). La CI (`.github/workflows/`) corre lo mismo.
   Máquina local de 15 GB: un proceso pesado a la vez (Maven, Gradle, `docker build`, Playwright).
7. No subas secretos. Las claves JWT de dev/test se generan en memoria; en prod vienen por env/archivos montados
   (`infra/scripts/gen-jwt-keys.sh` → `infra/secrets/`, ignorado). `infra/.env` tampoco se versiona (`.env.example` sí).

## Roles y JWT
| Usuario | `groups` en JWT | Claims |
|---|---|---|
| Usuario de PYME | `PYME` | `org_id`, `org_type=PYME`, `role` |
| Usuario de transportista | `CARRIER` | `org_id`, `org_type=CARRIER`, `role` |
| Conductor | `DRIVER` (solo) | `org_id` del transportista, `role=DRIVER` |
| Operador LOGYX | `OPERATOR` | `org_type=OPERATOR`; cuentas creadas por LOGYX (no hay registro público). Solo usa la web. |

Todo JWT lleva además `sv` (versión de sesión): si no coincide con la BD → 401 (ADR-0009).
**Rol de organización** (claim `role`, V14, F03) — el backend lo hace cumplir (`AccountAccessFilter`):
| `role` | Puede | Código si no |
|---|---|---|
| `ADMIN` | todo: además gestiona usuarios (`/organizations/me/users`), el perfil (`PATCH /organizations/me`) y las cuentas de conductores | — |
| `OPS` | operar: publicar, ofertar, aceptar, contraofertar, chatear, asignar, cancelar, vehículos, viajes, reservas, incidencias, documentos, reseñas | 403 `LOGYX-AUTH-008` en gestión |
| `VIEWER` | solo leer (más su cuenta: cambiar contraseña, cerrar sesión, leer avisos) | 403 `LOGYX-AUTH-007` en cualquier escritura |
| `DRIVER` | sus envíos (grupo `DRIVER`) | — |
`UserInfo.permissions` (`READ`, `OPERATE`, `MANAGE_USERS`, `MANAGE_ORGANIZATION`, `MANAGE_DRIVERS`) sirve para mostrar u ocultar acciones.

## Cuentas demo (perfil dev del backend)
`pyme@logyx.pe`, `carrier@logyx.pe`, `carrier2@logyx.pe`, `driver@logyx.pe`, `operator@logyx.pe` — contraseña `Logyx2026!` (ver `backend/AGENTS.md`).
Misma PYME con otros roles: `pyme.ops@logyx.pe` (OPS) y `pyme.viewer@logyx.pe` (VIEWER). Correos en dev (restablecer,
invitaciones): `GET http://localhost:8080/api/v1/dev/mailbox?to=<email>`.

## Cómo levantar todo
```bash
cd backend && ./mvnw quarkus:dev          # API en :8080 (Dev Services levanta Postgres con Docker)
cd web && npm install && npm run dev      # Web en :4321 (API_URL=http://localhost:8080)
cd android && ./gradlew installDebug      # Emulador: API en http://10.0.2.2:8080
```
Requiere `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` (el JDK por defecto de la máquina es 27).

Stack completo en contenedores (sin datos demo): ver `infra/README.md`
(`docker compose -f infra/docker-compose.yml --env-file infra/.env up -d` → web :8580, API :8680).
