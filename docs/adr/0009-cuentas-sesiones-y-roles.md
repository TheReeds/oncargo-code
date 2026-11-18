# ADR-0009 — Cuentas, sesiones revocables y roles de organización

**Estado:** aceptado · 2026-11-16 · implementado en backend (V14); web y Android consumen el contrato

## Contexto
La revisión de flujos (`docs/reviews/2026-11-16-flow-gaps.md`) encontró:
- **F02**: no hay forma de recuperar ni cambiar la contraseña; el conductor depende de que su transportista lo recree.
- **F03**: `Profile.Role {ADMIN, OPS, VIEWER, DRIVER}` existía pero nadie lo miraba: un VIEWER podía aceptar ofertas.
  Una organización = un usuario.
- **F24**: el conductor pierde la sesión a las 8 h en medio de un tramo Lima → Juliaca.
- **F31**: un conductor que deja la empresa conserva su acceso.
- **F55**: el logout web solo borra la cookie: el JWT sigue valiendo 8 h.
- **F20**: no hay forma de corregir los datos de la organización.

La API usa JWT RSA sin estado (ADR-0001) y RLS por organización con dos usuarios de BD (ADR-0006).

## Decisión

### 1. Versión de sesión en el JWT (`sv`) en vez de lista de revocación
- `profiles.session_version` (int) viaja como claim `sv`. `identity/infrastructure/security/AccountAccessFilter` (un
  `@ServerRequestFilter` en cada endpoint autenticado) compara `sv` (ausente = 0, tokens anteriores a V14) con la BD y
  exige `active = true`. Si no coincide: **401 `LOGYX-AUTH-401`**, el mismo flujo que un token vencido.
- Sube al cambiar o restablecer la contraseña, cambiar el rol, desactivar al usuario, cuando un ADMIN restablece la
  contraseña de un conductor y con `POST /auth/logout-all`.
- Lectura por `SystemTx` (solo versión y estado del propio perfil) con **caché en memoria de 30 s**
  (`logyx.session.cache-ttl`). En el mismo proceso la caché se vacía al confirmar el cambio (evento CDI
  `SessionsRevoked`, `AFTER_COMPLETION`). **Con varias instancias, una revocación tarda hasta 30 s** en las demás:
  aceptable frente al costo de una consulta por petición o de un bus de invalidación (Redis). Si se escala, se puede
  bajar el TTL o publicar la invalidación por el `LISTEN/NOTIFY` que ya usa el tiempo real (ADR-0007).
- Alternativa descartada: lista de revocación por `jti`. Hay que guardar cada token emitido o revocado, no sirve para
  "cerrar todas" ni para desactivar al usuario sin enumerar sus tokens, y igual exige una consulta por petición.

### 2. Web sin refresh; refresh token rotativo solo para el móvil (opt-in)
- **Web**: `logyx.jwt.ttl = PT8H` sin refresh, en cookie httpOnly. No se acorta: con `sv` el token ya es revocable
  (cambio de contraseña, rol, desactivación, "cerrar todas"), y un refresh en la web exigiría guardar otro secreto en la
  cookie y un proxy de renovación en Astro sin ganar seguridad frente a XSS (la cookie ya es httpOnly).
- **F55, residual aceptado**: el botón "Cerrar sesión" de la web sigue sin revocar el access token (borra la cookie). Si
  alguien copió el JWT, vale hasta que venza o hasta "Cerrar sesión en todos los dispositivos".
- **Móvil (F24)**: `POST /auth/login {refresh: true}` devuelve `refreshToken` + `refreshExpiresIn`, y el access token dura
  `logyx.jwt.refresh-access-ttl = PT1H`. `POST /auth/refresh` **rota** el token (el anterior queda usado). La familia
  (un inicio de sesión) tiene un **vencimiento absoluto** de 30 días (`logyx.refresh.family-ttl`); rotar no lo extiende.
- **Detección de robo**: reutilizar un refresh ya usado revoca toda la familia (`LOGYX-AUTH-009`, se audita
  `REFRESH_TOKEN_REUSED`). También se rechaza si el perfil está inactivo o si su `sv` cambió desde que se emitió.
- `POST /auth/logout {refreshToken}` revoca la familia del dispositivo.

### 3. Tokens opacos de un solo uso
- Restablecer contraseña (`RESET`, 30 min) e invitaciones (`INVITE`, 72 h) comparten `password_tokens`; los refresh van
  en `refresh_tokens`. 32 bytes de `SecureRandom` en base64url; en la BD **solo el SHA-256** (índice único). Un hash
  rápido basta: el token tiene 256 bits de entropía (no es una contraseña elegida por una persona).
- Pedir un enlace nuevo invalida los anteriores sin usar; definir la contraseña invalida todos los del usuario.
- El token nunca va en el path (acabaría en logs de acceso): siempre en el body (`/auth/reset-password`,
  `/auth/password-tokens/inspect`, `/auth/refresh`). El enlace del correo lo lleva en la query de la **web**
  (`/restablecer?token=`), que lo pasa a la API por POST.

### 4. "Olvidé mi contraseña" sin enumeración
- Siempre **202** con el mismo cuerpo, exista o no el correo, esté activo o no. El trabajo real (buscar el perfil, emitir
  el token, enviar el correo) corre en un pool propio de 1–2 hilos con cola acotada, **fuera del hilo de la respuesta**:
  el tiempo de respuesta no depende del correo.
- Rate limit: por IP (20 / 15 min → 429, no depende del correo) y por email (3 / 15 min: al excederlo la respuesta es la
  misma pero no se envía nada). En memoria por instancia, como el de login (docs/security.md).
- Correo con Quarkus Mailer: `mock` en dev/test, SMTP por variables de entorno en prod (`SMTP_*`, `MAIL_FROM`).
- F54 (registro con un email ya usado responde "El email ya está registrado") no cambia: `LOGYX-AUTH-002` es contrato y
  la web lo usa para marcar el campo. El riesgo residual de enumeración por registro queda documentado; el
  restablecimiento no agrega otro.

### 5. Tablas de credenciales fuera del alcance de `logyx_app`
- `password_tokens` y `refresh_tokens`: `ENABLE ROW LEVEL SECURITY` **sin políticas** para `logyx_app` y
  `REVOKE ALL … FROM logyx_app` (V2 da privilegios por defecto a toda tabla nueva). Solo `SystemTx` las usa.
- **Sin `FORCE ROW LEVEL SECURITY`** (desviación del diseño inicial): `logyx_owner` no tiene `BYPASSRLS` (ADR-0006) y
  salta RLS solo por ser dueño; con `FORCE` quedaría sujeto a RLS sin política y `SystemTx` no vería nada. Agregarle una
  política `USING (true)` equivaldría a no forzar. La barrera real para la app es la falta de privilegios.

### 6. Roles de organización: el backend manda
- `ADMIN` todo; `OPS` opera (publicar, ofertar, aceptar, contraofertar, chatear, asignar, cancelar, vehículos, viajes,
  reservas, incidencias, documentos, reseñas); `VIEWER` solo lectura; `DRIVER` sin cambios (grupo JWT propio).
- **VIEWER por denegación por defecto**: el filtro rechaza todo método no seguro salvo una lista corta de autoservicio
  (`/auth/*`, marcar notificaciones leídas, `POST /pricing/calculate`, que no tiene efectos) → **403 `LOGYX-AUTH-007`** con
  un mensaje que dice a quién pedírselo (F47). Así los endpoints nuevos quedan protegidos sin anotarlos. Reglas puras en
  `identity/domain/OrgAccessPolicy` (con test).
- **Solo ADMIN**: anotación `shared.security.@OrgAdminOnly` (la hace cumplir el mismo filtro: **403
  `LOGYX-AUTH-008`**) más la comprobación en el caso de uso (`AccessDenied.requireOrgAdmin`). Cubre usuarios, perfil de la
  organización y cuentas de conductores.
- El rol del JWT es confiable porque cambiarlo sube `sv`. `UserInfo.permissions` lo traduce para los clientes.
- **Siempre un ADMIN activo**: cambiar rol o desactivar bloquea la fila de la organización (`SELECT … FOR UPDATE`, que
  bajo RLS exige la política `orgs_update_own`) antes de contar ADMIN activos (las invitaciones pendientes no cuentan).
- Invitar crea el perfil con un hash aleatorio inutilizable e `invitation_pending = true`; el invitado define su
  contraseña con el token `INVITE` y acepta la política de privacidad en ese momento (Ley N° 29733: se registra
  `consent_accepted_at`).

### 7. Perfil de la organización
- Ciudad, teléfono y dirección (nueva, opcional) siempre; razón social y RUC solo con verificación `PENDING` o `REJECTED`
  (`LOGYX-ORG-007` si no). Corregirlos estando `REJECTED` la devuelve a `PENDING` y avisa a los operadores por el outbox.
  Como el trigger de V13 solo deja al operador o a la plataforma tocar las columnas de verificación, la edición corre en
  `SystemTx` (solo sobre la organización del JWT, después de comprobar que el usuario es su ADMIN).

## Consecuencias
- Una consulta liviana por usuario cada 30 s (no por petición). Revocación inmediata en una instancia, ≤ 30 s con varias.
- Los clientes deben tratar `LOGYX-AUTH-401` en cualquier endpoint como sesión cerrada (ya lo hacen) y `LOGYX-AUTH-009`
  en `/auth/refresh` como "volver a iniciar sesión".
- Pendientes: MFA, CAPTCHA en "olvidé mi contraseña", rate limit compartido entre réplicas, avisar por correo al cambiar
  la contraseña, foto/logo de la organización.
