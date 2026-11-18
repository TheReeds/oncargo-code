# Seguridad de LOGYX — modelo de amenazas resumido

Actualizado 2026-11-10 (fase 5); 2026-11-16 cuentas, sesiones y roles (V14). Decisiones: ADR-0001 (RLS), ADR-0005
(documentos), ADR-0006 (mínimo privilegio, outbox), ADR-0009 (sesiones revocables, refresh, tokens, roles).
Marco legal: Ley N° 29733 (datos personales) — medidas técnicas proporcionales.

## Activos
Datos de PYMEs y transportistas (RUC, contactos, flota, conductores), ofertas y montos (información comercial sensible
entre competidores), envíos y ubicaciones, documentos/evidencias (guías, fotos, firmas), credenciales y JWT.

## Superficie y fronteras de confianza
| Frontera | Quién | Control |
|---|---|---|
| Navegador → web (Astro SSR) | cualquiera | cookie de sesión httpOnly + SameSite=Lax + Secure (prod); `checkOrigin` de Astro contra CSRF; CSP y cabeceras |
| Web/Android → API | usuarios con JWT RSA (web 8 h; móvil con refresh: 1 h + refresh rotativo de 30 días) | `@RolesAllowed`, organización solo desde el JWT (`TenantContext`), versión de sesión `sv` + rol de organización (`AccountAccessFilter`), Bean Validation |
| API → PostgreSQL | `logyx_api` (solo `logyx_app`) | RLS por organización; sin dueño ni BYPASSRLS (ADR-0006) |
| API → PostgreSQL privilegiado | `logyx_owner` | solo Flyway y `SystemTx` (usos enumerados en `backend/AGENTS.md`) |
| API → S3/MinIO | credenciales de servicio | bucket privado; descarga solo vía API |

## Amenazas (STRIDE) y mitigaciones
| Amenaza | Mitigación | Residual |
|---|---|---|
| Fuerza bruta de contraseñas / registros masivos | `AuthRateLimits`: login 50/15 min por IP, bloqueo tras 10 fallos por email (15 min), registro 10/15 min por IP, canje de enlaces 30/15 min por IP, refresh 300/15 min por IP, 10 contraseñas actuales incorrectas por usuario en `change-password` → `429 LOGYX-AUTH-429` + `Retry-After`. La web reenvía la IP del navegador en `X-Forwarded-For`; el backend solo la acepta de proxies confiables (`TRUSTED_PROXIES`). Contraseñas con hash adaptativo | En memoria por instancia (con N réplicas el límite efectivo es ×N): pasar a Redis/BD al escalar. Sin CAPTCHA ni MFA |
| Token robado o sesión que debía cerrarse (F55, F31) | Claim `sv` = `profiles.session_version`, comparado en cada petición (caché 30 s): sube al cambiar/restablecer la contraseña, cambiar el rol, desactivar al usuario o al conductor y con "cerrar todas las sesiones" → 401 `LOGYX-AUTH-401` | El logout web solo borra la cookie (el JWT vale hasta 8 h o hasta "cerrar todas"); con varias réplicas la revocación tarda hasta 30 s |
| Robo de refresh token (móvil) | Opt-in, rotativo, un solo uso, hash SHA-256 en BD, vencimiento absoluto 30 días; reutilizar uno usado revoca la familia (`REFRESH_TOKEN_REUSED` en auditoría); se rechaza si cambió `sv` o el usuario está inactivo | El robo se detecta al reutilizarse, no antes |
| Enumeración de cuentas por "olvidé mi contraseña" | Siempre 202 con el mismo cuerpo; el trabajo (búsqueda, token, correo) corre fuera del hilo de la respuesta; límite por email sin cambiar la respuesta (3 / 15 min) y por IP (20 / 15 min → 429) | El registro sigue diciendo "El email ya está registrado" (F54, `LOGYX-AUTH-002` es contrato) |
| Enlaces de restablecimiento o invitación filtrados | 256 bits, solo el SHA-256 en BD, un solo uso, 30 min (reset) / 72 h (invitación), pedir otro invalida los anteriores; el token nunca va en el path de la API; `password_tokens` y `refresh_tokens` sin privilegios para `logyx_app` (RLS sin políticas) | Quien controla el buzón del usuario controla la cuenta (sin MFA) |
| Escalada dentro de la organización (F03) | VIEWER: denegación por defecto de métodos no seguros (`LOGYX-AUTH-007`); `@OrgAdminOnly` + chequeo en el caso de uso para usuarios, perfil y conductores (`LOGYX-AUTH-008`); siempre un ADMIN activo (fila de la organización bloqueada); todo en `audit_log` | — |
| Robo de sesión (XSS) | JWT en cookie httpOnly (no accesible a JS); CSP `default-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`; React escapa por defecto | `script-src 'unsafe-inline'` (scripts de hidratación de Astro); a futuro nonces/hashes |
| CSRF | `security.checkOrigin` de Astro en acciones y POST; SameSite=Lax | — |
| Clickjacking / sniffing | `X-Frame-Options: DENY`, `frame-ancestors 'none'`, `nosniff` en web y API | — |
| Escucha / downgrade | HSTS 1 año en prod (API siempre en `%prod`; web cuando llega por https o `HSTS=true`); TLS lo termina el proxy | El compose local es http (solo 127.0.0.1) |
| Acceso a datos de otra organización (bug de app) | Doble barrera: filtros por `TenantContext` + RLS (`RowLevelSecurityTest`) | — |
| Inyección SQL | JPA/consultas parametrizadas; conexión `logyx_api` sin privilegios de escalada: `RESET ROLE`/`SET ROLE`/`ALTER`/`DROP POLICY` fallan (`DatabasePrivilegesTest`) | Una inyección podría cambiar `app.org_id` con `set_config` y actuar como otra organización dentro de lo que permite `logyx_app` (ADR-0006) |
| Avisos falsificados para otra organización | `notifications` sin INSERT para `logyx_app`; `outbox_events` solo INSERT con `actor_org_id = app_org()` (auditable) | Con inyección se podría encolar un evento con datos inventados firmado con la propia organización |
| Fuga entre organizaciones por el stream en tiempo real (SSE) | Destinatario explícito por fila en `realtime_events` (organización / usuario-conductor / difusión por tipo solo con datos públicos para ese tipo); `RealtimeAudiencePolicy` (pura, testeada) en el hub; reanudación bajo RLS (`realtime_events_read`) + filtro explícito; payload mínimo sin montos/nombres/textos; el NOTIFY es solo un aviso (se lee la fila real: un NOTIFY forjado no cambia destinatarios). `RealtimeStreamFlowTest` prueba que otra PYME, otro transportista y otro conductor de la misma empresa no reciben nada | Entrega al mejor esfuerzo (los clientes refrescan por REST) |
| Agotamiento por conexiones SSE | Máx. 5 streams por usuario (`429 LOGYX-RTM-001`), latido 20 s que detecta caídas, cierre al vencer el JWT; la web abre una sola conexión por pestaña vía proxy (`/eventos`) que aborta la petición al backend si el navegador corta | El límite es por instancia; el proxy debe permitir respuestas largas (`X-Accel-Buffering: no`, timeouts de lectura > 20 s) |
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
6. Proxy delante de API/web: sin buffering ni compresión en `text/event-stream` (`/api/v1/events/stream` y `/eventos` de la
   web), timeout de lectura ≥ 60 s. Con varias réplicas de la API, todas contra la misma BD (fan-out por LISTEN/NOTIFY);
   si hay PgBouncer en modo transacción, el LISTEN necesita conexión directa (o modo sesión) al datasource `app`.
7. Correo (V14): `SMTP_HOST`, `SMTP_PORT` (587), `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_START_TLS` (por omisión
   `REQUIRED`; con TLS directo en 465: `SMTP_TLS=true` y `SMTP_START_TLS=DISABLED`), `MAIL_FROM`
   (`OnCargo <no-reply@oncargo.tech>`) con SPF/DKIM/DMARC del dominio, y `PASSWORD_RESET_URL`
   (`https://oncargo.tech/restablecer`, la página de la web). `MAILER_MOCK` debe quedar sin definir (false) en
   producción. La imagen de producción no incluye el buzón dev (`logyx.dev-mailbox=false`, propiedad de build).
8. Varias réplicas: la revocación por `sv` tarda hasta `logyx.session.cache-ttl` (30 s) y los rate limits son por
   instancia.
