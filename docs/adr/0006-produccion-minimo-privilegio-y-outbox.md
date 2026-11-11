# ADR-0006 — Producción: BD con mínimo privilegio, outbox transaccional y almacenamiento S3

**Estado:** aceptado · 2026-11-10 · Resuelve los riesgos abiertos de ADR-0001 (conexión como dueño), ADR-0004
(avisos que se pierden entre commit y observer) y ADR-0005 (disco local).

## 1. Dos usuarios de base de datos
**Contexto.** La app se conectaba como dueño del esquema y bajaba a `logyx_app` con `SET LOCAL ROLE`: una inyección
SQL podía hacer `RESET ROLE` y leer o escribir todo, y las transacciones sin JWT corrían sin RLS de forma implícita.

**Decisión.**
- Roles (`infra/db/init-roles.sh`; en dev/test `db/local-roles/R__local_roles.sql`):
  `logyx_owner` (LOGIN, dueño de BD y esquema, sin SUPERUSER ni BYPASSRLS), `logyx_app` (NOLOGIN, sujeto a RLS) y
  `logyx_api` (LOGIN, `INHERIT`, miembro **solo** de `logyx_app`).
- Dos datasources sobre la misma BD: el **por defecto** = `logyx_owner` (Flyway y `SystemTx`; pool chico) y **`app`** =
  `logyx_api` (todo lo demás).
- Hibernate usa la multitenencia `DATABASE` **solo como enrutador** (`shared/tenancy/DataSourceRouting`): dos "tenants"
  fijos, `app` (por omisión) y `system`. `TenantResolver` devuelve `system` solo mientras el hilo está dentro de
  `SystemTx.call(...)` (ThreadLocal) y `TenantConnectionResolver` mapea cada uno a su `AgroalDataSource`. Las sesiones de
  Hibernate son por transacción, así que la transacción nueva de `SystemTx` abre su propia sesión y conexión privilegiada
  sin tocar la del usuario. Las mismas entidades y repositorios Panache sirven en ambos casos.
- **Fail-closed:** una transacción sin JWT y fuera de `SystemTx` corre como `logyx_api` sin `app.org_id` → las políticas no
  devuelven filas. Por eso login, registro, alta de operador, jobs (`CloseExpiredAuctionsUseCase`,
  `SmartLoadService.generateForAllCarriers`, `TripMaintenanceUseCase`, outbox) y el seed dev entran **explícitamente** por
  `SystemTx` (cada uso comentado). `SystemTx` activa el request context si falta (jobs/arranque) y, si ya está dentro de
  una transacción de sistema activa, se une a ella.
- `TenantSessionBinder` sigue haciendo `set_config` + `SET LOCAL ROLE logyx_app` (ya no da ni quita privilegios: fija el
  rol explícitamente). `RESET ROLE` vuelve a `logyx_api`, que no puede `SET ROLE` a otro rol, no es dueño de nada y no
  tiene BYPASSRLS. `DatabasePrivilegesTest` lo prueba con sentencias "inyectadas" (SQLSTATE 42501).

**Alternativas descartadas.**
- *Segunda persistence unit* para `SystemTx`: en Quarkus una entidad pertenece a una sola PU, habría que duplicar
  entidades/repositorios o separar paquetes. Descartada.
- *JDBC directo* en lo que corre en `SystemTx`: reescribir ~15 usos que hoy reutilizan repositorios y políticas de dominio.
  Descartada por costo y duplicación; queda para consultas puntuales.
- *Funciones `SECURITY DEFINER`*: mueven lógica de negocio a SQL. Descartada.

**Trade-offs.** (−) La multitenencia de Hibernate impide la generación de esquema de Hibernate (no se usa: Flyway) y exige
request context para resolver el datasource (lo garantiza `SystemTx`). (−) Dos pools: más conexiones (8 + 20 por instancia).
(−) Residual: con inyección SQL en una transacción de usuario, `set_config('app.org_id', …)` permitiría suplantar a otra
organización dentro de lo que `logyx_app` puede hacer (las políticas confían en el GUC). Mitigación: consultas
parametrizadas (JPA) y revisión; a futuro firmar el contexto (HMAC en el GUC verificado por las políticas).

## 2. Outbox transaccional
**Decisión.** Los eventos de dominio que generan avisos implementan `shared.outbox.OutboxEvent`.
`OutboxRecorder` (`@Observes(during = IN_PROGRESS) @Default`) los serializa a JSON en `outbox_events` (V11) dentro de la
transacción de negocio. `OutboxDispatcher` toma `PENDING` vencidos con `FOR UPDATE SKIP LOCKED`, los re-dispara como
`@Outboxed` y los marca `DONE` en la **misma** transacción de sistema donde los consumidores (`NotificationObservers`,
`ReputationRecalculator`) escriben: o se crean las notificaciones y se marca el evento, o nada. Si falla, otra transacción
suma el intento con espera exponencial (`logyx.outbox.retry-base` × 2^n, tope 1 h); a los `logyx.outbox.max-attempts`
(10) queda `FAILED`. Camino rápido: tras el commit se despacha en el mismo hilo (misma latencia que antes); el job
`logyx.outbox.every` (5 s) cubre fallos y caídas.
- RLS: `logyx_app` solo puede **insertar** con `actor_org_id = app_org()`; no lee, modifica ni borra el outbox.
- Tiempo real (fase siguiente): un emisor SSE/WebSocket será otro observador `@Outboxed` (o leerá `outbox_events` por
  `seq` > Last-Event-ID); los `DONE` se conservan `logyx.outbox.retention` (7 días).
- Consecuencias: entrega **al menos una vez** a los consumidores (idempotentes o atómicos con el despacho); cambiar campos
  de un evento de forma incompatible rompe los pendientes (agregar campos, no renombrar).

## 3. Almacenamiento S3/MinIO
`logyx.documents.storage=local|s3` (`DocumentStorageProducer`). `S3DocumentStorage` usa AWS SDK v2 con el cliente HTTP de
`java.net` (sin Netty/Apache), `path-style` para MinIO, prefijo y SSE-S3 opcionales, creación del bucket opcional. La
descarga sigue pasando por la API (RLS); no hay URLs públicas ni firmadas todavía. Test con MinIO real
(Testcontainers, imagen `chainguard/minio`: MinIO dejó de publicar `minio/minio` en Docker Hub).
