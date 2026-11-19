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
| Denegación de servicio | Límites de body (12 MB), `mem_limit` en compose, pools acotados, `CONNECTION LIMIT` de `logyx_api` | Sin WAF ni rate limit general en el proxy (Caddy 2.10 no trae el módulo; Cloudflare pendiente); fail2ban banea en 80/443 las IP que acumulan 429 o escanean (ver § Infraestructura del VPS). Endpoints públicos con límite por IP: login, registro, olvidé, canje, refresh, el **cotizador público** (`POST /public/pricing/quote`, 30 por 15 min, `logyx.rate-limit.public-quote.per-ip`) y el **seguimiento público** (`GET /public/tracking/{token}`, 120 por 15 min, `logyx.rate-limit.public-tracking.per-ip`) |
| Enlace público de seguimiento (ADR-0011): enumeración, fuga de datos, indexación, `Referer` | Token de 128 bits (`SecureRandom`), en BD solo su SHA-256; formato validado antes de consultar; inexistente/revocado/vencido = la misma 404 (`SHL-001`); lista blanca `PublicTrackingView` (sin precio, contactos, direcciones, placa, conductor ni ids/nombres de organizaciones) probada campo por campo; único `SystemTx` acotado al hash; límite por IP; `noindex` (cabecera y meta), `Referrer-Policy: no-referrer`, `no-store`, sin canónica; revocable al instante y vence 7 días tras la entrega; RLS: solo la PYME dueña crea/revoca (UPDATE por columna) | Quien tenga el enlace ve el avance hasta que se revoque o venza (es su propósito); el límite por IP es por instancia |
| Repudio | `audit_log` en acciones críticas con usuario y organización | — |

### Infraestructura del VPS (2026-11-19): endurecimiento, respaldos y monitoreo
Detalle, reversión y estado por control: `infra/vps/README.md` § Seguridad del host. Se aplica con `infra/vps/harden.sh`
(sistema operativo, idempotente) y `deploy.sh` (compose y Caddy); todo vive en el repo.
- **Red**: UFW (22, 80, 443 tcp/udp) + `DOCKER-USER` como lista blanca (solo 80/443 hacia contenedores, por puerto original);
  `db` y `minio` en una red `internal: true` (sin salida a Internet); `http://<IP>` redirige al dominio (ya no hay login en claro);
  `/q/*` de Quarkus cerrado en el proxy salvo `/q/health*`.
- **SSH**: solo clave, `AllowUsers deploy root`, sin reenvío X11/agente/túneles, KEX/cifrados/MAC modernos; puerto 22 (cambiarlo
  requiere aprobación). fail2ban: `sshd`, `recidive` (solo reincidentes de sshd), y sobre el log de Caddy `oncargo-caddy-probe`
  y `oncargo-caddy-auth`, que bloquean **solo 80/443** (nunca dejan al administrador sin SSH). Umbrales altos por el CGNAT móvil.
- **Contenedores**: `no-new-privileges`, `cap_drop: ALL` (+ mínimas en db/caddy), `pids_limit`, `read_only` en caddy, web y
  backend; AppArmor `docker-default`; MinIO sin consola y bucket privado.
- **Registros**: acceso de Caddy en JSON (14 días) con los tokens de URL redactados (`?t=`, `?token=`, `/s/<token>`,
  `/public/tracking/<token>`); Caddy ya oculta `Cookie` y `Authorization`. journald ≤ 1 GB / 30 días. auditd sobre accesos,
  sudoers, `authorized_keys`, `state/` y configuración de Docker/UFW/fail2ban. Las IP son dato personal (Ley 29733): retención corta.
- **Respaldos**: diarios, cifrados con age (la identidad privada **no** está en el VPS), 14 días locales, prueba de restauración
  `oncargo-backup verify` con conteos; incluyen `state/.env` (todas las claves: JWT, `LOGYX_UNSUBSCRIBE_SECRET`,
  `LOGYX_PAYOUT_KEY`). **Residual**: la copia está en el mismo disco hasta configurar el destino externo (R2).
- **Monitoreo**: `oncargo-health` cada 5 min (web, API, APK, contenedores, disco, memoria, certificado, antigüedad del respaldo);
  **residual**: sin canal de alerta ni latido externo configurados (necesitan una cuenta del usuario: ntfy/healthchecks.io/UptimeRobot).
- **Pendiente con decisión del usuario**: Cloudflare (proxy naranja, WAF gestionado, regla de rate limit en `/api/v1/auth/*`;
  **sin Bot Fight Mode**, que desafía a clientes no navegador y rompería la app Android; con proxy hay que restaurar la IP real
  con `trusted_proxies` de Caddy para los rangos de Cloudflare y limitar 80/443 a esos rangos en `DOCKER-USER`), HSTS `preload`,
  registro CAA `0 issue "letsencrypt.org"`, reinicio por parches de kernel, destino externo de respaldos y proveedor SMTP.
- **Contraseña demo pública**: `Logyx2026!` está en el repo público y abre las cuentas `*@logyx.pe` del VPS (datos demo, pero
  con acceso de operador). Propuesta: el seeder lee `DEMO_PASSWORD` (por omisión `Logyx2026!` en dev) y el VPS la define en
  `state/.env` con un valor propio que se comparte solo con los evaluadores; cambia `backend/` (DemoDataSeeder), pendiente.

### Ola 2 · zona T (V22)
- **Documentos de flota** (`fleet_documents`: SOAT, revisión técnica, licencia, DNI…): privados del transportista por RLS (`fleet_documents_owner`; el operador solo lee). Inmutables: el único `UPDATE` permitido es `superseded_at`. La PYME **no** los lee: solo recibe los sellos derivados (`bids.soat_valid_until`/`inspection_valid_until`). Descarga solo por `GET /fleet/documents/{id}/file` (propietario; `nosniff`, `no-store`). Formato por firma binaria y tamaño como la evidencia (ADR-0005).
- **Historial de precios por ruta** (RN04): `lane_price_stats` corre con los privilegios de su dueño y solo contiene agregados con k ≥ 5 envíos de ≥ 3 transportistas, redondeados a S/ 5; ningún endpoint devuelve montos individuales de otras ofertas. `bid_revisions` (historial de mejoras) solo lo lee el transportista dueño.
- **Búsquedas guardadas** (`saved_searches`): privadas de cada persona (RLS por `app_profile()`); la bitácora `saved_search_alerts` y `fleet_document_notices` no tienen política para `logyx_app` (solo el sistema). Test: `CarrierZoneRlsTest`.

### Ola 2 · zona X (V23)
- **Ajustes del envío** (`shipment_adjustments`, ADR-0012): solo los leen las dos partes, el operador y, entre los conductores, el asignado
  (política restrictiva `adjustments_driver_scope`). El transportista inserta **solo sobre un envío suyo** (la política verifica el envío,
  no solo las columnas). Nadie cambia tipo, monto ni partes: `logyx_app` tiene `UPDATE` solo sobre el estado y la decisión; sin `DELETE`.
  Notas con RN07. Cada paso queda en `audit_log`. El escalado lo hace un job con `SystemTx` que solo toca `escalated_at`.
- **Notas internas de incidencias** (`incident_comments.internal`): la RLS impide a las partes leerlas o insertarlas aunque lo intenten por
  SQL; la API las filtra además (segunda barrera). Adjuntos (`incident_attachments`): solo documentos del mismo envío de la incidencia.
  Quien reportó una informativa solo puede pasarla a `CLOSED` con la marca de retiro (política `incidents_reporter_update`).
- **Operador sobre cuentas ajenas**: `profiles_operator_update` + trigger `profiles_guard_operator_suspension`: el operador solo cambia el
  estado y la suspensión de un usuario de otra organización (no su correo, contraseña, rol ni organización); el ADMIN de la organización no
  puede levantar una suspensión del operador (`LOGYX-USR-007`). Suspender sube `session_version` y revoca los refresh.
- **Cambio de RUC** (sucesora): `SystemTx` acotado en `OrganizationSuccessionService` (inserta la sucesora y mueve personas y flota); exige
  documento de sustento (jpg/png/pdf por firma binaria, clave generada por el servidor) y motivo; todo queda en
  `organization_legal_changes` (solo el operador lee e inserta) y en la auditoría. Las personas movidas suben su versión de sesión.
- **Señales de fraude**: SQL de solo lectura bajo las políticas `*_operator` (nunca `SystemTx`). La IP del registro **no se guarda**: solo un
  SHA-256 con prefijo fijo (`organizations.signup_ip_hash`), y nunca se muestra (ni el hash). Las IP locales o privadas no se registran.
  Las señales son indicios: no bloquean nada solas.
- **«Pedir acceso a la empresa» desde el registro** (`POST /public/organization-access-requests`, sin JWT): límite por IP
  (`logyx.rate-limit.public-access-request.per-ip`, 429) y un aviso por organización cada 10 min; responde siempre igual (no confirma más
  de lo que ya dijo `ORG-002`); el correo al ADMIN advierte que el correo del solicitante no está verificado y no lleva enlaces de acceso.
- **Registro de paradas por la oficina**: motivo obligatorio, marca en la parada y auditoría `STOP_REGISTERED_BY_OFFICE`; no sube evidencia
  (RN06: la foto de entrega y la de la carga son solo del conductor asignado).

### Ola 2 · zona P (V21)
- **Tenant sintético (ADR-0013)**: las plantillas programadas publican «en nombre de» la organización con un `JsonWebToken` en memoria (`SyntheticTenant`), no con `SystemTx`: `app.org_id`/`app.profile_id` quedan fijados y la RLS sigue activa; el token no se firma ni sale de la JVM, el rol es `OPS` y la organización y la persona salen de la plantilla guardada, nunca de un cliente. El único uso nuevo de `SystemTx` es leer los **ids** de las programaciones vencidas (`DueSchedules`). Un fallo no se reintenta en bucle (3 seguidos pausan la programación).
- **Bloqueo de transportistas**: `carrier_preferences` es privada de la PYME (`prefs_owner`); el transportista no puede leerla. La política `requests_marketplace` y la restrictiva `bids_not_blocked` consultan `app_is_blocked_by(requester)`, una función `SECURITY DEFINER` (`search_path` fijo) que devuelve solo un booleano para el par «PYME, mi organización»: no revela otras marcas ni quién bloqueó a quién. El bloqueado recibe 404 y no se entera. `DemandZoneRlsTest` lo cubre.
- **Ofertas de respaldo** (`bid_backups`): la PYME dueña las gestiona; el transportista solo ve su fila cuando se le pidió confirmar (`state <> 'AVAILABLE'`, así no sabe que quedó de respaldo) y solo puede pasar de `REQUESTED` a `CONFIRMED`/`DECLINED` (`UPDATE` por columna: `state`, fechas). Nunca ve otras ofertas (RN04).
- **Acciones masivas**: cada ítem corre bajo la RLS y las reglas de la PYME (otra organización recibe 404 por ítem); máximo 50 por llamada y sin lectura de datos ajenos en el resultado.

### Ola 2 · zona F (V24): cobros, reportes y configuración
- **Datos de cobro** (`carrier_payout_accounts`): el CCI o el celular de Yape/Plin se cifran en la aplicación con **AES-256-GCM**
  (`LOGYX_PAYOUT_KEY`, IV aleatorio, etiqueta de integridad); la BD guarda bytes y los 4 últimos dígitos. RLS: el transportista (escribe
  solo su ADMIN), la PYME **solo con un envío no cancelado con él**, el operador lee, el conductor nunca. Los no ADMIN del transportista ven
  solo los 4 últimos dígitos. Sin la clave en prod esos endpoints fallan (el resto sigue); rotarla exige re-cifrar.
- **Cobros**: `shipment_settlements`/`settlement_history` solo para las partes y el operador (restrictiva sin conductor). Un trigger impide que
  una parte cambie la comisión o las partes del cobro; la comisión la registra el operador (`commission_collections`, la PYME no la lee). El
  número de operación y los comprobantes son datos de las partes: no se publican ni van en los avisos de tiempo real (payload mínimo).
- **SystemTx nuevo, acotado**: abrir el cobro (vencimiento con el plazo de la PYME), el barrido de recordatorios, leer el estado del cobro para
  bloquear ajustes (el conductor no ve cobros) y leer tarifario y calendario (datos de plataforma). Los **reportes** leen bajo RLS.
- **Tarifario, parámetros y calendario**: solo el operador escribe (RLS `app_org_type() = 'OPERATOR'`), cada cambio con motivo y auditoría;
  una versión del tarifario no se reescribe (`GRANT UPDATE` solo de la anulación).
- **CSV del reporte**: `CsvWriter` neutraliza fórmulas; misma consulta y RLS que la vista; la PYME no recibe columnas de comisión.

### Ola 2 · zona F (V24): preferencias, correo de avisos, baja sin login y términos
- **Token de baja** (`/baja?t=…`, `List-Unsubscribe`): sin estado, `base64url(perfil ‖ alcance) . base64url(HMAC-SHA256)` con
  `LOGYX_UNSUBSCRIBE_SECRET` (≥ 32 caracteres, obligatoria; el backend no arranca sin ella). Comparación en tiempo constante; no vence (un correo
  viejo debe poder darse de baja); cambiar la clave invalida todos los enlaces. Solo identifica al perfil: no lleva email, organización ni
  permisos, y lo único que permite es **apagar** correos de esa persona (nunca encender ni leer otra cosa). Token mal formado, alterado, de otra
  persona o de una cuenta desactivada: misma respuesta 404 `LOGYX-NTF-003`. Endpoints públicos con límite por IP
  (`logyx.rate-limit.public-unsubscribe.per-ip`), `SystemTx` acotado al perfil del token, lista blanca `UnsubscribeView` (email enmascarado) y
  `noindex`; el token viaja en el cuerpo hacia la API (no queda en sus logs). La web `/baja` va sin `Referer`, sin caché, `noindex` y en
  `robots.txt`; el one-click (`POST /baja/one-click`, RFC 8058) no usa cookies ni muestra datos.
- **Correo masivo**: los avisos respetan la categoría, el correo apagado, el resumen diario y el horario de silencio de cada persona; un resumen
  agrupa la cola en **un correo por persona** (la cola es solo del sistema: `REVOKE ALL` a `logyx_app`). Antes de enviar se vuelve a mirar si la
  persona sigue activa y con el correo encendido. Producción necesita **SPF, DKIM y DMARC** del dominio remitente (`infra/vps/README.md` §
  Correo) y la baja en un clic (Gmail/Yahoo la exigen para envíos masivos desde 2024). Los correos no llevan montos de comisión a la PYME ni
  datos de contacto de la contraparte (solo el título y el texto del aviso).
- **Preferencias**: `user_preferences` por persona y `organization_preferences` por organización bajo RLS (el operador solo lee); escribir las de
  la organización exige ADMIN (`@OrgAdminOnly` + `AccessDenied`). `org_route_alerts` y la cola de correo, solo sistema
  (`PreferencesRowLevelSecurityTest`).
- **Términos**: la persona solo inserta **su** aceptación y solo con `source = 'UPDATE'` (`terms_own_insert`); la de invitación la escribe la
  plataforma en el canje del token; no hay UPDATE/DELETE para `logyx_app`. El diálogo de la web no bloquea la API.
- `DevTermsResource` (`POST /api/v1/dev/terms/forget`) existe solo con `logyx.dev-mailbox=true` (dev y la imagen de pruebas), como el buzón dev.

## Checklist de despliegue
1. Contraseñas únicas para superusuario, `logyx_owner`, `logyx_api` y MinIO (o un usuario MinIO/S3 limitado al bucket).
2. `infra/scripts/gen-jwt-keys.sh` (o claves del gestor de secretos); nunca reutilizar las de otro entorno.
3. TLS en el proxy delante de web y API; `COOKIE_SECURE=true` (por omisión en prod); `TRUSTED_PROXIES` = red del proxy.
4. Backups de Postgres y del bucket; Postgres y MinIO sin puertos públicos. En el VPS: `harden.sh` instala el respaldo diario
   cifrado (`oncargo-backup`) y el chequeo de salud; falta la copia fuera del servidor y guardar la identidad age del administrador.
5. Revisar `outbox_events` en estado `FAILED` y los logs `ERROR`.
6. Proxy delante de API/web: sin buffering ni compresión en `text/event-stream` (`/api/v1/events/stream` y `/eventos` de la
   web), timeout de lectura ≥ 60 s. Con varias réplicas de la API, todas contra la misma BD (fan-out por LISTEN/NOTIFY);
   si hay PgBouncer en modo transacción, el LISTEN necesita conexión directa (o modo sesión) al datasource `app`.
7. Correo (V14): `SMTP_HOST`, `SMTP_PORT` (587), `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_START_TLS` (por omisión
   `REQUIRED`; con TLS directo en 465: `SMTP_TLS=true` y `SMTP_START_TLS=DISABLED`), `MAIL_FROM`
   (`OnCargo <no-reply@oncargo.tech>`) con SPF/DKIM/DMARC del dominio, y `PASSWORD_RESET_URL`
   (`https://oncargo.tech/restablecer`, la página de la web). `MAILER_MOCK` debe quedar sin definir (false) en
   producción. La imagen de producción no incluye el buzón dev (`logyx.dev-mailbox=false`, propiedad de build).
   V24: `LOGYX_UNSUBSCRIBE_SECRET` (obligatoria, `openssl rand -base64 48`), `WEB_URL` (https; enlaces de los correos y
   `List-Unsubscribe`), `MAIL_REPLY_TO` opcional. DMARC empieza en `p=none` y sube a `quarantine` con reportes limpios.
8. Varias réplicas: la revocación por `sv` tarda hasta `logyx.session.cache-ttl` (30 s) y los rate limits son por
   instancia.
9. Cobros (V24): `LOGYX_PAYOUT_KEY` = `openssl rand -base64 32`, guardada fuera del repo y con respaldo (perderla deja ilegibles los datos
   de cobro). `BILLING_ISSUER_NAME`/`BILLING_ISSUER_RUC` = razón social y RUC del emisor de las liquidaciones internas.

