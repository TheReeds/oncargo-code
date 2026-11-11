# ADR-0001 — Aislamiento por organización (no multitenancy completo)

**Estado:** aceptado · 2026-11-10

## Contexto
Se evaluó si LOGYX debía ser multitenant. LOGYX es un **marketplace**: su flujo central cruza
organizaciones (una PYME publica, varios transportistas ofertan, uno ejecuta). Los modelos clásicos de
multitenancy SaaS —BD por tenant, schema por tenant o discriminador `@TenantId` de Hibernate— asumen que
los datos de un tenant nunca se cruzan con otro, lo que rompería el marketplace y obligaría a consultas
entre schemas.

## Decisión
Una sola BD y un solo schema con datos **org-scoped**:
1. Cada fila privada lleva la organización dueña (`organization_id`, `requester_org_id`, `carrier_org_id`).
   Donde hace falta, se **denormaliza** (p. ej. `bids.requester_org_id`) para que las políticas no
   consulten otras tablas protegidas (evita recursión en RLS).
2. **Primera barrera — aplicación:** la organización actual sale del JWT (`TenantContext`); los servicios
   filtran siempre por ella. El cliente nunca envía `organizationId`.
3. **Segunda barrera — PostgreSQL RLS** (`V2__row_level_security.sql`): al iniciar cada transacción de un
   usuario autenticado, `TenantSessionBinder` ejecuta `set_config('app.org_id', …, true)` y
   `SET LOCAL ROLE logyx_app` (rol sin `BYPASSRLS`). Si el código olvida un `WHERE`, la BD igual filtra.
4. Operaciones de plataforma que cruzan organizaciones por definición (login, registro, scheduler, conteo
   agregado de ofertas, unicidad global de placa/email) corren en `SystemTx`, sobre un datasource privilegiado
   separado (ADR-0006). Cada uso de `SystemTx` debe estar comentado con su justificación.

Datos públicos del marketplace (solicitudes `PUBLISHED`, perfil público de organizaciones) son visibles
entre organizaciones por política explícita; la flota, usuarios, ofertas ajenas y envíos no.

## Consecuencias
- (+) El marketplace funciona sin fricción; el aislamiento está probado a nivel BD (`RowLevelSecurityTest`).
- (+) Defensa en profundidad exigida por la Ley N° 29733 (medidas técnicas de seguridad).
- (−) Todo acceso a datos de usuario debe ir en `@Transactional`; lecturas fuera de transacción no activan RLS.
- ~~(−) La app se conecta como dueño del esquema y baja a `logyx_app` con `SET LOCAL ROLE`; una inyección SQL
  podría hacer `RESET ROLE`.~~ **Resuelto en ADR-0006 (2026-11-10):** la app se conecta como `logyx_api` (LOGIN,
  miembro solo de `logyx_app`); Flyway y `SystemTx` usan un datasource separado con `logyx_owner`. Sin JWT y fuera de
  `SystemTx` ya no hay privilegios implícitos (fail-closed). Riesgo residual: suplantar `app.org_id` vía `set_config`
  con una inyección (ver ADR-0006).
- Si en el futuro LOGYX se vende como SaaS de marca blanca a cooperativas, se reevalúa (schema por cooperativa).
