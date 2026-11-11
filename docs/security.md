# Seguridad de LOGYX — modelo de amenazas resumido

Actualizado 2026-11-10 (fase 5). Decisiones: ADR-0001 (RLS), ADR-0005 (documentos), ADR-0006 (mínimo privilegio, outbox).
Marco legal: Ley N° 29733 (datos personales) — medidas técnicas proporcionales.

## Activos
Datos de PYMEs y transportistas (RUC, contactos, flota, conductores), ofertas y montos (información comercial sensible
entre competidores), envíos y ubicaciones, documentos/evidencias (guías, fotos, firmas), credenciales y JWT.

## Superficie y fronteras de confianza
| Frontera | Quién | Control |
|---|---|---|
| Navegador → web (Astro SSR) | cualquiera | cookie de sesión httpOnly + SameSite=Lax + Secure (prod); `checkOrigin` de Astro contra CSRF; CSP y cabeceras |
| Web/Android → API | usuarios con JWT RSA (8 h) | `@RolesAllowed`, organización solo desde el JWT (`TenantContext`), Bean Validation |
| API → PostgreSQL | `logyx_api` (solo `logyx_app`) | RLS por organización; sin dueño ni BYPASSRLS (ADR-0006) |
| API → PostgreSQL privilegiado | `logyx_owner` | solo Flyway y `SystemTx` (usos enumerados en `backend/AGENTS.md`) |
| API → S3/MinIO | credenciales de servicio | bucket privado; descarga solo vía API |

## Amenazas (STRIDE) y mitigaciones
| Amenaza | Mitigación | Residual |
|---|---|---|
| Fuerza bruta de contraseñas / registros masivos | `AuthRateLimits`: login 50/15 min por IP, bloqueo tras 10 fallos por email (15 min), registro 10/15 min por IP → `429 LOGYX-AUTH-429` + `Retry-After`. La web reenvía la IP del navegador en `X-Forwarded-For`; el backend solo la acepta de proxies confiables (`TRUSTED_PROXIES`). Contraseñas con hash adaptativo | En memoria por instancia (con N réplicas el límite efectivo es ×N): pasar a Redis/BD al escalar. Sin CAPTCHA ni MFA |
| Robo de sesión (XSS) | JWT en cookie httpOnly (no accesible a JS); CSP `default-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`; React escapa por defecto | `script-src 'unsafe-inline'` (scripts de hidratación de Astro); a futuro nonces/hashes |
| CSRF | `security.checkOrigin` de Astro en acciones y POST; SameSite=Lax | — |
| Clickjacking / sniffing | `X-Frame-Options: DENY`, `frame-ancestors 'none'`, `nosniff` en web y API | — |
| Escucha / downgrade | HSTS 1 año en prod (API siempre en `%prod`; web cuando llega por https o `HSTS=true`); TLS lo termina el proxy | El compose local es http (solo 127.0.0.1) |
| Acceso a datos de otra organización (bug de app) | Doble barrera: filtros por `TenantContext` + RLS (`RowLevelSecurityTest`) | — |
| Inyección SQL | JPA/consultas parametrizadas; conexión `logyx_api` sin privilegios de escalada: `RESET ROLE`/`SET ROLE`/`ALTER`/`DROP POLICY` fallan (`DatabasePrivilegesTest`) | Una inyección podría cambiar `app.org_id` con `set_config` y actuar como otra organización dentro de lo que permite `logyx_app` (ADR-0006) |
| Avisos falsificados para otra organización | `notifications` sin INSERT para `logyx_app`; `outbox_events` solo INSERT con `actor_org_id = app_org()` (auditable) | Con inyección se podría encolar un evento con datos inventados firmado con la propia organización |
| Pérdida de avisos / recálculos | Outbox transaccional con reintentos y dead letter (`FAILED`) | Revisar `FAILED` a mano (sin alerta automática aún) |
| Archivos maliciosos / path traversal | Formato por firma binaria (jpg/png/pdf), tamaño máx. 10 MB, claves generadas por el servidor y validadas (`..`, `/` inicial) en disco y S3; descarga por la API con RLS | Sin antivirus; sin URLs firmadas |
| Exposición de secretos | Nada en el repo: `infra/.env` y `infra/secrets/` en `.gitignore`; claves JWT montadas como secretos; contraseñas de BD por env | Rotación manual |
| CORS abusivo | En prod CORS desactivado (`CORS_ENABLED=false`): el navegador nunca llama a la API directamente (SSR); Android no usa CORS | Si se habilita, `CORS_ORIGINS` explícito |
| Fuga de datos personales en logs | Logs sin emails/RUC/teléfonos/tokens: solo ids y contadores; `quarkus.hibernate-orm.log.sql=false`; el outbox guarda solo tipo de excepción y mensaje. Swagger UI desactivado en prod | El stack trace de un 500 puede incluir valores de una restricción SQL; las contraseñas demo del seed se loguean solo en perfil dev |
| Denegación de servicio | Límites de body (12 MB), `mem_limit` en compose, pools acotados, `CONNECTION LIMIT` de `logyx_api` | Sin WAF ni rate limit general (poner en el proxy) |
| Repudio | `audit_log` en acciones críticas con usuario y organización | — |

## Checklist de despliegue
1. Contraseñas únicas para superusuario, `logyx_owner`, `logyx_api` y MinIO (o un usuario MinIO/S3 limitado al bucket).
2. `infra/scripts/gen-jwt-keys.sh` (o claves del gestor de secretos); nunca reutilizar las de otro entorno.
3. TLS en el proxy delante de web y API; `COOKIE_SECURE=true` (por omisión en prod); `TRUSTED_PROXIES` = red del proxy.
4. Backups de Postgres y del bucket; Postgres y MinIO sin puertos públicos.
5. Revisar `outbox_events` en estado `FAILED` y los logs `ERROR`.
