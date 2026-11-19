# ADR-0014 — Cobros sin pasarela, tarifario versionado y documentos internos

**Estado:** aceptado · 2026-11-19 · implementado en backend (V24) y web (Ola 2, zona F; roadmap v2 ítems 2.11, 2.12 y 2.14)
**Relacionadas:** ADR-0010 (parámetros y comisión), ADR-0012 (ajustes y precio final), ADR-0001/0006 (RLS, `SystemTx`).

## Contexto
Con la comisión (ADR-0010) y el precio final (ADR-0012) definidos, faltaba: saber si cada envío entregado se cobró, cobrarle
la comisión al transportista, darles a las partes reportes y un documento mensual, y que el operador cambie sin desplegar
el tarifario, la subasta (RN04), el piso (RN03) y los feriados que usan las plantillas recurrentes. No hay pasarela de pago
(RNF20: no tocamos tarjetas) ni facturación electrónica SUNAT (Ola 3).

## Decisión

### 1. Un cobro por envío entregado, registrado fuera de la plataforma
- Tabla `shipment_settlements` (V24), una fila por envío **entregado**. La abre el sistema al entregarse
  (`SettlementReminders.onDelivered`, consumidor `@Outboxed` de `ShipmentStatusChanged`), el barrido horario o, si aún no
  existe, el primer paso de una parte (todo con el mismo `INSERT … ON CONFLICT DO NOTHING` por `SystemTx`: el vencimiento usa
  el plazo de pago de la PYME, que el transportista no puede leer).
- Estados (`SettlementStatus`, roadmap 2.12): `PENDING_INVOICE` → `INVOICED` (el transportista registra **su** factura o
  boleta: número, fecha, adjunto opcional `CARRIER_INVOICE`) → `PAID` (la PYME registra el pago: transferencia, depósito,
  Yape, Plin, efectivo u otro, número de operación salvo efectivo, fecha, monto y constancia opcional `PAYMENT_PROOF`) →
  `CONFIRMED` (el transportista confirma que recibió el dinero). La PYME puede pagar antes de la factura. `DISPUTED`: una
  parte objeta un cobro facturado o pagado; el operador elige a qué estado vuelve (con nota) o quien la abrió la retira.
- **Vencimiento** = fecha de entrega (Lima) + `organization_preferences.payment_terms_days` de la PYME (2.2) o el parámetro
  `payment_terms_days` (30). Recordatorios a la PYME 3 días antes, el día y al vencer (una vez cada uno, bitácora
  `settlement_notices`).
- **Comisión de OnCargo** (`commission_status`): `PENDING` / `COLLECTED` / `NOT_APPLICABLE` (tasa 0 o envío anterior a V18).
  La cobra el **operador** registrando un pago del transportista a OnCargo (`commission_collections`: medio, operación, fecha,
  envíos); el monto lo calcula el backend (comisión + IGV de la comisión de cada envío sobre su precio final). Un trigger
  impide que una parte toque esas columnas. La PYME nunca ve la comisión (ADR-0010).
- **Bloqueo de ajustes**: desde `INVOICED` el precio final queda fijo; `AdjustmentService.propose/decide(aceptar)` responde
  `LOGYX-ADJ-009` (puerto `shipment.SettlementLock`, SQL por `SystemTx` porque el conductor no ve cobros). Facturar o pagar con
  ajustes pendientes da `LOGYX-STL-003`. Las dos operaciones toman el mismo candado consultivo `shipment-adjust:<envío>`. Si
  hay un error de precio tras facturar, se abre una disputa y el operador puede devolver el cobro a `PENDING_INVOICE`, lo que
  reabre los ajustes. Se eligió «desde la factura» y no «desde el pago» porque la factura es el primer documento con monto:
  un ajuste posterior la dejaría mal.
- **Datos de cobro del transportista** (`carrier_payout_accounts`): CCI de 20 dígitos o Yape/Plin (celular). El número se
  cifra en la aplicación con **AES-256-GCM** (`LOGYX_PAYOUT_KEY`, base64 de 32 bytes; IV aleatorio por valor); la BD guarda
  bytes y los 4 últimos dígitos. RLS: el transportista (escribe solo su ADMIN), la PYME **solo si tiene un envío no cancelado
  con él**, el operador lee; el conductor nunca. Rotar la clave exige re-cifrar (pendiente: comando de rotación).

### 2. Tarifario versionado con vigencia
- `tariff_versions`: base, combustible por km, transporte por t·km, peaje (precio y cada cuántos km), manipuleo (% del
  transporte) y multiplicador por tipo de carga. La versión 1 son las constantes que tenía `TariffCalculator` (no cambia
  ningún precio al migrar). La vigente = la de mayor número con `effective_from <= ahora` y sin anular; una futura queda
  programada y se puede anular antes de que rija. Nunca se edita una versión (`GRANT UPDATE` solo de `cancelled_*`).
- `TariffCalculator` recibe la `Tariff`; `PricingService` usa `TariffService.current()` (caché de 10 s por `SystemTx`, como
  `SystemParameters`: el cotizador público y las plantillas cotizan sin usuario). Cada solicitud guarda `tariff_version`.

### 3. Parámetros nuevos y calendario
- `ParameterKey` suma `auction_duration_minutes` y `auction_max_bids` (RN04), `price_floor_ratio` (RN03) y
  `payment_terms_days` (tipos `MINUTES`, `COUNT`, `DAYS`). Sin fila rige la configuración (`logyx.auction.*`,
  `logyx.pricing.floor-ratio`, `logyx.settlement.default-terms-days`): los tests y el perfil dev siguen igual.
- `calendar_days` (catálogo): feriados, paros y avisos, nacionales o por ciudades, con `skip_recurrence`. Siembra los feriados
  nacionales 2026–2028. Las plantillas recurrentes (ADR-0013) consultan `CalendarService.skipsRecurrence(fecha, origen)`; un
  año sin feriados cargados usa la lista nacional fija (`NationalHolidays`) para no dejar de saltar Navidad. La web muestra
  un aviso global (hoy a pasado mañana) y un aviso al elegir la fecha de recojo. «Pausar subastas de una región» queda
  pendiente.

### 4. Reportes y documentos internos: HTML imprimible, no PDF del servidor
- `reports` lee bajo RLS (nunca `SystemTx`) los envíos no cancelados y calcula cada línea con el **precio final** y
  `CommissionPolicy` (misma aritmética que el envío). Agrega en dominio puro (`ReportAggregator`).
- «Facturas» de 2.11 son **documentos internos**: liquidación de comisión al transportista (`LIQ-aaaamm-…`) y resumen de
  costos de la PYME (`RES-…`), por mes de entrega, con la leyenda «no es un comprobante de pago electrónico (SUNAT)».
- **Formato: HTML imprimible** (página de la web con `@media print`, «Imprimir o guardar PDF») en lugar de una librería PDF.
  Razones: cero dependencias ni cambios en `pom.xml` (OpenPDF es LGPL/MPL y PDFBox pesa ~3 MB y es de bajo nivel), mismo
  sistema de diseño y accesibilidad que el resto, el navegador ya genera un PDF fiel, y en la Ola 3 el comprobante oficial
  (PDF + XML firmado) lo emitirá el proveedor de facturación electrónica: un generador propio sería trabajo descartable.
  Costo: no hay un archivo para adjuntar en un correo; si se necesita, se agrega una librería entonces.
- CSV del reporte con `CsvWriter` (misma consulta que la vista; columnas de comisión solo para transportista y operador).

## Consecuencias
- Los montos de cobros, reportes, CSV y liquidaciones cuadran con la vista del envío (pruebas: `SettlementFlowTest`,
  `ReportsFlowTest`, `ReportAggregatorTest`, `SettlementPolicyTest`, `FinanceRowLevelSecurityTest`).
- El cumplimiento de pago aún no entra al Trust Score (RF43): queda pendiente con los datos de `settlement_history`.
- Un cambio de tarifa o de parámetros afecta solo lo nuevo: las solicitudes publicadas conservan su sugerido y piso.
