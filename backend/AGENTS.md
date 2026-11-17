# Backend LOGYX — guía para agentes

Quarkus 3.40 · Java 21 · PostgreSQL 16 (Flyway) · JWT RSA · Hibernate ORM Panache.
Contexto general: `../AGENTS.md`, `../docs/architecture/overview.md`, ADR-0001 (RLS), ADR-0002 (hexagonal),
ADR-0004 (eventos de dominio, notificaciones, escrituras del operador), ADR-0005 (almacenamiento de documentos) y
ADR-0006 (dos usuarios de BD, outbox transaccional, S3). Despliegue: `../infra/README.md`; seguridad: `../docs/security.md`.

## Correr y testear
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk      # el JDK por defecto de la máquina es 27
./mvnw quarkus:dev                                 # API :8080, Swagger en /q/swagger-ui, Postgres por Dev Services (Docker)
./mvnw quarkus:dev -Dquarkus.http.port=8580        # otro puerto si 8080 está ocupado
./mvnw test                                        # unit (dominio) + @QuarkusTest (flujo HTTP y RLS contra Postgres real)
./mvnw package -DskipTests                         # regenera ../docs/api/openapi.yaml (contrato)
```
Requiere Docker en marcha (Dev Services levanta `postgres:16-alpine` en dev y test; `S3DocumentStorageTest` levanta
`chainguard/minio` con Testcontainers). Imagen: `docker build -t logyx-backend backend/` (multi-stage, usuario no root).
En dev `%dev.quarkus.http.host-validation.require-localhost=false`: el emulador Android llama con
`Host: 10.0.2.2:<puerto>` y, sin eso, Quarkus dev responde 400 vacío.

## Forma de un módulo
```
pe/logyx/<modulo>/
├── domain/                 Java puro: model/ (entidades + value objects) y <Regla>Policy
├── application/            casos de uso (@Transactional aquí), commands, read models
│   └── port/out/           interfaces que implementa infrastructure (repositorios, gateways)
└── infrastructure/
    ├── persistence/        @Entity Panache (*Entity) + Panache*Repository (implementan port/out)
    ├── web/                Recurso JAX-RS + DTOs (records) request/response
    └── scheduling/ | integration/ | security/ | distance/   otros adaptadores
```
Dependencias: `infrastructure → application → domain`. `ModuleBoundariesTest` lo verifica:
- `domain/` no importa `io.quarkus`, `jakarta.*`, Jackson, MicroProfile, Hibernate ni SmallRye.
- Nadie importa la `infrastructure` de **otro** módulo; `domain`/`application` no importan ninguna `infrastructure`.
- Un módulo usa a otro **por su `application`** (p. ej. `marketplace` → `fleet.application.FleetService.ownVehicle`).
  Puede importar tipos de valor del `domain` ajeno que esa `application` devuelve (`Vehicle`, `Organization`).
- Vocabulario compartido importable desde cualquier dominio: `catalog.domain.City`, `catalog.domain.CargoType`,
  `shared.tenancy.OrgType`, `shared.domain.BusinessException`.
- Si dos `application` se necesitarían mutuamente, una lo hace por un puerto: `shipment` lee datos de la
  solicitud vía `port/out/RequestInfoProvider`, implementado en `shipment/infrastructure/integration`
  llamando a `marketplace.application.MarketplaceQueries` (marketplace ya depende de shipment para crear el envío).

Pragmatismo: no hay `port/in`; los casos de uso son clases concretas (`PlaceBidUseCase`) o un `<Modulo>Service`
cuando son varios pequeños (`FleetService`, `AuthService`). Las consultas van en `<Modulo>Queries`.

### Ejemplo real: ofertar (marketplace)
| Capa | Archivo | Qué hace |
|---|---|---|
| web | `infrastructure/web/MarketplaceResource#bid` + `MarketplaceDtos.PlaceBid` | valida JSON (`@Valid`), arma `PlaceBidCommand`, mapea `BidSummary → BidView` |
| application | `application/PlaceBidUseCase` | `@Transactional`; carga solicitud y subasta (lock), consulta fleet, aplica políticas, guarda, cuenta ofertas con `SystemTx` |
| domain | `domain/BidEligibilityPolicy` (RN02/RN03), `domain/AuctionClosingPolicy` (RN04), `domain/model/{ShipmentRequest,Auction,Bid}` | reglas puras, lanzan `BusinessException` con su código |
| port | `application/port/out/{AuctionRepository,BidRepository,...}` | contrato de persistencia |
| persistence | `infrastructure/persistence/{BidEntity,PanacheBidRepository}` | JPA ↔ dominio (`toDomain()` / `apply()`) |

Persistencia: los ids (UUID) los genera el dominio; `save()` inserta o actualiza copiando el modelo sobre la
entidad gestionada. **Después de mutar un modelo de dominio hay que llamar a `save()`**: no hay dirty-checking
sobre los objetos de dominio.

### Módulos
| Módulo | Qué hace | Tablas (migración) |
|---|---|---|
| identity, fleet, catalog, pricing, marketplace, shipment, dashboard | fase 1 | V1–V3 |
| `negotiation` | chat por (solicitud, transportista) con RN07 y contra-ofertas de la PYME | `messages`, `counter_offers` (V4) |
| `documents` | upload/descarga de documentos y evidencia de entrega; puerto `DocumentStorage` | `documents` (V5) |
| `reputation` | calificaciones post-entrega (RN06) y Trust Score bayesiano (RN09) | `reviews`, `reputation_scores` (V6) |
| `incident` | incidencias, comentarios y estados (RN11) | `incidents`, `incident_comments` (V7) |
| `notification` | centro de notificaciones alimentado por eventos | `notifications` (V8) |
| `operator` | alertas SLA, métricas, candidatos y asignación manual (RN10); sin tablas propias | políticas de escritura (V9) |
| `trip` | viajes publicados (RN12), reservas de espacio, cargas de retorno (RF61–RF63) y Smart Load Planner (OE07) | `carrier_trips`, `trip_bookings`, `smart_load_suggestions`, `smart_load_items` (V10) |
| `shared/outbox` | outbox transaccional de eventos (ADR-0006) | `outbox_events` (V11) |
| `realtime` | stream SSE `GET /api/v1/events/stream` alimentado por el outbox (ADR-0007) | `realtime_events` (V12) |
| `routing` | rutas y km por carretera entre ciudades del catálogo, orden óptimo de paradas, desvíos, `GET /api/v1/routes` (ADR-0008) | sin tablas: dataset `resources/routing/routes.json` |
| (varios) | huecos de flujo 2026-11-16: verificación de organizaciones, retiro de ofertas, cancelación/reasignación de envíos, estado final RF55, grupos de carga | columnas y políticas en V13 (sin tablas nuevas) |

Dependencias entre módulos nuevos (todas por `application`): `negotiation → marketplace`; `documents → shipment`;
`reputation → shipment, documents`; `incident → shipment`; `marketplace → reputation` (Trust Score en ofertas);
`operator → marketplace, shipment, incident, fleet, reputation`; `trip → fleet, marketplace, shipment, identity, reputation, catalog`;
`dashboard → trip`; `pricing → routing` y `trip → routing` (km por carretera, `RoutePlanner`; trip los pasa a su dominio como `RoadDistances`); `notification` observa eventos de `trip`. `marketplace` y `shipment` no dependen de `trip`. `shipment` no depende de ellos: usa los puertos
`IncidentHoldProvider` (RN11) y `DeliveryEvidenceProvider` (foto de entrega), implementados en
`shipment/infrastructure/integration` (igual que `RequestInfoProvider`).

### Eventos de dominio (ADR-0004)
Records en `<modulo>/application/event/` que implementan `shared.outbox.OutboxEvent`, publicados con `@Inject Event<X>` +
`fire(...)` dentro de la transacción. **Outbox (ADR-0006):** `OutboxRecorder` los guarda en `outbox_events` (V11) en esa misma
transacción; `OutboxDispatcher` los entrega tras el commit (y por job cada `logyx.outbox.every`, con reintentos exponenciales,
`FOR UPDATE SKIP LOCKED` y `FAILED` tras 10 intentos). Consumidores con `@Observes @Outboxed X e`: corren dentro de la
transacción de sistema del despacho; si lanzan, todo se revierte y el evento se reintenta (deben ser idempotentes o escribir
solo en esa transacción). No uses `AFTER_SUCCESS` para efectos que no deben perderse. Tests: `outbox/OutboxTest`.
| Evento | Publica | Consumen |
|---|---|---|
| `BidPlaced`, `BidAccepted` | `PlaceBidUseCase`, `AcceptBidUseCase.award` (también asignación manual) | notification |
| `MessagePosted`, `CounterOfferUpdated` | `NegotiationService` | notification |
| `ShipmentStatusChanged` | `TrackStopsUseCase` (IN_TRANSIT, DELIVERED) | notification |
| `IncidentReported`, `IncidentStatusChanged` | `IncidentService` | notification |
| `ReviewSubmitted` | `ReviewService` | notification, `ReputationRecalculator` |
| `TripBookingRequested`, `TripBookingCancelled` | `BookingService.book` / `cancelByRequester` | notification (al transportista) |
| `TripBookingConfirmed`, `TripBookingRejected` | `BookingService.confirm/reject`, `BookingSweeper` (viaje cerrado/lleno/salió) | notification (a la PYME) |
| `TripCancelled` | `TripService.cancel` | notification (a cada PYME con reserva activa) |
| `SmartLoadSuggested` | `SmartLoadService.generate*` (a demanda y job) | notification (al transportista) |
| `SmartLoadAccepted` | `SmartLoadService.accept` | notification (a cada PYME agrupada, RF33) |
| `ShipmentStopUpdated`, `DriverAssigned` | `TrackStopsUseCase.arrive/complete`, `AssignDriverUseCase` | realtime (solo) |
| `ShipmentStatusChanged` (CANCELLED / DELIVERED por el operador, con `actorOrgId`/`reason`/`requestId`) | `ShipmentLifecycleUseCase` | notification, realtime, `marketplace.RequestLifecycleObserver` (solicitud → CANCELLED), `trip.BookingShipmentObserver` (reserva → CANCELLED + capacidad) |
| `ShipmentReassigned` | `AssignDriverUseCase` (cambio), `ShipmentLifecycleUseCase.changeVehicle` | notification (a la PYME) |
| `OrganizationVerificationChanged` | `OrganizationVerificationService` | notification |
| `AuctionClosed`, `RequestCancelled`, `BidWithdrawn` | `CloseExpiredAuctionsUseCase`, `CancelRequestUseCase`, `PlaceBidUseCase.withdraw` | notification; `BidWithdrawn` y `BidAccepted` también `negotiation.CounterOfferExpiry` (F40) |
| (todos los anteriores) + `NotificationsPublished` (síncrono, no outbox) | `NotificationPublisher` | realtime (`RealtimeProjector`) |
Para un aviso nuevo: agrega el record (`implements OutboxEvent`) al módulo que publica, dispara el evento y un método
`@Observes @Outboxed` en `NotificationObservers` (los textos van ahí, en español). El evento debe traer los datos que el aviso
necesita y es un formato persistido: agrega campos, no los renombres. `ReputationRecalculator` también consume por outbox.

### Tiempo real (ADR-0007)
`realtime/`: `RealtimeProjector` (`@Observes @Outboxed`, en la transacción del despacho) escribe en `realtime_events` (V12)
**una fila por destinatario** (`domain/model/Recipient`: organización, usuario-conductor o difusión por tipo) con payload
mínimo (ids/estados/contadores; nunca montos, nombres, rutas ni textos). Trigger → `pg_notify('logyx_realtime', seq)` al
confirmar; `infrastructure/messaging/PgRealtimeListener` (un hilo y una conexión del datasource `app` por instancia) pasa
los seq a `RealtimeHub`, que lee las filas (SystemTx) y entrega a sus conexiones SSE que cumplen
`domain/RealtimeAudiencePolicy` (misma regla que la política RLS `realtime_events_read`). Reanudación
(`Last-Event-ID`/`?lastEventId=`) en `RealtimeStreamService.replay`, `@Transactional` bajo RLS + filtro explícito.
Config `logyx.realtime.*` (latido 20 s, 5 conexiones por usuario → `LOGYX-RTM-001`, replay 500 → `stream.resync`,
retención 24 h). Evento nuevo: tipo en `RealtimeEventTypes`, método en el proyector (payload mínimo + destinatarios), fila
en `docs/api/CHANGELOG.md` y test de aislamiento en `RealtimeStreamFlowTest` (cliente SSE de tests: `support/SseClient`).

### Almacenamiento de archivos (ADR-0005, ADR-0006)
`documents/application/port/out/DocumentStorage`; `infrastructure/storage/DocumentStorageProducer` elige por
`logyx.documents.storage` (env `LOGYX_DOCUMENTS_STORAGE`): `local` → `LocalFileSystemDocumentStorage`
(`logyx.documents.storage-dir`, por omisión `~/.logyx/documents`, env `LOGYX_DOCUMENTS_DIR`; en tests un temp dir) o
`s3` → `S3DocumentStorage` (AWS SDK v2 + cliente `java.net`; `logyx.documents.s3.*` / env `LOGYX_S3_*`, ver `infra/README.md`).
Claves generadas por el servidor; formato por firma binaria (`documents/domain/FileFormat`: jpg/png/pdf);
`logyx.documents.max-size-bytes` = 10 MB (200 KB en test).

### Mapa de reglas
`PriceFloorPolicy` (RN03, pricing) · `TariffCalculator` (motor de costos, pricing) · `BidEligibilityPolicy`
(RN02/RN03, marketplace) · `AuctionClosingPolicy` (RN04, marketplace) · `Organization.ensureCanTransact`
(RN01) · `StopSequencePolicy` (RN08, shipment) · `RucValidator` (identity)
· `ThreadAccessPolicy` (chat) · `ReviewEligibilityPolicy` (RN06) y `TrustScoreCalculator` (RN09, reputation)
· `ManualAssignmentPolicy` (RN10, marketplace) · `VehicleCargoCompatibility` (RN02 por tipo de carga, fleet)
· `ResourceSchedulePolicy` (F11, shipment) · `IncidentOutcomePolicy` (RF55, incident) · `Organization.ensureCanTransact`
+ `OrganizationGuard` (RN01, identity) · `ContactInfoPolicy` (RN07, ahora en `shared.domain`) · `IncidentStatusPolicy` (RN11, incident) · `DeliveryEvidencePolicy`
(shipment) · `DocumentUploadPolicy` (documents) · `TripCapacityPolicy` (RN12), `TripPricingPolicy`, `TripStatusPolicy`,
`ReturnLoadPolicy`, `SmartLoadPlanner`, `CityProximityPolicy`, `VehicleCargoRules` (trip) · `StopOrderOptimizer`, `RouteCalculator`
(routing: orden de paradas y desvíos). Ver `../docs/domain/business-rules.md`.

### Módulo `routing` (ADR-0008)
- **Datos:** `src/main/resources/routing/routes.json`, generado con `python3 -I ../scripts/routing/build_routes.py` (OSRM `driving`,
  ruta más rápida; matriz **dirigida** de km/min + polylines con Douglas-Peucker a 60 m, precisión 5; atribución ODbL de OSM).
  Al agregar o mover una ciudad de `City`, regenera el JSON: `ClasspathRouteDatasetTest` falla si no coinciden.
  Sin dato para un par → `StraightLineEstimate` (línea recta × 1.35) con `estimated = true`.
- **Uso desde otros módulos:** `routing.application.RoutePlanner` (`distanceKm`, `leg`, `route`, `shortestTour`, `planLoads`,
  `detour`, `detourKm`, `geometry`, `attribution`). Puerto `application/port/out/RouteDataset`
  (hoy `infrastructure/dataset/ClasspathRouteDataset`; un OSRM propio para direcciones arbitrarias sería otro adaptador).
- **Algoritmos (dominio puro):** `StopOrderOptimizer` (Held-Karp exacto ≤ 13 nodos con partida y llegada fijas y precedencias
  recojo → entrega; más nodos: vecino más cercano + reubicación/2-opt), `RouteCalculator` (recorridos, orden de recojos/entregas,
  desvío por mejor inserción con partida y llegada fijas).
- **API:** `RoutingResource` `GET /api/v1/routes?stops=A,B,C[&optimize=true]` (cualquier rol autenticado; sin datos de organización).

### Módulo `trip` (fase 4)
- **Viajes** (`CarrierTrip`, `TripService`): el transportista publica ciudad origen/destino del corredor, salida futura, vehículo
  propio, `capacityKg` (RN12 ≤ capacidad del vehículo), `pricingMode` (`TripPricingMode`: `PER_KG` | `PER_TRIP`) y `rate`.
  Estados `TripStatus` (`TripStatusPolicy`): OPEN → FILLING (1.ª reserva **confirmada**) → CLOSED (lleno, cierre manual o ya salió)
  → COMPLETED (+24 h); CANCELLED. `confirmed_weight_kg` está denormalizado en `carrier_trips` para que la PYME vea la capacidad
  restante sin leer reservas ajenas.
- **Precio de reserva** (`TripPricingPolicy`): `PER_KG` → `tarifa × kg`; `PER_TRIP` → `tarifa × kg / capacidadPublicada`;
  2 decimales HALF_UP, mínimo S/ 1.00.
- **Reservas** (`TripBooking`, `BookingService`): PYME reserva (PENDING; no descuenta), transportista confirma (valida RN12 de nuevo,
  descuenta, genera envío) o rechaza; la PYME cancela. Al cerrarse/llenarse/salir el viaje, `BookingSweeper` rechaza las pendientes.
- **Qué pasa en shipment al confirmar:** `shipments.request_id`/`bid_id` son obligatorios, así que `marketplace/application/
  DirectContractUseCase` crea una solicitud `MATCHED` (a nombre de la PYME), su subasta cerrada (`ACCEPTED`) y una oferta `ACCEPTED`
  por el precio de la reserva, y llama a `CreateShipmentUseCase.createFromAcceptedBid` (paradas recojo/entrega con las direcciones de
  la reserva). Todo en un `SystemTx` (ver abajo). `trip_bookings.request_id`/`shipment_id` quedan `NULL` hasta confirmar.
  Cancelar una reserva confirmada cancela el envío con `shipment/application/CancelShipmentUseCase` (solo `PENDING`, si no
  `LOGYX-SHP-003`); la solicitud MATCHED queda como histórico (no se reabre).
- **Retornos** (`ReturnLoadService` + `ReturnLoadPolicy`): sin tablas; cruza los envíos IN_TRANSIT/DELIVERED (≤ 3 días) del transportista
  con `MarketplaceQueries.openRequests`. Proximidad en `CityProximityPolicy` con km **por carretera** (`RoadDistances` =
  `routing.application.RoutePlanner::distanceKm`, ADR-0008; Juliaca–Puno = 43 km). Radios: retorno 50 km, Smart origen 50 km, destino 80 km.
- **Smart Load** (`SmartLoadPlanner` puro + `SmartLoadService`): ver `docs/api/CHANGELOG.md`. A demanda corre bajo RLS del
  transportista; el job `TripScheduler` (`logyx.smartload.every`, 15 m; `%test` = `off`) corre sin usuario y recorre todos los
  transportistas (`FleetService.availableVehicles`, `MarketplaceQueries.openRequestsForPlanning`). Compatibilidad y orden de la ruta con km por
  carretera: primero los recojos (en orden de fecha) y luego las entregas, en la permutación de menos km (`Plan.routeKm`). **Aceptar oferta en lote**
  (F12): una oferta por carga con el vehículo del plan vía `PlaceBidUseCase`, marcadas con `loadGroupId`; no adjudica (cada PYME
  decide) y avisa a las PYMEs (RF33). Se persiste en `smart_load_suggestions` +
  `smart_load_items` (en vez de `uuid[]` + `jsonb` del modelo: la ruta se deriva de los ítems). `logyx.smartload.ttl` = PT4H.
- Mantenimiento (`TripMaintenanceUseCase`, `logyx.trip.maintenance.every`, 5 m): cierra viajes que ya salieron, completa los de hace
  > 24 h y vence sugerencias.
- Pendientes: notificar cargas de retorno por evento al entregar (hoy solo endpoint + dashboard), `volume_m3`, paradas intermedias del viaje,
  reabrir la solicitud MATCHED al cancelar la reserva, `Vehicle` sin estado "verificado".

## Agregar un módulo nuevo (checklist)
1. **Dominio** `pe/logyx/<m>/domain/model/*` (clases con comportamiento, ids UUID) y `<Regla>Policy` por cada RN.
   Errores con `BusinessException` y código nuevo `LOGYX-<XXX>-NNN`.
2. **Puerto** `application/port/out/<X>Repository` (y gateways externos).
3. **Caso de uso** `application/<Accion>UseCase` o `<M>Service`, con `@Transactional`, `TenantContext` para la
   org actual (nunca desde el cliente), `AuditService.record(...)` en acciones críticas. Commands/read models como records.
4. **Adaptador de persistencia** `infrastructure/persistence/<X>Entity` (sin `@GeneratedValue`; mapea a dominio)
   + `Panache<X>Repository implements <X>Repository`, `@ApplicationScoped`.
5. **Recurso** `infrastructure/web/<M>Resource` + `<M>Dtos` (records con Bean Validation). Respuesta
   `ApiResponse.of(...)`; `@RolesAllowed(Roles.X)`. Los **nombres simples** de los DTOs son los schemas del
   OpenAPI: elige nombres únicos y estables.
6. **Migración** `src/main/resources/db/migration/V<n>__<m>.sql` (V1/V2 ya están publicadas: **no se editan**).
   Toda tabla con datos de una organización lleva su `organization_id`/`*_org_id` (denormaliza si hace falta),
   `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` y políticas `TO logyx_app` usando `app_org()` / `app_org_type()`
   (copia el estilo de `V2__row_level_security.sql`). Los GRANT a `logyx_app` ya aplican por default privileges.
7. **Tests**: unitarios de cada policy (sin `@QuarkusTest`), flujo HTTP en `src/test/java/pe/logyx/flow`
   (helpers en `support/Api`), y un caso en `RowLevelSecurityTest` que demuestre que otra org no ve los datos.
8. **Regenerar OpenAPI** `./mvnw package -DskipTests` y revisar `git diff ../docs/api/openapi.yaml`.
9. **STATUS**: actualiza la fila del módulo en `../docs/STATUS.md` y `../docs/domain/business-rules.md`.

## RLS, dos datasources y SystemTx (ADR-0001, ADR-0006) — no romper
- **Dos datasources sobre la misma BD.** Por defecto = dueño del esquema (`logyx_owner` en prod; `quarkus` en dev/test):
  Flyway y `SystemTx`. `app` = `logyx_api` (LOGIN, miembro solo de `logyx_app`): todo lo demás. Hibernate elige con
  multitenencia `DATABASE` usada como enrutador (`shared/tenancy/DataSourceRouting`): tenant `system` solo dentro de
  `SystemTx`. En dev/test `db/local-roles/R__local_roles.sql` crea `logyx_api` (contraseña `logyx_api`) y el datasource
  `app` apunta a la URL de Dev Services. En prod los roles los crea `infra/db/init-roles.sh`.
- `shared/tenancy/TenantSessionBinder` observa `@Initialized(TransactionScoped.class)`: en cada transacción con
  usuario autenticado hace `set_config('app.org_id'|'app.org_type', …, true)` y `SET LOCAL ROLE logyx_app`.
  **Todo acceso a datos de usuario va dentro de `@Transactional`** (en `application`).
- **Sin JWT y fuera de `SystemTx` no se ve nada** (fail-closed: `logyx_api` sin organización). Todo lo que corre sin
  usuario (login, registro, alta de operador, jobs, seed, outbox) entra explícitamente por `SystemTx`.
- `SystemTx.call(...)` abre una transacción nueva en el datasource privilegiado (sin RLS); si ya hay una de sistema activa en
  el hilo, se une a ella; activa el request context si falta. Solo para operaciones de plataforma que cruzan
  organizaciones; cada uso lleva comentario. En tests, las consultas "como dueño" van con `systemTx.run/call`
  (no con `QuarkusTransaction`, que ahora es `logyx_api`). Usos actuales:
  - `AuthService.register` / `provisionOperator` / `login`: sin usuario aún; unicidad de RUC/email y búsqueda por email.
  - Jobs: `CloseExpiredAuctionsUseCase.closeExpired`, `SmartLoadService.generateForAllCarriers`, `TripMaintenanceUseCase.run`,
    `OutboxDispatcher` (despacho y limpieza). `DemoDataSeeder` (arranque dev).
  - `FleetService.addVehicle`: unicidad global de placa.
  - `UserAccounts.createDriverAccount`: unicidad global de email.
  - `FleetService.plateForContract`: la PYME que acepta copia la placa (flota privada del transportista).
  - `PlaceBidUseCase`: conteo total de ofertas para RN04 (bajo RLS un transportista solo ve la suya).
  - `RequestSummaries.bidCounts`: conteo agregado de ofertas para transportistas (sin montos ni competidores).
  - `NotificationPublisher.publish`: la notificación es para otra organización; `notifications` no tiene política
    de INSERT para `logyx_app` (ADR-0004). Se une a la transacción del despacho del outbox.
  - `ReputationRecalculator.recalculate`: quien califica actualiza la reputación de otra organización;
    `reputation_scores` es de solo lectura para `logyx_app`.
  - `NotificationObservers.operators`: ids de las organizaciones operadoras (dentro del despacho del outbox).
  - `RealtimeHub.onCommitted/catchUp` y `RealtimeMaintenance` (realtime): el listener LISTEN/NOTIFY no tiene usuario; lee
    filas ya dirigidas y filtra en memoria por destinatario. El proyector escribe dentro del despacho del outbox.
  - `DirectContractUseCase.create` (marketplace): al confirmar una reserva de viaje, el transportista debe escribir la solicitud
    (de la PYME, `requests_owner`) y la oferta (suya) a la vez; ninguna transacción de usuario puede con ambas bajo RLS.
  - `shipment/application/ResourceScheduler` (F11): tras tomar un bloqueo consultivo por vehículo/conductor en la transacción
    del usuario, lee los envíos activos ya confirmados de ese recurso (la PYME que adjudica no ve los otros envíos del
    transportista). Solo ids, fechas y grupo de carga.
  - `DemoDataSeeder.verifyExistingDemoOrganizations` (dev): marca verificadas las cuentas demo de una BD previa a V13.
  - Consumidores `@Outboxed` nuevos (corren en la transacción de sistema del despacho): `RequestLifecycleObserver`,
    `BookingShipmentObserver`, `CounterOfferExpiry`.
  - `BookingService.cancelByRequester`: la PYME devuelve la capacidad en `carrier_trips` (solo lectura para ella); se bloquea
    la fila y se resta el peso de esa reserva.
- Listados globales del operador (`GET /api/v1/operator/requests|shipments`, `OperatorService` → `MarketplaceQueries.allRequests` / `ShipmentQueries.allShipments`, paginados y filtrados por estado/ciudad/fechas): leen con las políticas `*_operator`, sin SystemTx. Viven en `OperatorResource` (un `@Path` más corto en otra clase daría 404: RESTEasy elige la clase por el prefijo más largo).
- El operador **no** usa `SystemTx`: lee con las políticas `*_operator` (V2, V4–V7) y la asignación manual escribe
  con las políticas de V9 (`bids_operator_insert` exige `assigned_by_operator`, etc.). V13 le suma UPDATE de
  `organizations` (verificación; un trigger impide que otra organización toque esas columnas), `shipments`,
  `shipment_stops`, `vehicles` y `drivers` (cancelar o dar por entregado al resolver una incidencia, RF55).
- RLS por tabla nueva: `messages`/`counter_offers`/`documents`/`incidents`/`incident_comments` → las dos partes
  (`requester_org_id`, `carrier_org_id` denormalizados) + lectura del operador; `reviews`/`reputation_scores` →
  lectura pública (RF44); `notifications` → solo la propia organización. Historial, evidencia y calificaciones no
  admiten UPDATE/DELETE de usuarios. Casos en `RowLevelSecurityTest`.
- `trip`: `carrier_trips` → dueño (solo `CARRIER`), PYMEs leen los OPEN/FILLING y los que reservaron, operador lee; `trip_bookings` →
  las dos partes leen/actualizan, solo la PYME (`PYME`) inserta, operador lee, sin DELETE; `smart_load_*` → solo el transportista
  dueño (+ lectura del operador, RF59).
- La transacción de `SystemTx` no ve lo no confirmado de la transacción externa de usuario (ver comentario en `PlaceBidUseCase`).
- `realtime_events` (V12): `logyx_app` solo SELECT de lo dirigido a su organización o a su tipo (sin INSERT/UPDATE/DELETE).
- `outbox_events` (V11): `logyx_app` solo INSERT con `actor_org_id = app_org()`. `DatabasePrivilegesTest` prueba que
  `logyx_api` no puede `RESET ROLE`/`SET ROLE` a un rol privilegiado, alterar tablas/políticas ni leer sin organización.

## Seguridad HTTP (docs/security.md)
- Rate limiting de login/registro en `shared/security/AuthRateLimits` (en memoria, por instancia): `logyx.rate-limit.*`;
  en dev/test el límite por IP es enorme (tests y e2e registran cientos de cuentas desde 127.0.0.1), el bloqueo por email
  (10 fallos / 15 min) sí aplica. 429 `LOGYX-AUTH-429` con `Retry-After`. La IP sale de `X-Forwarded-For` solo desde
  `TRUSTED_PROXIES` (`%prod.quarkus.http.proxy.*`).
- Cabeceras `nosniff`, `X-Frame-Options`, `Referrer-Policy`, `Cache-Control: no-store` en `/api/*`; HSTS en `%prod`.
  CORS desactivado en prod (`CORS_ENABLED`), Swagger UI fuera del build prod. No loguees emails, RUC, teléfonos ni tokens.

## Códigos de error
Formato `LOGYX-<ÁREA>-<NNN>`, respuesta `{ error: { code, message, field }, meta }` (`shared/api/ExceptionMappers`).
| Área | Códigos |
|---|---|
| GEN | `404` no encontrado, `403` regla de permiso de negocio, `500` |
| AUTH | `001` credenciales, `002` email ya registrado, `401` sin token, `403` rol sin permiso, `429` demasiados intentos (login/registro) |
| VAL | `001` Bean Validation (`field` = propiedad) |
| ORG | `001` RUC inválido, `002` RUC duplicado, `003` (403) sin verificar por el operador (RN01), `004` (403) suspendida, `005` operadora no se verifica, `006` falta motivo (`reason`) |
| FLT | `001` placa duplicada, `002` estado "en ruta" lo controla el envío |
| PRC | `001` ciudad fuera del corredor, `002` origen = destino |
| REQ | `002` cancelar solo publicadas, `003` fecha de recojo pasada (`pickupDate`), `004` solo se republica una cancelada |
| AUC | `001` subasta no abierta, `002` subasta cerrada, `003` solo se reabre una cerrada por tiempo |
| BID | `001` bajo el piso (RN03), `003` ya ofertó (o retiró), `004` no admite aceptar, `005` no se puede retirar |
| CAP | `001` sin capacidad (RN02), `002` vehículo no disponible, `003` tipo de vehículo no apto para la carga, `004` volumen, `005` vehículo comprometido en otro envío solapado (F11) |
| SHP | `001` envío cerrado: no se reasigna (antes: "solo antes del recojo"), `002` (404) el conductor no tiene envío activo, `004` conductor con otro envío solapado (`driverId`), `006` falta motivo (`reason`), `007` mismo vehículo |
| TRK | `001` orden de paradas (RN08), `002` ya llegó, `003` falta llegada, `004` envío cerrado, `005` incidencia activa (RN11), `006` entrega sin foto |
| NEG | `001` datos de contacto (RN07, `body`/`message`), `002` hilo cerrado, `003` mismo monto, `004` contra-oferta pendiente, `005` oferta no negociable, `007` ya respondida |
| DOC | `001` tipo no permitido, `002` muy grande, `003` vacío (`file`), `004` falta parada de entrega, `005` sin llegada (`stopId`), `006` envío cancelado, `007` (403) evidencia solo del conductor asignado, `008` evidencia cerrada (parada completada o envío entregado), `500` fallo de almacenamiento |
| REV | `001` no entregado, `002` sin foto (RN06), `003` ya calificó |
| INC | `001` ya hay activa, `002` transición inválida (`status`), `003` faltan notas (`resolutionNotes`), `004` cerrada, `005` envío cancelado, `006` estado final del envío faltante o incoherente (`shipmentOutcome`, RF55) |
| OPS | `001` SLA no vencido (RN10), `002` no es transportista (`carrierOrgId`), `003` transportista no verificado o suspendido |
| RTM | `001` (429) más de `logyx.realtime.max-connections-per-user` streams abiertos del usuario |
| RTE | `001` ruta con menos de 2 paradas (`stops`), `002` más de 25 paradas (`stops`) |
| TRP | `001` capacidad > vehículo (RN12, `capacityKg`), `002` salida no futura (`departureAt`), `003` viaje sin reservas, `004` peso > restante (`weightKg`), `005` transición de viaje inválida, `006` reserva ya resuelta, `007` viaje con envíos ya salidos, `008` sugerencia ya resuelta, `009` sugerencia vencida, `010` carga no compatible con el vehículo (`cargoType`), `011` carga de la sugerencia ya no disponible, `012` monto para una carga que no es del plan |
| SHP (nuevo) | `003` solo se cancela un envío que aún no salió |
`BusinessException` admite `field` (`badRequest/conflict(code, msg, field)`) para que los clientes marquen el campo:
RUC inválido/duplicado → `ruc`, email repetido → `email`, placa repetida → `plate`, BID-001 → `amount`,
CAP-001/002 → `vehicleId`. Montos en mensajes: `shared.domain.Money.soles(...)` → `S/ 1,850.00`.
Listados paginados: `shared.domain.PageRequest/Paged` + `ApiResponse.of(Paged)` (agrega `meta.page|size|total`).
Por cursor (chat): `shared.domain.Slice` + `ApiResponse.of(Slice)` (agrega `meta.size|nextCursor`).
Enums expuestos en el contrato: dales un **nombre simple único** (`DocumentType`, `IncidentStatus`…). Un enum anidado
`Status`/`Type` nuevo renumera los schemas `Status2`, `Status3`… existentes y rompe a los clientes generados.
Rutas: todos los recursos usan la raíz `@Path("/api/v1")` (o la misma raíz exacta que otro recurso, p. ej.
`/api/v1/organizations`); Quarkus REST no combina raíces distintas que se solapan (da 404).
Los códigos son contrato: no reutilices ni cambies uno existente; agrega el siguiente número.

## Cuentas demo (solo perfil dev)
`pe.logyx.devdata.DemoDataSeeder` (`@IfBuildProfile("dev")`, en `StartupEvent`) crea, si no existe `pyme@logyx.pe`,
todo mediante los casos de uso reales autenticado como cada usuario (JWT → `CurrentIdentityAssociation`, con RLS).
Contraseña de todas: `Logyx2026!`.

| Email | Rol | Organización |
|---|---|---|
| `pyme@logyx.pe` | PYME (ADMIN) | Comercial Altiplano SAC — RUC 20601234565 |
| `carrier@logyx.pe` | CARRIER (ADMIN) | Transportes Sur Andino SRL — RUC 20602345671 · ASA-101 (TRUCK_MEDIUM), ASA-202 (TRUCK_LARGE), ASA-303 (REFRIGERATED) |
| `driver@logyx.pe` | DRIVER | conductor de Transportes Sur Andino |
| `carrier2@logyx.pe` | CARRIER (ADMIN) | Logística Titicaca EIRL — RUC 20603456786 · LTT-401 (TRUCK_SMALL), LTT-502 (PLATFORM) |
| `operator@logyx.pe` | OPERATOR (ADMIN) | LOGYX Operaciones SAC — RUC 20604567891 (creada con `AuthService.provisionOperator`; no hay registro público de operadores) |

Datos: 11 solicitudes en el corredor (Lima, Arequipa, Juliaca, Puno, Cusco, Tacna), 6 ofertas; la de Lima → Juliaca
está adjudicada a Sur Andino, con `driver@logyx.pe` asignado y el recojo completado (envío `IN_TRANSIT`, ASA-101
`IN_ROUTE`). Fase 2: envío Arequipa → Juliaca **entregado** con foto, firma y guía de remisión, calificado en ambas
direcciones (Trust Score de Sur Andino 77.8) y con una incidencia `CARGO_DAMAGE` abierta comentada; chat en
Arequipa → Puno (lácteos) y pregunta de Titicaca en Lima → Cusco; contra-oferta aceptada (Tacna → Arequipa) y otra
pendiente (quinua Juliaca → Lima); Puno → Tacna y Lima → Arequipa con `created_at` retrocedido 30/27 h (alertas
SLA asignables por el operador); notificaciones generadas por todos esos eventos. Los archivos van a
`logyx.documents.storage-dir`. Fase 4: 3 solicitudes nuevas (Juliaca → Arequipa lana 3 500 kg, +1 día = carga de retorno; Lima → Juliaca 7 500 kg y Lima → Puno
6 000 kg, +3 días, combinables); 4 viajes publicados (Sur Andino: Lima → Juliaca ASA-202 12 000 kg S/ 0.48/kg y Arequipa → Cusco ASA-101
6 000 kg tarifa por tramo S/ 3 200; Titicaca: Puno → Lima LTT-502 12 000 kg y Juliaca → Arequipa LTT-401 3 500 kg S/ 0.60/kg);
2 reservas de `pyme@logyx.pe` (2 500 kg en el viaje de Sur Andino, **pendiente**; 1 200 kg en el de Titicaca, **confirmada** con envío
generado → viaje en `FILLING`); 1 sugerencia Smart Load `PENDING` para `carrier@logyx.pe` (ASA-202 con las dos cargas desde Lima, 67.5 %);
`carrier@logyx.pe` ve 3 cargas de retorno (Juliaca → Arequipa, Juliaca → Lima quinua y Puno → Tacna) en `/return-loads` y en el dashboard.
En dev `logyx.auction.duration=PT72H` para que las subastas sigan abiertas. Dev Services recrea la BD
en cada arranque, así que el seed corre cada vez.
