# Backend LOGYX — guía para agentes

Quarkus 3.40 · Java 21 · PostgreSQL 16 (Flyway) · JWT RSA · Hibernate ORM Panache.
Contexto general: `../AGENTS.md`, `../docs/architecture/overview.md`, ADR-0001 (RLS), ADR-0002 (hexagonal),
ADR-0004 (eventos de dominio, notificaciones, escrituras del operador) y ADR-0005 (almacenamiento de documentos).

## Correr y testear
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk      # el JDK por defecto de la máquina es 27
./mvnw quarkus:dev                                 # API :8080, Swagger en /q/swagger-ui, Postgres por Dev Services (Docker)
./mvnw quarkus:dev -Dquarkus.http.port=8580        # otro puerto si 8080 está ocupado
./mvnw test                                        # unit (dominio) + @QuarkusTest (flujo HTTP y RLS contra Postgres real)
./mvnw package -DskipTests                         # regenera ../docs/api/openapi.yaml (contrato)
```
Requiere Docker en marcha (Dev Services levanta `postgres:16-alpine` en dev y test).
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

Dependencias entre módulos nuevos (todas por `application`): `negotiation → marketplace`; `documents → shipment`;
`reputation → shipment, documents`; `incident → shipment`; `marketplace → reputation` (Trust Score en ofertas);
`operator → marketplace, shipment, incident, fleet, reputation`. `shipment` no depende de ellos: usa los puertos
`IncidentHoldProvider` (RN11) y `DeliveryEvidenceProvider` (foto de entrega), implementados en
`shipment/infrastructure/integration` (igual que `RequestInfoProvider`).

### Eventos de dominio (ADR-0004)
Records en `<modulo>/application/event/`, publicados con `@Inject Event<X>` + `fire(...)` dentro de la transacción.
Consumidores con `@Observes(during = TransactionPhase.AFTER_SUCCESS)` (solo lo confirmado; un fallo no revierte):
| Evento | Publica | Consumen |
|---|---|---|
| `BidPlaced`, `BidAccepted` | `PlaceBidUseCase`, `AcceptBidUseCase.award` (también asignación manual) | notification |
| `MessagePosted`, `CounterOfferUpdated` | `NegotiationService` | notification |
| `ShipmentStatusChanged` | `TrackStopsUseCase` (IN_TRANSIT, DELIVERED) | notification |
| `IncidentReported`, `IncidentStatusChanged` | `IncidentService` | notification |
| `ReviewSubmitted` | `ReviewService` | notification, `ReputationRecalculator` |
Para un aviso nuevo: agrega el record al módulo que publica, dispara el evento y un método observer en
`NotificationObservers` (los textos van ahí, en español). El evento debe traer los datos que el aviso necesita.

### Almacenamiento de archivos (ADR-0005)
`documents/application/port/out/DocumentStorage` → `infrastructure/storage/LocalFileSystemDocumentStorage`
(`logyx.documents.storage-dir`, por omisión `~/.logyx/documents`, env `LOGYX_DOCUMENTS_DIR`; en tests un temp dir).
Claves generadas por el servidor; formato por firma binaria (`documents/domain/FileFormat`: jpg/png/pdf);
`logyx.documents.max-size-bytes` = 10 MB (200 KB en test). Para S3/MinIO: otro adaptador del mismo puerto.

### Mapa de reglas
`PriceFloorPolicy` (RN03, pricing) · `TariffCalculator` (motor de costos, pricing) · `BidEligibilityPolicy`
(RN02/RN03, marketplace) · `AuctionClosingPolicy` (RN04, marketplace) · `ShipmentRequest.ensureRequesterCanPublish`
(RN01) · `StopSequencePolicy` (RN08, shipment) · `RucValidator` (identity) · `ContactInfoPolicy` (RN07, negotiation)
· `ThreadAccessPolicy` (chat) · `ReviewEligibilityPolicy` (RN06) y `TrustScoreCalculator` (RN09, reputation)
· `ManualAssignmentPolicy` (RN10, marketplace) · `IncidentStatusPolicy` (RN11, incident) · `DeliveryEvidencePolicy`
(shipment) · `DocumentUploadPolicy` (documents). Ver `../docs/domain/business-rules.md`.

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

## RLS y SystemTx (ADR-0001) — no romper
- `shared/tenancy/TenantSessionBinder` observa `@Initialized(TransactionScoped.class)`: en cada transacción con
  usuario autenticado hace `set_config('app.org_id'|'app.org_type', …, true)` y `SET LOCAL ROLE logyx_app`.
  **Todo acceso a datos de usuario va dentro de `@Transactional`** (en `application`), si no, no hay RLS.
- Sin request context / sin JWT (login, registro, scheduler, arranque) la transacción corre como dueño del esquema.
- `SystemTx.call(...)` abre una transacción nueva **sin** RLS. Solo para operaciones de plataforma que cruzan
  organizaciones; cada uso lleva comentario. Usos actuales:
  - `FleetService.addVehicle`: unicidad global de placa.
  - `UserAccounts.createDriverAccount`: unicidad global de email.
  - `FleetService.plateForContract`: la PYME que acepta copia la placa (flota privada del transportista).
  - `PlaceBidUseCase`: conteo total de ofertas para RN04 (bajo RLS un transportista solo ve la suya).
  - `RequestSummaries.bidCounts`: conteo agregado de ofertas para transportistas (sin montos ni competidores).
  - `NotificationPublisher.publish`: la notificación es para otra organización; `notifications` no tiene política
    de INSERT para `logyx_app` (ADR-0004).
  - `ReputationRecalculator.recalculate`: quien califica actualiza la reputación de otra organización;
    `reputation_scores` es de solo lectura para `logyx_app`.
  - `NotificationObservers.operators`: ids de las organizaciones operadoras (corre tras el commit).
- El operador **no** usa `SystemTx`: lee con las políticas `*_operator` (V2, V4–V7) y la asignación manual escribe
  con las políticas de V9 (`bids_operator_insert` exige `assigned_by_operator`, etc.).
- RLS por tabla nueva: `messages`/`counter_offers`/`documents`/`incidents`/`incident_comments` → las dos partes
  (`requester_org_id`, `carrier_org_id` denormalizados) + lectura del operador; `reviews`/`reputation_scores` →
  lectura pública (RF44); `notifications` → solo la propia organización. Historial, evidencia y calificaciones no
  admiten UPDATE/DELETE de usuarios. Casos en `RowLevelSecurityTest`.
- La transacción de `SystemTx` no ve lo no confirmado de la transacción externa (ver comentario en `PlaceBidUseCase`).

## Códigos de error
Formato `LOGYX-<ÁREA>-<NNN>`, respuesta `{ error: { code, message, field }, meta }` (`shared/api/ExceptionMappers`).
| Área | Códigos |
|---|---|
| GEN | `404` no encontrado, `403` regla de permiso de negocio, `500` |
| AUTH | `001` credenciales, `002` email ya registrado, `401` sin token, `403` rol sin permiso |
| VAL | `001` Bean Validation (`field` = propiedad) |
| ORG | `001` RUC inválido, `002` RUC duplicado |
| FLT | `001` placa duplicada, `002` estado "en ruta" lo controla el envío |
| PRC | `001` ciudad fuera del corredor, `002` origen = destino |
| REQ | `002` cancelar solo publicadas |
| AUC | `001` subasta no abierta, `002` subasta cerrada |
| BID | `001` bajo el piso (RN03), `003` ya ofertó, `004` no admite aceptar |
| CAP | `001` sin capacidad (RN02), `002` vehículo no disponible |
| SHP | `001` reasignar conductor solo antes del recojo, `002` (404) el conductor no tiene envío activo |
| TRK | `001` orden de paradas (RN08), `002` ya llegó, `003` falta llegada, `004` envío cerrado, `005` incidencia activa (RN11), `006` entrega sin foto |
| NEG | `001` datos de contacto (RN07, `body`/`message`), `002` hilo cerrado, `003` mismo monto, `004` contra-oferta pendiente, `005` oferta no negociable, `007` ya respondida |
| DOC | `001` tipo no permitido, `002` muy grande, `003` vacío (`file`), `004` falta parada de entrega, `005` sin llegada (`stopId`), `006` envío cancelado, `500` fallo de almacenamiento |
| REV | `001` no entregado, `002` sin foto (RN06), `003` ya calificó |
| INC | `001` ya hay activa, `002` transición inválida (`status`), `003` faltan notas (`resolutionNotes`), `004` cerrada, `005` envío cancelado |
| OPS | `001` SLA no vencido (RN10), `002` no es transportista (`carrierOrgId`) |
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

Datos: 8 solicitudes en el corredor (Lima, Arequipa, Juliaca, Puno, Cusco, Tacna), 6 ofertas; la de Lima → Juliaca
está adjudicada a Sur Andino, con `driver@logyx.pe` asignado y el recojo completado (envío `IN_TRANSIT`, ASA-101
`IN_ROUTE`). Fase 2: envío Arequipa → Juliaca **entregado** con foto, firma y guía de remisión, calificado en ambas
direcciones (Trust Score de Sur Andino 77.8) y con una incidencia `CARGO_DAMAGE` abierta comentada; chat en
Arequipa → Puno (lácteos) y pregunta de Titicaca en Lima → Cusco; contra-oferta aceptada (Tacna → Arequipa) y otra
pendiente (quinua Juliaca → Lima); Puno → Tacna y Lima → Arequipa con `created_at` retrocedido 30/27 h (alertas
SLA asignables por el operador); notificaciones generadas por todos esos eventos. Los archivos van a
`logyx.documents.storage-dir`. En dev `logyx.auction.duration=PT72H` para que las subastas sigan abiertas. Dev Services recrea la BD
en cada arranque, así que el seed corre cada vez.
