# ADR-0004 — Eventos de dominio, notificaciones y escrituras del operador

**Estado:** aceptado · 2026-11-10

## Contexto
La fase 2 agrega notificaciones in-app (RF49) disparadas por casi todos los módulos (ofertas, chat, envíos,
incidencias, calificaciones) y el recálculo del Trust Score (RN09). Si cada módulo llamara a `notification`
directamente, todos dependerían de él y cada caso de uso mezclaría su regla con la entrega de avisos.
Además la notificación es casi siempre para **otra** organización que la que actúa, lo que choca con RLS
(ADR-0001), y el operador (RN10) necesita escribir datos de otras organizaciones al asignar manualmente.

## Decisión
1. **Eventos de dominio con CDI.** Cada módulo publica records inmutables en `<modulo>/application/event/`
   (`BidPlaced`, `BidAccepted`, `MessagePosted`, `CounterOfferUpdated`, `ShipmentStatusChanged`,
   `IncidentReported`, `IncidentStatusChanged`, `ReviewSubmitted`) con `Event<T>.fire(...)` dentro de su
   transacción. Los eventos llevan los datos que el consumidor necesita (ids de las partes, nombres, montos, ruta)
   para que el consumidor no tenga que volver a leer datos protegidos.
2. **Consumidores `@Observes(during = TransactionPhase.AFTER_SUCCESS)`.** Solo se notifica lo confirmado; un fallo
   del observador no revierte el caso de uso (se registra en log). Consumidores actuales:
   `notification/application/NotificationObservers` y `reputation/application/ReputationRecalculator`.
   El publicador no conoce al consumidor (la dependencia es consumidor → `application/event` del publicador).
3. **Inserción de notificaciones en `SystemTx`.** `notifications` tiene RLS de lectura/actualización propia
   (`org_id = app_org()`) y **ninguna política de INSERT** para `logyx_app` (además `REVOKE INSERT`). Solo
   `NotificationPublisher` inserta, en una transacción de sistema nueva. Se descartó una política
   `WITH CHECK (true)`: permitiría a cualquier usuario fabricar avisos para otra organización ante un bug o una
   inyección. Lo mismo para `reputation_scores`: lectura pública, escritura solo por el recálculo en `SystemTx`
   con bloqueo de la fila (`SELECT … FOR UPDATE`) y recálculo completo desde las calificaciones confirmadas.
4. **El operador escribe con RLS, no con `SystemTx`.** La asignación manual (RN10) corre como el operador y V9
   agrega políticas acotadas a `app_org_type() = 'OPERATOR'`: INSERT en `bids` (solo con
   `assigned_by_operator = true`), UPDATE en `bids` y `shipment_requests`, INSERT en `shipments` y
   `shipment_stops`; y en V7, UPDATE de `incidents`. Así la acción queda auditada con la organización operadora y
   el resto de tablas sigue protegido.

## Consecuencias
- (+) Módulos desacoplados: agregar un aviso nuevo es un observer, sin tocar el caso de uso.
- (+) Las notificaciones reflejan solo transacciones confirmadas.
- ~~(−) Entrega "al menos una vez" no garantizada: si el proceso cae entre el commit y el observer, el aviso se
  pierde.~~ **Resuelto en ADR-0006 (2026-11-10):** outbox transaccional (`outbox_events`, V11). Los consumidores ahora
  observan `@Outboxed` (no `AFTER_SUCCESS`) y corren en la transacción del despacho, con reintentos. Email (RF50), push y
  tiempo real se alimentarán del mismo outbox.
- (−) Los observers corren en el hilo de la petición (latencia extra pequeña). Si crece, pasar a `@ObservesAsync`
  o a un outbox.
- `SystemTx` suma dos usos justificados: `NotificationPublisher` y `ReputationRecalculator`.
