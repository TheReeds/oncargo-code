# ADR-0011 — Enlace público de seguimiento de un envío

**Estado:** aceptado · 2026-11-18 · implementado en backend (V19), web y Android (Ola 1, zona B)

## Contexto
La PYME quiere mandar a **su** cliente final (por WhatsApp) un enlace para que vea por dónde va el pedido sin crear una cuenta
(roadmap v2 ítem 1.6). Es la **primera superficie pública con datos de un envío**: hasta ahora todo lo que tocaba un envío exigía JWT y
pasaba por la RLS de las partes (ADR-0001). Riesgos: enumeración de enlaces, fuga de datos comerciales (precio, contraparte, placa,
contactos que RN07 solo revela a las partes tras adjudicar), indexación por buscadores, fuga del token por `Referer` y abuso del endpoint.

## Decisión

### 1. Token aleatorio, solo su hash en la base de datos
- 16 bytes de `SecureRandom` (128 bits) en base64url sin relleno: 22 caracteres (`shipment/domain/model/ShareToken`).
- En `shipment_share_links` (V19) se guarda **solo el SHA-256** (`token_hash`, único) y 4 caracteres (`token_hint`) para que la PYME
  reconozca cada enlace en la lista. Quien lea la tabla (copia de seguridad, consulta del operador) no puede reconstruir un enlace.
- El token en claro existe **una sola vez**: en la respuesta de `POST /shipments/{id}/share-links` (`token` y `url`). Después no se
  vuelve a mostrar; si se pierde, se crea otro. Consecuencia aceptada: el Lector (VIEWER) ve la lista y las visitas pero no puede
  «copiar uno existente» (lo pedía el roadmap); guardarlo cifrado o derivarlo con HMAC exigiría gestionar una clave nueva en
  producción y debilitaría la protección ante una fuga de la BD.
- Antes de consultar la BD se descarta lo que no tiene el formato de un token.

### 2. Vigencia y revocación
- Caducidad opcional al crearlo (1–30 días) y, en todo caso, **7 días después de la entrega** (o de la cancelación). Revocable al
  instante (`POST …/share-links/{linkId}/revoke`, idempotente). Hasta 10 vigentes por envío. No se comparte un envío cancelado ni uno
  entregado hace más de 7 días (`shipment/domain/ShareLinkPolicy`).
- **Inexistente, mal formado, revocado o vencido responden exactamente lo mismo**: 404 `LOGYX-SHL-001` «Este enlace ya no está
  disponible». No hay forma de saber si un token existió.

### 3. Lectura pública por `SystemTx`, acotada y con lista blanca
- `GET /api/v1/public/tracking/{token}` (sin JWT) no tiene usuario ni organización, así que la RLS no dejaría leer nada: resuelve por
  **`SystemTx`** (ADR-0006) en `shipment/application/PublicTrackingService`, **único** uso de `SystemTx` de esta superficie: busca por
  hash, comprueba la vigencia, cuenta la visita (`views`, `last_viewed_at`) y arma `PublicTracking`.
- **Lista blanca** (`PublicTracking` → `PublicTrackingView`): código del envío, estado, ciudades de origen y destino, fecha de recojo,
  entregado el, hitos (tipo, ciudad, estado y horas de cada parada), ETA aproximada como **ventana** (`EtaPolicy`: tiempo de manejo por
  carretera que falta desde el último hito, ajustado a camión, con descansos y redondeado a media hora; sin GPS) y la ruta por carretera
  (polyline del dataset de ADR-0008 con su atribución). **Nunca**: precio, contactos, direcciones, placa, conductor, nombres o ids de las
  organizaciones, ids del envío, de la solicitud o de las paradas. Un campo nuevo del envío no sale por aquí si no se agrega a la lista
  blanca; el test `ShipmentTrackingFlowTest` compara el conjunto exacto de claves y busca en el cuerpo crudo los datos sensibles.
- Sin SSE (ADR-0007 exige sesión): la página consulta cada 60 s mientras está visible.

### 4. Quién gestiona los enlaces (RLS)
- `shipment_share_links`: la PYME dueña del envío lee, crea (con `WITH CHECK` de que el envío es suyo) y revoca (privilegio por columna:
  solo `revoked_at`/`revoked_by`); el operador lee; nadie borra; el transportista y el conductor no los ven. ADMIN y OPS crean y revocan;
  el Lector solo lee (`AccountAccessFilter`). Crear y revocar se auditan (`SHARE_LINK_CREATED`/`SHARE_LINK_REVOKED`, entidad `shipment`).

### 5. Defensa en profundidad de la página
- Límite por IP en el backend (`AuthRateLimits.beforePublicTracking`, `logyx.rate-limit.public-tracking.per-ip`, 120 por 15 min →
  429 con `Retry-After`). La web (Astro) reenvía la IP del visitante en `X-Forwarded-For`; el backend solo la toma de proxies de
  confianza (`TRUSTED_PROXIES`).
- `X-Robots-Tag: noindex, nofollow` y `<meta name="robots" content="noindex">`; `Referrer-Policy: no-referrer` (cabecera y meta) para
  que el token no viaje a los teselas del mapa ni a otros sitios; `Cache-Control: no-store`; sin `<link rel="canonical">`.
- Web: `/s/<token>` y su consulta `/s/<token>/estado` son públicas en el middleware (también con sesión iniciada).

## Consecuencias
- La PYME tiene una forma oficial de informar a su cliente sin reenviar capturas ni datos del transportista (RN07 se mantiene: el
  contacto estructurado sigue siendo solo para las partes).
- El límite por IP es en memoria (por instancia), como el resto de límites (ver `docs/security.md`).
- La ETA es orientativa; cuando haya GPS del conductor (Ola 3) `EtaPolicy` partirá de la última posición.
- Si en el futuro se quiere «volver a copiar» un enlace, hará falta guardar el token cifrado con una clave administrada (nuevo ADR).
