# Backend LOGYX — guía para agentes

Quarkus 3.40 · Java 21 · PostgreSQL 16 (Flyway) · JWT RSA · Hibernate ORM Panache.
Contexto general: `../AGENTS.md`, `../docs/architecture/overview.md`, ADR-0001 (RLS) y ADR-0002 (hexagonal).

## Correr y testear
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk      # el JDK por defecto de la máquina es 27
./mvnw quarkus:dev                                 # API :8080, Swagger en /q/swagger-ui, Postgres por Dev Services (Docker)
./mvnw test                                        # unit (dominio) + @QuarkusTest (flujo HTTP y RLS contra Postgres real)
./mvnw package -DskipTests                         # regenera ../docs/api/openapi.yaml (contrato)
```
Requiere Docker en marcha (Dev Services levanta `postgres:16-alpine` en dev y test).

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

### Mapa de reglas
`PriceFloorPolicy` (RN03, pricing) · `TariffCalculator` (motor de costos, pricing) · `BidEligibilityPolicy`
(RN02/RN03, marketplace) · `AuctionClosingPolicy` (RN04, marketplace) · `ShipmentRequest.ensureRequesterCanPublish`
(RN01) · `StopSequencePolicy` (RN08, shipment) · `RucValidator` (identity). Ver `../docs/domain/business-rules.md`.

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
| SHP | `001` reasignar conductor solo antes del recojo |
| TRK | `001` orden de paradas (RN08), `002` ya llegó, `003` falta llegada, `004` envío cerrado |
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

Datos: 8 solicitudes en el corredor (Lima, Arequipa, Juliaca, Puno, Cusco, Tacna), 6 ofertas; la de Lima → Juliaca
está adjudicada a Sur Andino, con `driver@logyx.pe` asignado y el recojo completado (envío `IN_TRANSIT`, ASA-101
`IN_ROUTE`). En dev `logyx.auction.duration=PT72H` para que las subastas sigan abiertas. Dev Services recrea la BD
en cada arranque, así que el seed corre cada vez.
