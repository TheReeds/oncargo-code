# Backend LOGYX — guía para agentes

Quarkus 3.40 · Java 21 · PostgreSQL 16 (Flyway) · JWT RSA · Hibernate ORM Panache.
Contexto general: `../AGENTS.md`, `../docs/architecture/overview.md`, ADR-0001 (RLS), ADR-0002 (hexagonal),
ADR-0004 (eventos de dominio, notificaciones, escrituras del operador), ADR-0005 (almacenamiento de documentos) y
ADR-0006 (dos usuarios de BD, outbox transaccional, S3) y ADR-0010 (parámetros de sistema administrables, comisión y términos versionados). Despliegue: `../infra/README.md`; seguridad: `../docs/security.md`.

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
| `notification` | centro de avisos **por persona** alimentado por eventos, preferencias in-app/correo (V16); **V24 (2.2)**: preferencias de correo por persona (`EmailSettings`, `EmailDeliveryPolicy` puro: apagado/inmediato/resumen diario/silencio), cola de correos diferidos (`EmailQueueService` + job `EmailQueueScheduler`), baja sin login (`UnsubscribeToken` HMAC puro, `UnsubscribeLinks`, `UnsubscribeService`, `PublicUnsubscribeResource`) y avisos de cobro (`SettlementNoticeObservers`) | `notifications` (V8, `profile_id`/`mine` V16), `notification_preferences` (V16), `user_preferences`, `notification_email_queue` (V24) |
| `activity` | autoría y rastro de acciones de un recurso (`GET /activity`), solo lectura bajo RLS | sin tablas (lee `audit_log`, `created_by`) |
| `operator` | alertas SLA, métricas (con GMV y parámetros vigentes), **búsqueda `q`** (`OperatorSearchReader` → `NativeOperatorSearchReader`, SQL nativo bajo las políticas `*_operator`), **ofertas y chat en solo lectura con auditoría `OPERATOR_VIEWED_CHAT`**, **parámetros de sistema** (`/operator/parameters`, historial), candidatos y asignación manual (RN10), verificación de organizaciones y **visor de auditoría** (`AuditQueries` → puerto `AuditLogReader` → `NativeAuditLogReader`, SQL nativo con LEFT JOIN a `profiles`/`organizations`); sin tablas propias | políticas de escritura (V9); índices de `audit_log` y `requests_bidder_read` (V15) |
| `trip` | viajes publicados (RN12), reservas de espacio, cargas de retorno (RF61–RF63) y Smart Load Planner (OE07) | `carrier_trips`, `trip_bookings`, `smart_load_suggestions`, `smart_load_items` (V10) |
| `shared/config` (V18) | **parámetros de sistema editables por el operador**: `SystemParameters` (lectura cacheada 10 s por `SystemTx`, escritura del operador con versión, motivo, historial y auditoría; sin fila rige la configuración) + `domain/ParameterKey` (tipos y límites: `commission_rate`, `assignment_sla_hours`, `incident_warn_hours`, `incident_breach_hours`) | `system_parameters`, `system_parameter_history` (V18) |
| `pricing` (ola 1) | además del cálculo: **comisión** (`CommissionPolicy` puro + `CommissionService.currentRate()/breakdown()`), `GET /pricing/commission`, **cotizador público** `POST /public/pricing/quote` y `GET /public/pricing/commission` (sin JWT, `AuthRateLimits.beforePublicQuote`) | `shipments.commission_rate` (V18) |
| `fleet` (V20) | vehículos y conductores + **higiene de flota**: `PATCH /vehicles/{id}`, `POST /vehicles/{id}/archive` (`vehicles.archived_at`), licencia única por organización (`FLT-005`), no desactivar un conductor ni poner en mantenimiento un vehículo con envíos activos (`FLT-006`/`FLT-004`). Los compromisos (envíos activos, ofertas pendientes, viajes publicados) los lee por el puerto `application/port/out/FleetCommitments`, implementado en `infrastructure/integration/FleetCommitmentsAdapter` (así `fleet` no depende de `shipment`/`marketplace`/`trip`, que ya dependen de él) | `vehicles.archived_at`, `ux_drivers_org_license` (V20) |
| `marketplace` · RN02 por fecha (V20) | `application/VehicleAgenda` es **la única fuente** de la elegibilidad de un vehículo para una carga (mantenimiento, kg/tipo/m³, envíos activos solapados `CAP-005`, ofertas pendientes solapadas: aviso `CAP-006` y tope `CAP-007` = `logyx.bids.max-overlapping-per-vehicle`); la usan `PlaceBidUseCase` (`placeBidWithWarnings`) y `GET /shipment-requests/{id}/vehicle-eligibility`. `AcceptBidUseCase.award` guarda `CommissionService.currentRate()` en el envío y mantiene `CAP-005` como validación dura | `ix_bids_vehicle_pending` (V20) |
| `alerts` (V22, Ola 2 · 2.3) | **Búsquedas guardadas por persona** (`SavedSearch`, `SavedSearchService`; RLS `saved_searches_own`) y **avisos de carga nueva**: al publicarse una solicitud (`marketplace.application.event.RequestPublished`, por outbox) `LoadAlertMatcher` evalúa las búsquedas activas (filtros en `SavedSearchMatcher`, puro) y que el vehículo pueda ofertar (`VehicleAgenda.assess`, misma regla que ofertar); lo consume `notification/application/CarrierNotificationObservers` (`NEW_LOAD_MATCH` por persona, categoría `ALERTS`). Límite `logyx.alerts.max-per-search-per-hour` (10) con la bitácora `saved_search_alerts` (idempotente, solo sistema). Depende de `marketplace`, `fleet` y `catalog` (V24: e `identity` por las preferencias de la organización); nadie depende de él salvo `notification`. **V24 (2.2): alertas implícitas** por las rutas de interés del transportista: `LoadAlertMatcher.routeMatchesFor` (`RouteInterestMatcher` puro: ruta, tipo y precio sugerido/km; misma regla de vehículo, sin bloqueados, tope `logyx.alerts.max-implicit-per-org-per-hour` = 20, bitácora `org_route_alerts` por el puerto `RouteAlertLog`); el observador no duplica a quien ya recibió el aviso por una búsqueda guardada | `saved_searches`, `saved_search_alerts` (V22), `org_route_alerts` (V24) |
| `fleet` (V22, Ola 2 · 2.4 y 2.13) | además de V20: **documentos de flota con vencimiento** (`FleetDocumentService`, `FleetDocumentPolicy` puro, `FleetDocumentType`; archivo por el puerto `FleetFileStore` → `DocumentsFleetFileStore` sobre `DocumentStorage`; privados del transportista por RLS), barrido diario `FleetDocumentScheduler` (30/7/0 días, `fleet_document_notices`, evento `FleetDocumentExpiring` → categoría `FLEET`), **configuración del vehículo** (`equipment`, `acceptedCargoTypes`: restringe RN02, `Vehicle.configure`, `PUT /vehicles/{id}/config`), categoría/DNI del conductor y **agenda + asignación sugerida** (`FleetPlanningService`, `DriverRanking`, puerto `FleetPlanning` ↔ `FleetPlanningAdapter`: `GET /fleet/schedule`, `GET /fleet/assignment-suggestions`; `PUT /shipments/{id}/assignment` vive en `shipment/application/AssignResourcesUseCase`) | `fleet_documents`, `fleet_document_notices`, columnas de `vehicles`/`drivers` (V22) |
| `marketplace` · mejorar oferta (V22, 2.8) | `ImproveBidUseCase` + `Bid.improve` (solo bajar el monto y/o cambiar el vehículo; historial `bid_revisions`; evento `BidImproved`), `BidStandingPolicy.position` (`TOP_TWO`/`LOWER_HALF`/`UPPER_HALF`), `LaneStatsQueries` ← puerto `LanePriceStatsReader` (vista `lane_price_stats`: k ≥ 5 y ≥ 3 transportistas, redondeo a S/ 5), sellos de documentos copiados a la oferta (`soat_valid_until`/`inspection_valid_until`); `VehicleAgenda.assess` suma los avisos/bloqueo por documentos vencidos (`FLT-007/008`, parámetro `fleet_docs_block`). Endpoints en `CarrierBidResource` | `bid_revisions`, `lane_price_stats`, columnas de `bids` (V22) |
| `shared/outbox` | outbox transaccional de eventos (ADR-0006) | `outbox_events` (V11) |
| `realtime` | stream SSE `GET /api/v1/events/stream` alimentado por el outbox (ADR-0007) | `realtime_events` (V12) |
| `routing` | rutas y km por carretera entre ciudades del catálogo, orden óptimo de paradas, desvíos, `GET /api/v1/routes` (ADR-0008) | sin tablas: dataset `resources/routing/routes.json` |
| (varios) | huecos de flujo 2026-11-16: verificación de organizaciones, retiro de ofertas, cancelación/reasignación de envíos, estado final RF55, grupos de carga | columnas y políticas en V13 (sin tablas nuevas) |
| `places` (V17) | libreta de direcciones y almacenes frecuentes por organización (`SavedPlace`, `PlaceService`; archivo, sin borrado; orden por uso) | `saved_places` (V17, RLS por organización) |
| `search` (V17) | búsqueda global Ctrl+K: solicitudes, envíos y contrapartes por código, referencia, ruta, placa o RUC (`SearchQuery`, `SearchService`, `NativeSearchReader` con SQL nativo **bajo RLS, nunca `SystemTx`**) | sin tablas; índices `lower(code)`, `lower(customer_reference)` (V17) |
| `shipment` (V19, Ola 1 zona B, ADR-0011) | además del envío: **contactos de parada** copiados al adjudicar (`StopContact`), **conductor con mala señal** (idempotencia + `StopEventTimePolicy`), **listado filtrado y CSV** (`ShipmentFilter`, `searchMine`, `ShipmentCsv`), **enlaces públicos de seguimiento** (`ShareLink`, `ShareToken`, `ShareLinkPolicy`, `EtaPolicy`, `ShareLinkService`, `PublicTrackingService`, `PublicTrackingResource`) | columnas de `shipment_stops`, `shipment_share_links` (V19) |
| `templates` (V21, Ola 2 zona P, ADR-0013) | **Plantillas y cargas recurrentes**: `RequestTemplate` (campos de `CreateRequest` sin fecha), `RequestSchedule` (semanal/quincenal, anticipación, fin, feriados, modo `PUBLISH`/`DRAFT`), `ScheduleRun` (una por `(programación, recojo)`), `RecurrencePolicy`/`PeruHolidays` (dominio puro), `TemplateService` (CRUD, publicar ahora, borradores), `ScheduledTemplates` (job: `DueSchedules` por `SystemTx` solo ids → `SyntheticTenant.runAs` → `ScheduleStep.attempt`/`recordFailure`) | `request_templates`, `request_schedules`, `request_schedule_runs` (V21, RLS por organización) |
| `marketplace` · lado PYME (V21, Ola 2 zona P) | `CarrierPreferenceService` (preferidos/bloqueados), `InviteCarriersUseCase`, `BackupBidService` + `BackupAwardPolicy` (segunda oferta), `OffersInboxService` (bandeja), `BatchRequestsUseCase` (cancelar/reprogramar/duplicar por ítem, sin atomicidad), `AuctionRecoveryUseCase.reschedule`; web en `DemandResource`/`DemandDtos`; puerto `AwardedShipments` (SQL nativo bajo RLS, sin depender de `shipment`) | `carrier_preferences`, `bid_backups` (V21); `app_is_blocked_by()`; `requests_marketplace` y `bids_not_blocked` |
| `settlement` (V24, Ola 2 zona F · 2.12, ADR-0014) | **Cobros sin pasarela**: `SettlementService` (detalle, bandejas, facturar/pagar/confirmar/disputar/resolver, comisión cobrada por el operador, datos de cobro), `SettlementPolicy` (puro: lados, estados, bloqueo de ajustes, vencimiento y recordatorios), `PayoutAccountPolicy`, `SettlementReminders` (abre el cobro al entregar por outbox y barrido de recordatorios), adaptadores nativos (`NativeSettlementRepository` = repositorio + lector + `SettlementBootstrap`), `AesGcmPayoutCipher` (`logyx.settlement.payout-key`). Depende de `shipment`, `documents`, `pricing` | `shipment_settlements`, `settlement_history`, `commission_collections`, `settlement_notices`, `carrier_payout_accounts` (V24) |
| `reports` (V24, Ola 2 zona F · 2.11) | Reportes por rol, CSV y documentos internos del mes (`ReportService`, `ReportAggregator`/`ReportLine`/`StatementPeriod` puros, `NativeReportReader` **bajo RLS**). Montos = precio final + `CommissionPolicy` con la tasa del envío | sin tablas |
| `pricing` · tarifario (V24, 2.14) | `Tariff` (valores de una versión, `validate()`), `TariffCalculator(Tariff, PriceFloorPolicy)`, `TariffService` (vigente con caché por `SystemTx`, crear/anular del operador), `NativeTariffRepository`, `TariffResource` (`/pricing/tariffs*`). `PricingService` usa la vigente y `price_floor_ratio` | `tariff_versions`, `shipment_requests.tariff_version` (V24) |
| `catalog` · calendario (V24, 2.14) | `CalendarDay`/`CalendarDayKind`, `HolidayCalendar` y `NationalHolidays` (puros), `CalendarService` (lectura con caché por `SystemTx`; alta/baja del operador auditada), `CalendarResource` (`/catalog/calendar`). Lo usa `templates.ScheduleStep` | `calendar_days` (V24) |
| `identity` (V14, ADR-0009) | contraseñas (olvidé/restablecer, cambio, invitaciones), sesiones revocables (`sv`), refresh del móvil, usuarios y roles de la organización, perfil editable. **V24**: preferencias de la organización (`OrganizationPreferences`, `OrganizationPreferencesPolicy` puro, `OrganizationPreferencesService`, `OrganizationPreferencesResource` en `/organizations/me/preferences`; `settlement` lee `payment_terms_days` con su SQL) y términos al aceptar una invitación o al cambiar la versión (`TermsService`, `UserAccount.termsAccepted`) | `password_tokens`, `refresh_tokens` + columnas de `profiles`/`organizations` (V14), `organization_preferences` (V24) |

Dependencias entre módulos nuevos (todas por `application`): `negotiation → marketplace`; `documents → shipment`;
`reputation → shipment, documents`; `incident → shipment`; `marketplace → reputation` (Trust Score en ofertas);
`operator → marketplace, shipment, incident, fleet, reputation`; `trip → fleet, marketplace, shipment, identity, reputation, catalog`;
`dashboard → trip`; `pricing → routing` y `trip → routing` (km por carretera, `RoutePlanner`; trip los pasa a su dominio como `RoadDistances`); `notification` observa eventos de `trip`. `marketplace` y `shipment` no dependen de `trip`. `shipment` no depende de ellos: usa los puertos
`IncidentHoldProvider` (RN11) y `DeliveryEvidenceProvider` (foto de entrega), implementados en
`shipment/infrastructure/integration` (igual que `RequestInfoProvider`).

### Comisión y precio adjudicado (ADR-0010)
`pricing/domain/CommissionPolicy` (puro): la comisión es sobre el **valor de venta sin IGV** y lleva su propio IGV; `AwardedPriceBreakdown` trae `commission`, `commissionVat` y `carrierNet` (S/ 1,180 al 8 %: 80.00 + 14.40, neto 1,085.60). `AcceptBidUseCase.award` y `DirectContractUseCase` guardan la tasa vigente en `Shipment.commissionRate` (columna V18 `shipments.commission_rate`). `ShipmentView.priceBreakdown` (`shipment/infrastructure/web/PriceBreakdownView`) la arma según el rol del JWT: la PYME y el conductor no ven la comisión. Tests: `CommissionPolicyTest`, `CommissionFlowTest`.

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
| `RequestPublished` | `PublishRequestUseCase.publish` (también al republicar) | `notification/CarrierNotificationObservers` → `alerts.LoadAlertMatcher` (`NEW_LOAD_MATCH`, 2.3) |
| `BidImproved` | `ImproveBidUseCase` | notification (`BID_IMPROVED`, a la PYME) |
| `FleetDocumentExpiring` | `FleetDocumentService.sweepExpiring` (job diario, 30/7/0 días) | notification (`FLEET_DOCUMENT_EXPIRING`, a la organización transportista) |
| `AdjustmentProposed`, `AdjustmentDecided`, `AdjustmentEscalated` (V23) | `AdjustmentService.propose/decide/withdraw/escalateDue` | notification (`ExecutionNoticeObservers`), realtime (`shipment.adjustment_updated`) |
| `SettlementUpdated`, `PaymentDue`, `CommissionCollected` (V24) | `settlement` (pasos del cobro, recordatorios, comisión cobrada) | notification (`SettlementNoticeObservers`: a la contraparte / a las dos si actuó el operador; recordatorio a la PYME; comisión solo al transportista) |
| `IncidentAssigned` (V23) | `IncidentService.assign` | notification (aviso personal al responsable) |
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
· `ResourceSchedulePolicy` (F11, shipment; ventana de la agenda de RN02 por fecha) · `VehicleAgenda` (RN02 por fecha, marketplace) · `IncidentOutcomePolicy` (RF55, incident) · `Organization.ensureCanTransact`
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

## Cuentas, sesiones y roles (V14, ADR-0009)
- **Roles de organización** (`Profile.Role`): `ADMIN` todo; `OPS` opera; `VIEWER` solo lectura; `DRIVER` grupo propio.
  `identity/infrastructure/security/AccountAccessFilter` (`@ServerRequestFilter`, en todo endpoint con
  `@RolesAllowed`/`@Authenticated`): (1) versión de sesión: claim `sv` = `profiles.session_version` y usuario activo, si no
  401 `LOGYX-AUTH-401` (`SessionValidator`, `SystemTx` + caché 30 s `logyx.session.cache-ttl`); (2) VIEWER: **denegación
  por defecto** de POST/PUT/PATCH/DELETE salvo `identity/domain/OrgAccessPolicy.viewerMayWrite` (`/auth/*`, leer
  notificaciones, `/pricing/calculate`) → 403 `LOGYX-AUTH-007`; (3) `@shared.security.OrgAdminOnly` en método o clase → 403
  `LOGYX-AUTH-008`. Endpoint nuevo de escritura: no hace falta nada para VIEWER; si es de gestión, ponle `@OrgAdminOnly`
  **y** `AccessDenied.requireOrgAdmin(tenant)` en el caso de uso. Si un VIEWER debe poder escribir algo, agrégalo a
  `OrgAccessPolicy` (con test).
- **Subir la versión de sesión** (`Profile.revokeSessions`, o `changePassword/changeRole/deactivate`) + `profiles.update` +
  `AccountSessions.revoked(id)` (vacía la caché y revoca refresh tokens). Lo hacen: cambio/restablecimiento de contraseña,
  cambio de rol, desactivar usuario o conductor, reset del conductor por su ADMIN, `POST /auth/logout-all`.
- **Tokens** (`identity/domain/OpaqueTokens`): 32 bytes base64url, en BD solo SHA-256. `PasswordTokenService` (RESET 30 min,
  INVITE 72 h; "olvidé" corre en un pool propio fuera del hilo de la respuesta) y `RefreshSessions` (rotación, familia con
  vencimiento absoluto 30 días, reutilización = revoca la familia). Limpieza: `AuthTokenCleanup` (`logyx.auth.token-cleanup.every`).
- **Correo**: puerto `AccountMailer` → `infrastructure/mail/QuarkusAccountMailer` (Quarkus Mailer; textos en español,
  marca OnCargo, texto + HTML sin recursos remotos). dev/test `quarkus.mailer.mock=true`; tests: `support/Mailbox`
  (`MockMailbox`, espera el envío asíncrono).
- **Buzón dev** `GET /api/v1/dev/mailbox?to=<email>` (`DevMailboxResource`, `@IfBuildProperty(logyx.dev-mailbox=true)`:
  solo `%dev`, o una imagen compilada con `-Dlogyx.dev-mailbox=true`; oculto del OpenAPI): últimos 20 correos del mock con
  `subject`, `text`, `link` y `token` extraídos. Para e2e: `POST /auth/forgot-password` → leer el buzón → `/restablecer?token=`.
- **Usuarios** (`OrganizationUserService`, `/api/v1/organizations/me/users`): invitar (perfil con hash inutilizable +
  `invitation_pending`, token INVITE), reenviar, cambiar rol, desactivar/reactivar; siempre un ADMIN activo (fila de la
  organización con `FOR UPDATE`). Conductores: `FleetService.setDriverActive/resetDriverPassword` → `UserAccounts`; uno
  desactivado no se asigna (`LOGYX-FLT-003`).
- **Perfil de la organización** (`OrganizationProfileService`, `GET|PATCH /organizations/me`): ver ADR-0009 §7.

## Avisos por persona, preferencias, «Pedir acceso» y autoría (V16, Ola 1 zona D, ADR-0009 §8)
- **`app_profile()`**: `TenantSessionBinder` fija también `app.profile_id` (sub del JWT). Políticas por persona: `notifications`
  (`org_id = app_org() AND profile_id = app_profile()`; `logyx_app` solo actualiza `read_at`), `notification_preferences`,
  `realtime_events_read` (lo dirigido a un `user_id` solo lo lee esa persona). En tests RLS fija los tres (`profileOf(session)`).
- **Aviso nuevo**: en `NotificationObservers`, `Notification.to(org, …)` = toda la organización (el publisher lo expande en una fila por
  persona activa sin conductores ni invitaciones pendientes, `UserAccounts.noticeRecipients`) o `Notification.toPerson(org, perfil, …)`.
  Todo `NotificationType` debe tener categoría en `NotificationCategory.of` (preferencias). `NotificationPublisher` aplica las
  preferencias (`NotificationPreferencePolicy`), marca `mine` con `ResourceAuthors` (autor de la solicitud/oferta en esa organización)
  y encola el correo (`NotificationMailer` → `QuarkusNotificationMailer`, **después del commit**, `logyx.web.url` para los enlaces).
  `notification.created` va a `Recipient.user(org, perfil)`.
- **«Pedir acceso»**: `AccessRequestService` (`POST /organizations/me/access-requests`, VIEWER permitido): auditoría
  `ACCESS_REQUESTED` (es también el límite: `logyx.access-request.cooldown`, PT10M) + evento `AccessRequested` → aviso personal a cada ADMIN.
- **Autoría**: módulo `activity` (`GET /activity?subject=&id=`), SQL nativo de solo lectura bajo RLS sobre `audit_log`, `profiles`
  y `bids.created_by` (V16, DEFAULT `app_profile()`). Sin SystemTx. Una acción auditada nueva aparece sola; si su `entity` es hija de
  un sujeto nuevo, agrégala en `NativeActivityReader.entities`.
- Tests: `PerPersonNotificationFlowTest`, `NotificationPreferencePolicyTest`, `RowLevelSecurityTest.avisosYPreferenciasSonDeCadaPersonaDentroDeLaOrganizacion`.

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
  - `RequestSummaries.standings` (V15, RN04): posición del transportista en las solicitudes donde ofertó (`BidRepository.rankings`: conteos y el
    mejor monto; `BidStandingPolicy` lo convierte en banda). Del `SystemTx` solo sale `BidStanding{rank, bidCount, best, vsBest, finalStanding}`: nunca un monto ni un id ajeno.
  - `SystemParameters` (V18): lectura de `system_parameters` (datos de la plataforma, también sin usuario: endpoints públicos y jobs) con caché de 10 s; la escritura va en la transacción del operador con RLS (`params_operator_*`).
  - `AuthService.register` (V18): inserta `terms_acceptances` (versión + fecha de los términos) junto con el alta.
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
  - V14 (ADR-0009): `AuthService.login` también registra `last_login_at` y abre la familia de refresh;
    `AuthService.provisionUser` (seed/tests); `SessionValidator` (versión de sesión/estado del propio perfil, antes de la
    transacción de usuario); `RefreshSessions` (rotate/logout/cleanup) y `PasswordTokenService` (forgot/inspect/redeem,
    sin usuario; `password_tokens`/`refresh_tokens` no son accesibles para `logyx_app`); `AccountSessions.revoked`
    (revoca refresh tokens); `OrganizationUserService.invite` (unicidad global del email + token INVITE con FK al perfil
    nuevo, en una transacción) y `resendInvitation` (token); `OrganizationProfileService.update` (volver de REJECTED a
    PENDING toca columnas que el trigger de V13 reserva al operador/plataforma; solo la organización del JWT y tras
    comprobar ADMIN).
  - `shipment/application/PublicTrackingService.resolve` (V19, ADR-0011): el seguimiento público `GET /public/tracking/{token}` no tiene usuario; busca el enlace por el SHA-256 del token, comprueba su vigencia, cuenta la visita y arma solo la lista blanca `PublicTracking`. Nada más de esa superficie usa `SystemTx`.
  - V23 (Ola 2 zona X): `AdjustmentService.escalateDue` (job `AdjustmentScheduler`: escala ajustes vencidos de cualquier organización; solo toca
    `escalated_at` y audita); `OrganizationSuccessionService.change` (cambio de RUC: inserta la sucesora, mueve personas, conductores y vehículos,
    copia la reputación y suspende la antecesora; ninguna transacción de usuario puede escribir en dos organizaciones); `SignupFingerprints.record`
    (guarda el hash de la IP del registro, sin usuario); `PublicAccessRequestService.request` (busca la organización por RUC y sus ADMIN, sin
    usuario). La ficha 360 y las señales **no** usan SystemTx: leen con las políticas `*_operator`.
  - V24 (Ola 2 zona F): `TariffService.snapshot` y `CalendarService.snapshot` (datos de plataforma que leen el cotizador público y las
    plantillas sin usuario, caché 10 s); `SettlementService.ensure`/`collectCommission` y `SettlementReminders` (abren el cobro con el plazo de
    pago de la PYME, que el transportista no puede leer; barrido de recordatorios entre organizaciones); `shipment.NativeSettlementLock` (solo el
    estado del cobro de ese envío: el conductor que propone un ajuste no ve cobros). Reportes **no** usan SystemTx.
  - V24 (zona F · 2.2 y 2.17): `EmailQueueService.flushBatch` (job de la cola de correos; `notification_email_queue` es solo del sistema,
    `FOR UPDATE SKIP LOCKED`); `NotificationPublisher` ahora también lee `user_preferences` y encola en la misma transacción del despacho;
    `UnsubscribeService.inspect/unsubscribe` (baja sin login: solo las preferencias del perfil firmado en el token HMAC, lista blanca
    `UnsubscribeView`); `LoadAlertMatcher.routeMatchesFor` (en el despacho: lee `organization_preferences` de todos los transportistas y escribe
    `org_route_alerts`); `PasswordTokenService.redeem` inserta además `terms_acceptances` con `source = 'INVITATION'`; `DemoDataSeeder.acceptDemoTerms`
    y `DevTermsResource` (solo dev/e2e, `logyx.dev-mailbox=true`). Aceptar la versión nueva (`TermsService.accept`) y las preferencias de
    persona y organización van **bajo RLS**.
  - `BookingService.cancelByRequester`: la PYME devuelve la capacidad en `carrier_trips` (solo lectura para ella); se bloquea
    la fila y se resta el peso de esa reserva.
- Auditoría del operador (`GET /api/v1/operator/audit`, `/audit/actions`; V15): `NativeAuditLogReader` lee `audit_log` con `audit_operator` y hace LEFT JOIN a `profiles`/`organizations` (el operador las lee enteras: `profiles_operator`, `orgs_read`). Filtros en `AuditFilter` (`LOGYX-VAL-001` con `field`; `action` exacta, repetible o con comas; `from`/`to` de Lima, inclusive). Índices por fecha, organización, actor y acción. Acción nueva auditada = nada que hacer: aparece sola en `/audit/actions`.
- Listados globales del operador (`GET /api/v1/operator/requests|shipments`, `OperatorService` → `MarketplaceQueries.allRequests` / `ShipmentQueries.allShipments`, paginados y filtrados por estado/ciudad/fechas): leen con las políticas `*_operator`, sin SystemTx. Viven en `OperatorResource` (un `@Path` más corto en otra clase daría 404: RESTEasy elige la clase por el prefijo más largo).
- El operador **no** usa `SystemTx`: lee con las políticas `*_operator` (V2, V4–V7) y la asignación manual escribe
  con las políticas de V9 (`bids_operator_insert` exige `assigned_by_operator`, etc.). V13 le suma UPDATE de
  `organizations` (verificación; un trigger impide que otra organización toque esas columnas), `shipments`,
  `shipment_stops`, `vehicles` y `drivers` (cancelar o dar por entregado al resolver una incidencia, RF55).
- `shipment_requests` (V15, F15): además de `requests_owner`/`requests_marketplace`/`requests_operator`, `requests_bidder_read` deja a un transportista leer (solo SELECT) las solicitudes en las que tiene una oferta propia, aunque se adjudiquen a otro o se cancelen. No abre `bids` ajenos ni `shipments`. Quien no ofertó sigue viendo 404.
- RLS por tabla nueva: `messages`/`counter_offers`/`documents`/`incidents`/`incident_comments` → las dos partes
  (`requester_org_id`, `carrier_org_id` denormalizados) + lectura del operador; `reviews`/`reputation_scores` →
  lectura pública (RF44); `notifications` → solo la propia organización. Historial, evidencia y calificaciones no
  admiten UPDATE/DELETE de usuarios. Casos en `RowLevelSecurityTest`.
- `trip`: `carrier_trips` → dueño (solo `CARRIER`), PYMEs leen los OPEN/FILLING y los que reservaron, operador lee; `trip_bookings` →
  las dos partes leen/actualizan, solo la PYME (`PYME`) inserta, operador lee, sin DELETE; `smart_load_*` → solo el transportista
  dueño (+ lectura del operador, RF59).
- La transacción de `SystemTx` no ve lo no confirmado de la transacción externa de usuario (ver comentario en `PlaceBidUseCase`).
- `password_tokens`/`refresh_tokens` (V14): RLS activada **sin políticas** y `REVOKE ALL` para `logyx_app` (sin `FORCE`:
  `logyx_owner` las usa vía `SystemTx` y no tiene `BYPASSRLS`). Caso en `RowLevelSecurityTest`.
- `realtime_events` (V12): `logyx_app` solo SELECT de lo dirigido a su organización o a su tipo (sin INSERT/UPDATE/DELETE).
- `outbox_events` (V11): `logyx_app` solo INSERT con `actor_org_id = app_org()`. `DatabasePrivilegesTest` prueba que
  `logyx_api` no puede `RESET ROLE`/`SET ROLE` a un rol privilegiado, alterar tablas/políticas ni leer sin organización.

## Seguridad HTTP (docs/security.md)
- V14: `AuthRateLimits` también limita "olvidé mi contraseña" (por IP → 429; por email → misma respuesta 202 sin enviar),
  canje de enlaces, refresh y contraseñas actuales incorrectas en `change-password`.
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
| GEN | `404` no encontrado ("Solicitud no encontrada", "Envío no encontrado": `BusinessException.notFound` concuerda el género), `403` regla de permiso de negocio, `500` |
| HTTP | `LOGYX-HTTP-<status>` errores del framework (ruta inexistente 404, método 405, formato 406/415, cuerpo 413) con mensaje en español; siempre JSON (`ExceptionMappers.json`, `PayloadTooLargeHandler` para el 413 de Vert.x) |
| AUTH | `001` credenciales, `002` email ya registrado, `401` sin token o sesión revocada (`sv`), `403` rol sin permiso, `429` demasiados intentos (login/registro/olvidé/canje/refresh/cambio de contraseña), `005` enlace de contraseña inválido, vencido o usado (`token`), `006` contraseña actual incorrecta (`currentPassword`), `007` (403) VIEWER solo lectura, `008` (403) solo el ADMIN de la organización, `009` (401) refresh inválido/vencido/reutilizado/revocado, `010` nueva contraseña igual a la actual (`newPassword`), `011` invitación sin aceptar la política de privacidad (`acceptPrivacyPolicy`), `012` (409) versión de los términos desactualizada (`termsVersion`; el registro exige `acceptTerms`), `013` (V24) invitación sin aceptar los Términos (`acceptTerms`) |
| USR | `001` último ADMIN activo, `002` no puedes desactivarte, `003` invitación ya aceptada, `004` rol no permitido (`role`, DRIVER), `005` el usuario es conductor (se gestiona en Flota), `006` reenviar a un usuario desactivado, `007` (V23) lo suspendió el operador: su ADMIN no lo reactiva, `008` las cuentas del operador no se suspenden desde el 360 |
| VAL | `001` Bean Validation (`field` = propiedad), `002` teléfono inválido (`PeruPhone`, 9 dígitos con +51 opcional) |
| ORG | `001` RUC inválido, `002` RUC duplicado, `003` (403) sin verificar por el operador (RN01), `004` (403) suspendida, `005` operadora no se verifica, `006` falta motivo (`reason`), `007` razón social/RUC no editables (verificada o suspendida), `008` (V23) cambio de RUC con compromisos vivos, `009` cambio legal sin cambios, `010` (V24) ruta de interés con origen = destino (`routes`), `011` más de 20 rutas, `012` horario de recojo por defecto incompleto o invertido, `013` plazo de pago fuera de 0–120 (`paymentTermsDays`), `014` precio mínimo por km fuera de S/ 0.01–100 (`minPricePerKm`) |
| NTF | `001` el in-app de «Cuenta y accesos» no se apaga (`inApp`), `002` categoría que no aplica al tipo de cuenta, `003` (404, V24) enlace de baja inválido (misma respuesta para todo token malo), `004` horario de silencio incompleto o de duración cero (`quietFrom`/`quietTo`), `005` hora del resumen fuera de 0–23 (`digestHour`) |
| FLT | `001` placa duplicada, `002` estado "en ruta" lo controla el envío, `003` conductor desactivado (`driverId`) |
| PRC | `001` ciudad fuera del corredor, `002` origen = destino |
| REQ | `002` cancelar solo publicadas, `003` fecha de recojo pasada (`pickupDate`), `004` solo se republica una cancelada, `006` ventana horaria incompleta o invertida (`pickupWindowTo`/`deliveryWindowTo`), `007` valor declarado ≤ 0 (`declaredValue`) |
| PLC | `001` horario de la dirección guardada inválido (`windowTo`), `002` dirección archivada (se restaura antes de editar) |
| EXP | `001` exportación CSV de más de 10 000 filas (acota los filtros) |
| AUC | `001` subasta no abierta, `002` subasta cerrada, `003` solo se reabre una cerrada por tiempo |
| BID | `001` bajo el piso (RN03), `003` ya ofertó (o retiró), `004` no admite aceptar, `005` no se puede retirar |
| CAP | `001` sin capacidad (RN02), `002` vehículo no disponible, `003` tipo de vehículo no apto para la carga, `004` volumen, `005` vehículo comprometido en otro envío solapado (F11) |
| SHP | `001` envío cerrado: no se reasigna (antes: "solo antes del recojo"), `002` (404) el conductor no tiene envío activo, `004` conductor con otro envío solapado (`driverId`), `006` falta motivo (`reason`; también el registro de una parada desde la oficina), `007` mismo vehículo, `008` (V23) lo que pide el recojo ya no cambia (envío no PENDING) |
| TRK | `001` orden de paradas (RN08), `002` ya llegó, `003` falta llegada (se comprueba antes que la foto), `004` envío cerrado, `005` incidencia activa (RN11), `006` entrega sin foto, `007` la parada ya está completada (`002`/`007` ya no salen al **repetir**: arrive/complete son idempotentes desde V19), `008` hora del evento (`occurredAt`) fuera de límites, `009` (V23) falta la foto de la carga que pidió la PYME, `010` peso medido faltante o fuera de 1–60 000 kg (`weightKg`), `011` receptor inválido (`receivedByName`/`receivedByDoc`) |
| SHL | `001` (404) enlace público no disponible (inexistente, revocado o vencido: misma respuesta), `002` envío cancelado, `003` entregado hace más de 7 días, `004` 10 enlaces vigentes |
| NEG | `001` datos de contacto (RN07, `body`/`message`), `002` hilo cerrado, `003` mismo monto, `004` contra-oferta pendiente, `005` oferta no negociable, `007` ya respondida |
| DOC | `001` tipo no permitido, `002` muy grande, `003` vacío (`file`), `004` falta parada de entrega, `005` sin llegada (`stopId`), `006` envío cancelado, `007` (403) evidencia solo del conductor asignado, `008` evidencia cerrada (parada completada o envío entregado), `009` (V23) `PICKUP_PHOTO` sin la parada de recojo (`stopId`), `010` (403, V24) `PAYMENT_PROOF` solo la PYME, `CARRIER_INVOICE` solo la oficina del transportista, `500` fallo de almacenamiento |
| REV | `001` no entregado, `002` sin foto (RN06), `003` ya calificó |
| INC | `001` ya hay activa, `002` transición inválida (`status`), `003` faltan notas (`resolutionNotes`), `004` cerrada, `005` envío cancelado, `006` estado final del envío faltante o incoherente (`shipmentOutcome`, RF55), `007` (V23) solo quien reportó retira una informativa activa, `008` reabrir una abierta o retirada, `009` (403) nota interna de una parte, `010` monto reclamado ≤ 0 (`claimedAmount`), `011` adjuntos que no son del envío (`attachmentIds`), `012` responsable que no es del operador (`assigneeProfileId`) |
| OPS | `001` SLA no vencido (RN10), `002` no es transportista (`carrierOrgId`), `003` transportista no verificado o suspendido |
| ADJ | (V23, ajustes del envío) `001` envío cancelado, `002` entregado hace más de 7 días, `003` monto inválido (`amount`), `004` falta la nota (`note`: OTHER o al rechazar), `005` tope de 10 por envío, `006` (403) no te toca decidir (PYME antes del escalado, operador después), `007` ya resuelto, `008` evidencia de otro envío (`evidenceDocumentId`) |
| PAR | `001` valor inválido o fuera de rango (`value`), `002` falta el motivo (`reason`), `003` (409) aviso de incidencias ≥ incumplimiento, `004` (409) otro operador cambió el parámetro (`expectedVersion`) |
| STL | (V24, cobros) `001` (409) el envío no está entregado, `002` (409) transición no válida, `003` (409) ajustes pendientes, `004` (409) no disputable, `005` (403) no te toca (lado o conductor), `006` (409) comisión ya cobrada, sin comisión o en disputa, `007` CCI o celular inválido (`number`), `008` (409) más de 5 datos de cobro, `009` documento de otro tipo o envío (`documentId`), `010` envío de otro transportista (`shipmentIds`) |
| TRF | (V24, tarifario) `001` valor fuera de rango (`field` = el campo, p. ej. `cargoMultipliers.FRAGILE`), `002` sin motivo, `003` vigencia pasada o a más de un año (`effectiveFrom`), `004` (409) anular una versión que ya rige o ya anulada, `005` (409) otro operador creó una versión |
| CAL | (V24, calendario) `001` fecha pasada o a más de 3 años (`day`), `002` (409) ya existe ese día con ese nombre |
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
| `pyme.ops@logyx.pe` | PYME (OPS) | Comercial Altiplano SAC — opera, sin usuarios ni perfil (V14) |
| `pyme.viewer@logyx.pe` | PYME (VIEWER) | Comercial Altiplano SAC — solo lectura (V14) |
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

## Solicitud de la PYME: código, datos opcionales, libreta, búsqueda y CSV (V17, Ola 1 zona A)
- **Código** `OC-XXXXX` (`marketplace/domain/RequestCode`, alfabeto sin 0/1/I/L/O/U): lo asigna `RequestCodes.next()` (comprueba con `SystemTx` porque bajo RLS una PYME no ve los códigos ajenos; el índice único cubre la carrera). Toda solicitud nueva lo necesita (`DirectContractUseCase` también); el envío lo hereda (`shipment` lo lee de la solicitud, ver CHANGELOG).
- **Datos privados** (`ShipmentRequest.Details`: referencia, valor declarado, `StopDetails` de recojo y entrega): viajan en la fila de la solicitud; `RequestView` los entrega según `RequestSummary.showPrivate` (PYME dueña, operador, transportista adjudicado con `MATCHED`). El valor declarado es público para transportistas. Un campo nuevo privado: agrégalo a `RequestView` con el mismo patrón (`priv ? x : null`) y a `canSeePrivate`, no confíes en la UI.
- **Idempotencia** (`shared/idempotency/IdempotencyKeys`, tabla `idempotency_keys` con RLS): `lock` (candado consultivo de la transacción) → `find` → crear → `remember`, todo en la misma transacción del caso de uso (`PublishRequestUseCase.publishOnce`). Reutilizable para otras escrituras (otro `scope`).
- **Libreta** (`places`): `PlaceService.markUsed` lo llama `PublishRequestUseCase` con `originPlaceId`/`destinationPlaceId`. Un recurso nuevo que necesite una dirección guardada la lee por `PlaceService`, nunca por su repositorio.
- **Búsqueda** (`search`): el lector nativo cruza `shipment_requests`, `shipments`, `bids` y `organizations` y confía en la RLS; la referencia propia solo se busca para PYME/operador (o el transportista adjudicado). Tipo de resultado nuevo → `SearchHitType`, `SearchReader`, `SearchService` (href por rol) y test en `SearchFlowTest`.
- **Listados filtrados y CSV**: `MyRequestFilter` → `searchMine` (JPQL con `?n`, comodines de usuario escapados con `LikePatterns`); el CSV usa la **misma consulta** que el JSON (`MarketplaceQueries.exportMine`) y `shared/api/CsvWriter`. Otros módulos que exporten: reutiliza `CsvWriter` y el tope `EXPORT_MAX_ROWS`.

## Envíos, seguimiento público y conductor con mala señal (V19, Ola 1 zona B, ADR-0011)
- **Contactos de parada (RN07 post-adjudicación):** `CreateShipmentUseCase` (el único lugar que crea paradas) lee los `StopDetails` de la
  solicitud por `RequestInfoProvider` (`RequestInfo.pickupContact/deliveryContact`, `code`, `customerReference`) y los guarda en
  `ShipmentStop.contact` (`StopContact`). `ShipmentSummary.contactsVisible` (calculado en `ShipmentQueries`: partes, operador y, entre
  conductores, solo el asignado) decide si `StopView`/`ShipmentView.customerReference` los llevan. En la BD, `stops_driver_scope`
  (RESTRICTIVA, `app_driver_id()`) acota a un conductor a las paradas de sus envíos. `AcceptBidUseCase` no se tocó.
- **arrive/complete:** `TrackStopsUseCase` toma un candado consultivo por envío (`lockResource("shipment-track:" + id)`) **antes** de leer;
  si la acción ya estaba aplicada devuelve el envío sin efectos (idempotente, incluso cerrado o con incidencia); si no, valida orden,
  incidencia y foto como antes y resuelve la hora con `StopEventTimePolicy.resolve(occurredAt, now, pasoPrevio)` (`TRK-008`). Guarda la hora
  del evento en `arrived_at/completed_at` y la del servidor en `*_recorded_at`. La entrega (`delivered_at`) usa la hora del evento.
- **Listado y CSV:** `ShipmentFilter.of(status, originCity, destinationCity, fromDate, toDate, q)` → `ShipmentRepository.searchMine`
  (JPQL con subconsultas a `ShipmentRequestEntity` por nombre de entidad, bajo RLS) → `ShipmentQueries.mine(filter, page)` y
  `exportMine` (por páginas de 100, tope `EXPORT_MAX_ROWS`). El CSV (`ShipmentCsv`) sale de **la misma consulta**.
- **Enlaces públicos:** `ShareLinkService` (bajo la RLS de la PYME: `create` muestra el token una vez, `list`, `revoke` con UPDATE por
  columna) y `PublicTrackingService` (`SystemTx`, lista blanca). Campo nuevo para el cliente final = agrégalo a `PublicTracking` **y** a
  `PublicTrackingView`, y actualiza el conjunto exacto de claves de `ShipmentTrackingFlowTest`; nunca mapees un `ShipmentView` ahí.
  Límite por IP: `AuthRateLimits.beforePublicTracking` (`logyx.rate-limit.public-tracking.per-ip`). La URL que se devuelve usa
  `logyx.web.url` + `/s/<token>`.
- Tests: `ShipmentTrackingFlowTest` (contactos y visibilidad, idempotencia sin efectos dobles, hora del dispositivo, filtros y CSV, lista
  blanca del enlace público y no filtración, permisos, vencimiento), `StopEventTimePolicyTest`, `ShareLinkPolicyTest`, `EtaPolicyTest`,
  `RowLevelSecurityTest.unConductorSoloVeYTocaLasParadasDeSusEnvios` y `.enlacesPublicosSonDeLaPymeDuenaYSoloSeRevocan`.

## Ola 2 zona P: plantillas, preferidos/bloqueados, segunda oferta, bandeja y masivas (V21, ADR-0013)
- **Tenant sintético** (`shared/tenancy/SyntheticTenant`): `runAs(orgId, orgType, profileId) { … }` activa un contexto de petición con un `JsonWebToken` en memoria (`org_id`, `org_type`, `role=OPS`, `sub`); `TenantContext` y `TenantSessionBinder` funcionan igual que en una petición (fijan `app.org_id`/`app.profile_id`, `SET LOCAL ROLE logyx_app`). **No salta RLS.** Úsalo para jobs que escriben datos de una organización; para cruzar organizaciones sigue `SystemTx`.
  Uso de `SystemTx` nuevo: `ScheduledTemplates.runDue` (`DueSchedules`: lee ids de programaciones vencidas de todas las organizaciones; nada más).
- **Jobs sin reintento en bucle**: `ScheduleStep.attempt` (transacción bajo el tenant; candado consultivo por programación) y, si lanza, `recordFailure` (otra transacción: registra el fallo, avanza, avisa; pausa a los 3 fallos seguidos). `logyx.templates.every` (10 min, `%test` off): en tests se invoca `ScheduledTemplates.runDue(now)` a mano tras adelantar `next_run_at` con `SystemTx`.
- **Bloqueo en la BD, no en `BidEligibilityPolicy`**: `requests_marketplace` excluye `app_is_blocked_by(requester_org_id)` y `bids_not_blocked` (restrictiva) impide insertar ofertas; `AcceptBidUseCase.accept` responde `BID-006`. Si T quiere un mensaje propio al ofertar, puede consultar `CarrierPreferenceService`/la función; hoy un bloqueado recibe 404 de la solicitud (no se entera).
- **Segunda oferta**: `AcceptBidUseCase.award` llama a `BackupBidService.keep(others)` (una fila de `bid_backups` por oferta rechazada, `AVAILABLE`). `bid_backups` solo admite `UPDATE (state, requested_at, responded_at, used_at)` (`GRANT` por columna): `PanacheBidBackupRepository.save` actualiza con JPQL, nunca con la entidad completa. La PYME pide (`REQUESTED`), el transportista confirma o declina (RLS: solo ve su fila si `state <> 'AVAILABLE'`), la PYME adjudica (`USED`) con `AcceptBidUseCase.award` → `ShipmentRequest.matchTo` también limpia `cancelReason`. `shipments.request_id` es único solo entre los **no cancelados** (`ux_shipments_request_active`): un envío cancelado y uno nuevo comparten solicitud; las consultas por solicitud deben tolerar más de un envío.
- **Acciones masivas**: `BatchRequestsUseCase` **no** es `@Transactional` a propósito: cada caso de uso que invoca abre y cierra su transacción (por ítem). Resultado `ItemResult{ok,code,message,field,resultId}`; máx. 50 (`BAT-001`).
- **Migración**: usa una función `pg_temp.p21_add_check_values` con nombre propio (pg_temp vive toda la sesión de Flyway: dos migraciones con la misma función chocan) para **agregar** valores a los CHECK de `notifications` sin pisar los de T y X.
- Códigos: `TPL` plantillas (001 nombre repetido, 002 archivada, 003 fin pasado, 004 borrador ya resuelto, 005 sin programación), `PRF` marcas (001 a sí misma, 002 no es transportista), `BCK` segunda oferta (001 no corresponde, 002 pasaron 24 h, 003 recojo pasado, 004 sin confirmación, 005 estado no válido), `BAT-001` lote, `BID-006` transportista bloqueado, `REQ-008` solo publicadas, `REQ-009` reprogramar con ofertas.
- Tests: `TemplatesFlowTest`, `CarrierPreferencesFlowTest`, `BackupBidFlowTest`, `RequestBatchFlowTest`, `DemandZoneRlsTest`, `RecurrencePolicyTest`, `BackupAwardPolicyTest`.

## Ola 2 zona X: ejecución y operación (V23, 2026-11-19; roadmap 2.5, 2.6, 2.7, 2.15)
Contrato: `docs/api/CHANGELOG.md` (zona X). ADR-0012 (ajustes y precio final). Reglas: `docs/domain/business-rules.md` (apoyo 2.5–2.15, RN11).
- **Recojo y oficina (2.5):** `Shipment.PickupRequirements` (foto/peso; `PickupRequirementsService`), `ShipmentStop` suma `loadedWeightKg`,
  `receivedBy*` y `*OfficeReason` (vía `restoreExecution`, sin tocar los constructores). `TrackStopsUseCase.complete(…, StopCompletion)` valida con
  `StopCompletionPolicy`; `arriveByOffice`/`completeByOffice` usan el mismo candado consultivo que la app. `DocumentType.PICKUP_PHOTO` con
  `DocumentUploadPolicy.ensurePickupEvidence`. El puerto `DeliveryEvidenceProvider` suma `hasPickupPhoto` y `belongsToShipment` (métodos
  `default`: los fakes viejos compilan).
- **Ajustes (2.6):** `shipment/domain/{AdjustmentPolicy, model/ShipmentAdjustment, model/AdjustmentType, model/AdjustmentStatus}`,
  `application/AdjustmentService` (+ `AdjustmentsOverview`, `ProposeAdjustmentCommand` con `Idempotency-Key`), `infrastructure/persistence/
  {ShipmentAdjustmentEntity (@DynamicUpdate: V23 solo concede UPDATE de estado y decisión), PanacheAdjustmentRepository}`, job
  `infrastructure/scheduling/AdjustmentScheduler` (`logyx.adjustments.every`, 5 m; `%test` off). `ShipmentSummary` trae `adjustmentsTotal` y
  `pendingAdjustments` (una consulta agregada por página) y `finalPrice()`; `ShipmentView.finalPriceBreakdown` usa la tasa guardada.
- **Incidencias v2 (2.7):** la categoría manda (`IncidentCategory.blocking/defaultSeverity/label`); `Incident.report(…, Details, sla)`,
  `withdraw`, `reopen`/`ensureReopenable`, `assign`, `slaBreached`; persistencia por `restoreV2`. `IncidentComment.internal`. SLA desde
  `SystemParameters.incidentSla(severity)`. Bandeja del operador `IncidentQueries.operatorInbox` (`OperatorIncidentFilter`) servida en
  `OperatorResource` (`/operator/incidents`: el prefijo `/api/v1/operator` es de esa clase). `IncidentReported` suma `blocking`/`severity`
  (nulos en eventos viejos = congela).
- **Operador 360 (2.15):** `operator/application/Organization360Service` (ficha, señales, cuentas, cambio legal, ajustes escalados) sobre
  puertos de solo lectura `Organization360Reader`/`FraudSignalReader` (SQL nativo bajo `*_operator`) y servicios de `identity`
  (`OperatorAccountService`, `OrganizationSuccessionService` + `OrganizationSuccessionStore`) y `documents` (`OrganizationFileService`: archivos
  de una organización fuera de un envío). `operator/domain/{FraudSignalPolicy, FraudSignalKind, FraudSignalSeverity}`. Umbrales `logyx.fraud.*`.
  Herencia de reputación: `ReviewRepository.listReceivedWithPredecessors` (CTE recursiva sobre `organizations.predecessor_id`).
- **Parámetros nuevos** (`ParameterKey`, `Kind.AMOUNT` para soles): `adjustment_response_hours`, `detention_free_hours`, `detention_hourly_rate`,
  `incident_sla_{critical,high,medium,low}_hours`.
- **Migración V23 en dev:** si editas V23 después de aplicada, recalcula el checksum de Flyway y aplica el delta a mano en la BD de Dev Services
  (o reinicia `quarkus:dev`, que la recrea). El checksum de Flyway es el CRC32 de las líneas sin fin de línea.
- Tests: `ExecutionAndAdjustmentsFlowTest`, `IncidentsV2AndOperator360FlowTest`, `ExecutionRowLevelSecurityTest`, `AdjustmentPolicyTest`,
  `StopCompletionPolicyTest`, `IncidentV2Test`, `FraudSignalPolicyTest`. `support/Api.plate()` ahora usa 5 caracteres base 36 (con `% 1000` la
  suite repetía placas al pasar de mil vehículos y fallaban tests ajenos con `FLT-001`).

## Ola 2 zona F: cobros, reportes, tarifario, subasta, piso y feriados (V24, 2026-11-19; roadmap 2.11, 2.12, 2.14; ADR-0014)
Contrato: `docs/api/CHANGELOG.md` (zona F). Reglas: `docs/domain/business-rules.md` (RN03, RN04, cobros).
- **Cobro** (`settlement`): una fila por envío entregado; la abren `SettlementReminders.onDelivered` (outbox), el barrido o el primer paso
  (todos por `SettlementBootstrap.open`, `ON CONFLICT DO NOTHING`). Transiciones en `SettlementService.transition`: candado
  `shipment-adjust:<envío>` (el mismo de los ajustes) → `SettlementPolicy` → `update` + `settlement_history` + auditoría `SETTLEMENT_*` +
  `SettlementUpdated`. **Bloqueo de ajustes**: estado ≠ `PENDING_INVOICE` ⇒ `AdjustmentService` responde `ADJ-009` (puerto
  `shipment/application/port/out/SettlementLock`). La comisión la cobra el operador (`collectCommission`, `COMMISSION_COLLECTED`); el trigger
  `shipment_settlements_guard` impide que una parte toque `commission_*`. DTO por rol: la PYME nunca recibe la comisión.
- **Datos de cobro**: `carrier_payout_accounts.number_enc` = AES-256-GCM (versión + IV + texto); en prod **`LOGYX_PAYOUT_KEY`** obligatorio
  (`openssl rand -base64 32`); sin clave, solo fallan los endpoints de datos de cobro. Rotarla exige re-cifrar.
- **Reportes** (`reports`): una consulta nativa bajo RLS (envíos no cancelados + solicitud + ajustes aceptados + cobro + vehículo propio) y la
  aritmética de `CommissionPolicy` por línea; nunca sumes `agreed_price` en SQL para un reporte. Tope 20 000 líneas (`truncated`), CSV 10 000.
  Emisor de las liquidaciones: `logyx.billing.issuer-name|issuer-ruc` (`BILLING_ISSUER_NAME`, `BILLING_ISSUER_RUC`).
- **Tarifario**: un cambio = versión nueva (`TariffService.create`, auditoría `TARIFF_VERSION_CREATED|CANCELLED`); en tests crea otra con los
  valores anteriores al terminar (no se borra). **Parámetros nuevos** `auction_duration_minutes`, `auction_max_bids`, `price_floor_ratio`,
  `payment_terms_days`: `PublishRequestUseCase`/`AuctionRecoveryUseCase` leen `SystemParameters.auctionDuration()/auctionMaxBids()` (ya no
  `@ConfigProperty`). Restaura su valor al final de un test que los cambie.
- **Calendario**: `ScheduleStep` usa `CalendarService.skipsRecurrence(fecha, ciudad de origen)`; `PeruHolidays` queda como alias de
  `catalog.domain.NationalHolidays` (respaldo de un año sin feriados cargados).
- Tests: `SettlementFlowTest`, `ReportsFlowTest`, `TariffAndCalendarFlowTest`, `FinanceRowLevelSecurityTest`, `SettlementPolicyTest`,
  `PayoutAccountPolicyTest`, `AesGcmPayoutCipherTest`, `ReportAggregatorTest`, `HolidayCalendarTest`, `TariffCalculatorTest`, `ParameterKeyTest`.

## Ola 2 zona F: preferencias, correo y términos (V24, 2026-11-19; roadmap 2.2 y 2.17)
Contrato: `docs/api/CHANGELOG.md` («Ola 2 zona F — preferencias, correo y términos»). Seguridad: `docs/security.md` (baja, correo masivo).
- **Correo de avisos por persona.** Orden: matriz categoría × canal (V16) → `EmailDeliveryPolicy.decide(user_preferences, ahora en Lima)`:
  `SKIP` (correo apagado), `NOW` (sale tras el commit como antes) o `DEFER` (`notification_email_queue` con `reason` `QUIET`/`DIGEST` y
  `send_after`). El job `EmailQueueScheduler` (`logyx.notifications.email-queue.every`, 5 m; `%test` off: en tests
  `EmailQueueService.flushDue(now)` a mano) toma lo vencido por persona, marca `sent_at` y manda **un correo por persona** (varios = «Tienes N
  avisos»); vuelve a mirar el perfil y las preferencias al enviar (desactivada, sin email o que apagó el correo/la categoría: se marca y no sale).
- **Baja sin login.** `UnsubscribeToken` (dominio, JDK puro): `b64url(perfil‖alcance).b64url(HMAC-SHA256)`, alcance = categoría o `ALL`, sin
  vencimiento; clave `logyx.mail.unsubscribe-secret` (`LOGYX_UNSUBSCRIBE_SECRET`, ≥ 32 caracteres, `@Startup`: sin ella el backend no arranca;
  `%dev,test` traen una fija). Cada correo de aviso: «Darte de baja» → `<logyx.web.url>/baja?t=`, `List-Unsubscribe: <…/baja/one-click?t=>` y
  `List-Unsubscribe-Post`. `Reply-To` opcional `logyx.mail.reply-to` (`MAIL_REPLY_TO`). Superficie pública `PublicUnsubscribeResource`
  (`/public/unsubscribe` + `/inspect`, token en el cuerpo, `AuthRateLimits.beforePublicUnsubscribe`).
- **Preferencias de la organización.** El ADMIN escribe (`@OrgAdminOnly` + `AccessDenied.requireOrgAdmin`), todos leen, el operador lee
  `/organizations/{id}/preferences`. Ciudades y tipos son enums del catálogo (la API rechaza otros). Lo que no aplica al tipo se descarta.
- **Términos (2.17).** Invitación: `ResetPasswordRequest.acceptTerms` obligatorio (`AUTH-013`) + versión vigente (`AUTH-012`), fila
  `INVITATION` en la transacción del canje. Versión nueva: `UserInfo.termsAccepted/termsVersion` (lo arma `TermsService.account` en cada
  respuesta con sesión) y `POST /auth/terms/accept` (`UPDATE`, idempotente). No se bloquea la API: la web muestra el diálogo.
- **Avisos de cobro.** `NotificationCategory.PAYMENTS` (correo por omisión sí) y `SettlementNoticeObservers` (textos en español; nunca la
  comisión a la PYME). Para probar sin `settlement`: `systemTx.run(() -> event.fire(new SettlementUpdated(...)))` (ver `SettlementNoticesFlowTest`).
- Tests: `EmailPreferencesFlowTest`, `OrganizationPreferencesFlowTest`, `SettlementNoticesFlowTest`, `TermsUpdateFlowTest`,
  `PreferencesRowLevelSecurityTest`, `EmailDeliveryPolicyTest`, `UnsubscribeTokenTest`, `OrganizationPreferencesPolicyTest`,
  `RouteInterestMatcherTest`, `OrgAccessPolicyTest` (VIEWER: `email-settings` y `auth/terms/accept`).
