# ADR-0012 — Ajustes del envío y precio final

**Estado:** aceptado · 2026-11-19 · implementado en backend (V23), web y Android (Ola 2, zona X; roadmap v2 ítem 2.6)

## Contexto
El precio adjudicado (la oferta aceptada) no siempre es lo que se termina cobrando: la carga pesa más (o menos) que lo
declarado, el conductor espera horas en un almacén (estadía), hay peajes o maniobras de carga y descarga que nadie previó.
Hoy eso se arregla por fuera de la plataforma y los reportes, el CSV y la comisión de OnCargo (ADR-0010) quedan desalineados con
lo que de verdad se cobró. Es además el primer paso hacia los cobros (roadmap 2.12).

## Decisión

### 1. Un ajuste es una propuesta del transportista que decide la PYME
- Tabla `shipment_adjustments` (V23): tipo (`WEIGHT_DIFF`, `DETENTION`, `TOLL`, `HANDLING`, `OTHER`), monto **con IGV** (como el
  precio adjudicado), nota, evidencia (un documento del mismo envío), parada opcional (estadía en un recojo o una entrega), estado
  (`PENDING`, `ACCEPTED`, `REJECTED`, `WITHDRAWN`) y la decisión (quién, cuándo, nota).
- Lo **propone el transportista**: su oficina (ADMIN/OPS) o el conductor asignado (desde la app, también sin señal con
  `Idempotency-Key`). La PYME no propone: si quiere un descuento, lo pide por incidencia (`PAYMENT_DISPUTE`).
- Lo **decide la PYME** (ADMIN/OPS) dentro de `adjustment_response_hours` (parámetro de sistema, 48 h por omisión). Rechazar exige
  motivo. Si no responde, un job (`AdjustmentScheduler`, `SystemTx`) lo **escala**: queda pendiente con `escalated_at` y desde ese
  momento **solo el operador** lo acepta o rechaza (`LOGYX-ADJ-006`). Nadie más decide.
- Reglas (`shipment/domain/AdjustmentPolicy`): envío no cancelado (`ADJ-001`), hasta 7 días después de entregado (`ADJ-002`), monto
  ≠ 0 con 2 decimales y |monto| ≤ precio acordado; **solo el peso real u «otro» pueden ser descuentos** (`ADJ-003`); nota
  obligatoria en «otro» (`ADJ-004`), sin datos de contacto (RN07); hasta 10 por envío (`ADJ-005`). RN03 no aplica: el piso es de
  la oferta, no del precio final.
- Inmutable salvo la decisión: `logyx_app` solo tiene `UPDATE` sobre `status`, `escalated_at` y las columnas de la decisión; sin
  `DELETE`. Las partes y el operador leen; un conductor solo los de sus envíos (política restrictiva, como `stops_driver_scope`). Cada
  paso se audita (`ADJUSTMENT_PROPOSED|ACCEPTED|REJECTED|WITHDRAWN|ESCALATED`) y avisa por el outbox.

### 2. Precio final = acordado + aceptados, y la comisión se aplica al precio final
- `ShipmentView.finalPrice` = `agreedPrice` + suma de los ajustes **aceptados** (nunca negativo); `adjustmentsTotal` y
  `pendingAdjustments` acompañan. `finalPriceBreakdown` usa el mismo `CommissionService.breakdown` que el precio adjudicado, con la
  **tasa guardada en el envío al adjudicar** (ADR-0010): la PYME ve el IGV desglosado; el transportista y el operador, además la
  comisión y «cobras S/ X neto».
- **La comisión aplica sobre el precio final**, ajustes incluidos (también peajes). Alternativa descartada: excluir los peajes de la
  base. Complica la regla (una base por tipo), obliga a separar el IGV de cada ajuste en la factura de OnCargo y abre una vía para
  mover precio a «peaje». Si el dueño decide excluir algún tipo, el cambio queda en `PriceBreakdownView`/`CommissionPolicy` con una
  base distinta, sin migración.
- El CSV de envíos suma «Ajustes aceptados (S/)» y «Precio final (S/)» (misma consulta y RLS que el listado).

### 3. Sugerencias, no cobros automáticos
- **Estadía:** si en una parada `completedAt − arrivedAt` supera `detention_free_hours` (3 h), se sugiere cada media hora iniciada por
  encima a `detention_hourly_rate` (S/ 40 por hora, con IGV). Usa la hora del evento (la del dispositivo acotada, ADR de 1.11).
- **Peso:** si el peso medido en el recojo (2.5) supera al declarado en más de 5 %, se sugiere el ajuste proporcional.
- Las sugerencias solo las ve el transportista (`GET /shipments/{id}/adjustments`) y no se cobran solas: las propone con un toque.
  Una parada con estadía ya propuesta o aceptada deja de sugerirse.

## Consecuencias
- Positivas: el precio real queda registrado, auditado y consistente en la vista, el CSV y la comisión; la PYME conserva el control y
  el silencio no la perjudica sin que lo vea alguien (el operador).
- Negativas: la comisión de OnCargo sobre los peajes puede discutirse; queda documentado arriba cómo cambiarlo. Los reportes de 2.11
  deben leer `finalPrice`, no `agreedPrice`.
- Pendiente (2.12 cobros): marcar el envío como cobrado y bloquear ajustes nuevos después del cobro.
