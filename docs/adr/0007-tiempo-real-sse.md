# ADR-0007 — Tiempo real por SSE alimentado por el outbox

**Estado:** aceptado · 2026-11-10 · Continúa ADR-0004 (eventos de dominio) y ADR-0006 (outbox transaccional).

## Contexto
La web y Android hacían polling (campana ~30 s, chat ~8 s, detalle de solicitud y tracking al recargar). Se pide
tiempo real con **aislamiento estricto** (cada conexión recibe solo lo de su organización; el conductor, solo lo de sus
envíos), reanudación con `Last-Event-ID`, varias réplicas de la API y sin infraestructura nueva.

## Decisión
1. **SSE, no WebSocket.** `GET /api/v1/events/stream` (`text/event-stream`, JWT Bearer, roles PYME/CARRIER/DRIVER/OPERATOR).
   Es unidireccional (las mutaciones siguen por REST), atraviesa proxies HTTP, el navegador reconecta solo y reanuda con
   `Last-Event-ID`.
2. **Proyección `realtime_events` (V12) en vez de leer `outbox_events` directamente.** `realtime/application/RealtimeProjector`
   observa los eventos `@Outboxed` (y `NotificationsPublished`, síncrono en la misma transacción) y escribe **una fila por
   destinatario** con un payload mínimo (ids, estados, contadores; nunca montos, nombres, textos de chat ni contactos),
   dentro de la transacción de sistema del despacho: proyección, notificaciones y `DONE` se confirman juntas.
   Se descartó leer el outbox por `seq` (lo que anticipaba ADR-0006): sus payloads tienen datos sensibles y varios
   destinatarios implícitos; decidir el destinatario al leer, en cada conexión y en cada reanudación, mezclaría la regla
   de aislamiento con cada tipo de evento. Con la proyección el destinatario es un dato explícito y verificable por RLS.
3. **Destinatarios** (`realtime/domain/model/Recipient`): organización (sus usuarios no conductores), usuario de la
   organización (el conductor del envío) o difusión por tipo de organización (solo datos que ese tipo ya puede leer:
   conteo de ofertas a transportistas, RF15; incidencias a operadores). La regla única está en
   `RealtimeAudiencePolicy` (pura, con tests) y se replica en la política RLS `realtime_events_read`
   (`org_id = app_org() OR (org_id IS NULL AND org_type = app_org_type())`) más el filtro por usuario de la reanudación.
4. **Fan-out entre réplicas con `LISTEN/NOTIFY`.** Un trigger `AFTER INSERT` hace `pg_notify('logyx_realtime', seq)`, que
   PostgreSQL entrega solo al confirmarse. Cada instancia tiene un hilo (`PgRealtimeListener`) con una conexión dedicada
   del datasource `app` y pasa los `seq` a `RealtimeHub`, que lee esas filas una vez (`SystemTx`, comentado) y las entrega a
   sus conexiones locales que cumplen la política. El NOTIFY es solo un aviso: se lee la fila real, así que un NOTIFY
   forjado (cualquier rol puede emitirlo) no inventa eventos ni cambia destinatarios (test).
5. **Reanudación como el usuario.** Con `Last-Event-ID` (o `?lastEventId=`) se reenvían, en una transacción `@Transactional`
   bajo RLS y con filtro explícito por organización/usuario, las filas con `seq` mayor (máx. `logyx.realtime.replay-limit`,
   500); si hay más, `stream.resync` (el cliente recarga todo). Mientras se arma la reanudación los eventos en vivo se
   guardan en un buffer y se descartan duplicados.
6. **Ciclo de vida.** `stream.ready` (id = último seq visible) al abrir; latido `: ping` cada `logyx.realtime.heartbeat`
   (20 s); máximo `logyx.realtime.max-connections-per-user` (5, `429 LOGYX-RTM-001`); al vencer el `exp` del JWT,
   `stream.expired` y cierre (el cliente reconecta con un token nuevo). Retención `logyx.realtime.retention` (24 h).

## Consecuencias
- (+) Aislamiento demostrable en tres capas: destinatario explícito, política pura testeada y RLS en la reanudación.
  Tests: `RealtimeStreamFlowTest` (otra PYME, otro transportista, otro conductor de la misma empresa no reciben nada;
  reanudación; resync; 401/429; latido; expiración; NOTIFY forjado), `RealtimeAudiencePolicyTest`, `RowLevelSecurityTest`.
- (+) Varias réplicas sin Redis ni broker; un evento se lee una vez por instancia.
- (−) Una conexión de BD fija por instancia para el LISTEN. Entrega **al menos una vez y en el mejor esfuerzo**: si el
  listener se cae, al reconectar entrega lo posterior al último seq visto; dos transacciones que confirman en orden
  distinto al de su `seq` pueden dejar un hueco en una reanudación justo en ese instante. Por eso los eventos son
  **avisos para refrescar**: el cliente vuelve a pedir por REST lo que muestra, refresca todo al (re)conectar y mantiene
  un polling lento de respaldo.
- (−) Las conexiones SSE ocupan una conexión HTTP del cliente; en la web se usa una sola por pestaña (proxy de Astro).
- Agregar un evento: tipo en `RealtimeEventTypes`, método en `RealtimeProjector` con payload mínimo y destinatarios, fila en
  `docs/api/CHANGELOG.md` y test de aislamiento si cambia la audiencia.
