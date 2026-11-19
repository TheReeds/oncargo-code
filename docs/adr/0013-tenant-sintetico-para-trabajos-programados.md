# ADR-0013 — Tenant sintético para trabajos programados en nombre de una organización

- **Estado:** aceptada (2026-11-19, Ola 2 zona P, roadmap 2.1).
- **Relacionada:** ADR-0001 (RLS), ADR-0006 (dos usuarios de BD, `SystemTx`), ADR-0004 (eventos y avisos).

## Contexto
Las plantillas recurrentes (roadmap 2.1) publican solicitudes **sin que haya una persona conectada**: un job decide que
«el lunes toca publicar la carga de Juliaca a Arequipa» y debe crearla como si la hubiera publicado esa PYME (RN01 con
verificación, RN03 con el precio recalculado, RN07 sobre los textos, auditoría, avisos). Hasta ahora lo que corría sin
usuario entraba por `SystemTx` (datasource privilegiado, **sin RLS**): válido para cerrar subastas o repartir avisos entre
organizaciones, pero no para escribir datos de una organización con sus reglas, porque cualquier bug podría tocar datos de otra.

## Decisión
Los trabajos que actúan **en nombre de** una organización corren con un **tenant sintético**:
1. El job busca qué toca con `SystemTx` y devuelve **solo ids** (`DueSchedules`: programación, organización, autor de la plantilla).
2. Cada ocurrencia se ejecuta con `SyntheticTenant.runAs(orgId, orgType, profileId) { … }`: activa un contexto de petición
   cuyo `JsonWebToken` en memoria lleva `org_id`, `org_type`, `role = OPS` y `sub` = la persona que creó la plantilla.
3. A partir de ahí todo es una petición normal: `TenantContext` lee ese token, `TenantSessionBinder` fija `app.org_id`,
   `app.org_type`, `app.profile_id` y baja a `logyx_app`. **La RLS sigue activa**: el trabajo solo puede hacer lo que esa
   organización podría hacer. Se usa el mismo caso de uso (`PublishRequestUseCase.publishOnce`) que la API.
4. Cada ocurrencia es su propia transacción (`ScheduleStep.attempt`); si falla, otra transacción (`recordFailure`) registra el
   fallo y avanza la programación. **No hay reintento en bucle**: un fallo se avisa a la persona (aviso `TEMPLATE_RUN`) y a
   los 3 fallos seguidos la programación se pausa. La unicidad `(schedule_id, pickup_date)` y la `Idempotency-Key`
   `sch:<programación>:<recojo>` evitan duplicados si dos instancias o un reintento coinciden; además un candado consultivo
   por programación serializa las instancias.

## Reglas para quien lo use
- La organización y la persona salen de **datos ya guardados** (la plantilla y su autor), nunca de la entrada de un cliente.
- El token sintético nunca sale de la JVM ni se firma: no es una credencial. El rol es siempre `OPS` (opera, no gestiona).
- No sirve para saltarse RLS: si necesitas cruzar organizaciones, usa `SystemTx` con su comentario de justificación.
- El autor de la plantilla queda como `created_by` de las solicitudes y destinatario de los avisos; la auditoría distingue lo
  automático (`REQUEST_PUBLISHED_BY_TEMPLATE`, `automatica=si`).

## Consecuencias
- Una persona desactivada sigue figurando como autora de lo que su plantilla publique. Si hace falta, se archiva la plantilla.
- Un job nuevo «en nombre de» reutiliza `SyntheticTenant`; el test de referencia es `TemplatesFlowTest` (bajo RLS: la
  solicitud nace en la organización dueña y a nombre del autor, y otra organización no ve nada).
- Los feriados nacionales de la programación son una lista fija (`PeruHolidays`) hasta que el roadmap 2.14 los vuelva administrables.
