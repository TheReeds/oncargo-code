# ADR-0010 — Parámetros de sistema administrables, comisión y términos versionados

**Estado:** aceptado · 2026-11-17 · implementado en backend (V18) y web (ola 1, agente E)

## Contexto
La hoja de ruta v2 (`docs/product/2026-11-17-roadmap-v2.md`, ítems 1.13, 1.14, 2.14 y 2.17) y las decisiones del dueño del producto piden:
- que el **operador cambie sin desplegar** valores que hoy son configuración (SLA de asignación manual, umbrales de incidencias) y la
  **comisión** de OnCargo, con historial y auditoría;
- una **comisión porcentual al transportista** cuya tasa vigente se guarda en el envío al adjudicar;
- **términos y condiciones** (borrador pendiente de revisión legal) aceptados al registrarse, con versión y fecha;
- un **cotizador público** sin cuenta.

## Decisión

### 1. `system_parameters` con historial, y el valor por omisión sigue en la configuración
- Dos tablas (V18): `system_parameters` (valor vigente de lo que el operador cambió) y `system_parameter_history` (una fila por cambio:
  versión, valor anterior y nuevo, motivo, quién, cuándo; solo INSERT). RLS: cualquier usuario autenticado lee los vigentes; **solo el
  operador** escribe (`app_org_type() = 'OPERATOR'`).
- **Sin fila = rige `application.properties`** (`logyx.commission.default-rate`, `logyx.operator.sla`, `logyx.operator.incident-*`).
  Así los entornos y tests que fijan un valor por configuración (p. ej. `-Dlogyx.operator.sla=PT0S` en el e2e) siguen funcionando y no
  hay dos fuentes de verdad que sembrar.
- Las claves, tipos y límites viven en código (`shared/config/domain/ParameterKey`, dominio puro): el operador no crea parámetros,
  solo cambia los declarados. Agregar uno = un valor del enum + su valor por omisión.
- Cada cambio exige **motivo**, sube la **versión**, valida la **coherencia** entre parámetros (aviso < incumplimiento) y acepta
  `expectedVersion` para no pisar a otro operador (409). Se audita como `SYSTEM_PARAMETER_CHANGED`.
- **Lectura** (`SystemParameters`): por `SystemTx` (datos de plataforma, también sin usuario: endpoints públicos y jobs) con caché de 10 s
  que la escritura invalida al confirmar. Con varias instancias, un cambio tarda hasta 10 s en las demás (mismo criterio que la caché de
  sesión del ADR-0009).
- **Alcance de esta tanda:** `commission_rate`, `assignment_sla_hours`, `incident_warn_hours`, `incident_breach_hours`. Duración de la
  subasta, máximo de ofertas, piso (RN03/RN04), tarifario, feriados y ciudades **siguen fuera** (2.14): los tests fijan RN03/RN04 y un
  cambio de tarifa debe versionar la cotización guardada en cada solicitud.

### 2. Comisión
- Es un porcentaje que OnCargo retiene **al transportista** sobre el precio adjudicado; la PYME paga ese precio, **IGV 18 % incluido y
  desglosado**. `pricing/domain/CommissionPolicy` (puro) calcula valor de venta, IGV, comisión y neto; `CommissionService` expone la tasa
  vigente y el desglose a otros módulos.
- La tasa vigente se **guarda en `shipments.commission_rate`** al adjudicar (la columna la crea V18; la rellena `AcceptBidUseCase`). Un
  cambio posterior del parámetro no altera envíos existentes.
- **Supuestos por confirmar con el dueño:** (a) el precio ofertado incluye IGV; (b) la comisión se calcula sobre el total. Cambiarlos
  toca solo `CommissionPolicy`.

### 3. Términos versionados
- `logyx.terms.version` + `logyx.terms.status` (`DRAFT` hasta la revisión legal). `POST /auth/register` exige `acceptTerms` y acepta
  `termsVersion` (la que el usuario leyó; si ya no es la vigente, 409 `LOGYX-AUTH-012`). Cada aceptación es una fila de
  `terms_acceptances` (perfil, organización, versión, fecha), sin UPDATE ni DELETE, escrita por el registro (`SystemTx`).
- No se agregan columnas a `profiles`: una fila por aceptación permite pedir la nueva versión sin perder la anterior (ítem 2.17).

### 4. Cotizador público
- `POST /public/pricing/quote` (sin JWT) usa el mismo `TariffCalculator` y los km por carretera; limitado por IP
  (`AuthRateLimits`, 30 por 15 min). El servidor web reenvía la IP del visitante (`X-Forwarded-For`).
- Muestra el piso (RN03): los transportistas ya lo ven en el marketplace, así que no revela nada nuevo a quien compite.

### 5. Búsqueda y lectura del operador
- La búsqueda por texto del operador es SQL nativo de solo lectura (`NativeOperatorSearchReader`) bajo las políticas `*_operator`, sin
  tocar los módulos que poseen los datos. El chat se lee con los casos de uso de `negotiation` y **cada consulta se audita**
  (`OPERATOR_VIEWED_CHAT`); la política de privacidad lo informa.

## Consecuencias
- Un valor de RN10/RN11 pasa a ser dato: los tests que dependen de él lo fijan o lo restauran (`OperatorParametersFlowTest`).
- El historial crece con cada cambio (poco volumen); no hay borrado ni "deshacer": se corrige con otro cambio.
- Pendiente (2.14): tarifario, feriados, ciudades, subasta y piso editables; vigencia programada de un cambio.
