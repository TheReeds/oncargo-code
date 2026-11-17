# Changelog del contrato API

Fuente de verdad: `docs/api/openapi.yaml` (generado por el backend). Todos los cambios listados son **aditivos y
retrocompatibles** (no se renombró ni eliminó ningún campo, path o schema), salvo el cambio de comportamiento
señalado con ⚠️ en la fase 2. Los clientes deben regenerar/actualizar
sus DTOs; los campos nuevos de respuesta son siempre seguros de ignorar.

## 2026-11-16 — Rutas y distancias por carretera (ADR-0008)

`scripts/check-contract.sh`: **compatible** (path y schemas nuevos). Sin migración.

- **Nuevo `GET /api/v1/routes?stops=LIMA,AREQUIPA,PUNO[&optimize=true]`** (cualquier usuario autenticado) →
  `RouteView { stops[], distanceKm, durationMin, estimated, legs[RouteLegView{fromCity, toCity, distanceKm, durationMin,
  estimated}], polyline, polylinePrecision, attribution }`. La `polyline` usa el algoritmo de Google con precisión 5
  (`polylinePrecision`) y la decodifica `web/src/lib/routing`. `attribution` (© OpenStreetMap, ODbL) **debe mostrarse**
  junto al mapa. Con `optimize=true` se reordenan las paradas intermedias para el menor recorrido; la primera y la
  última no se mueven. `durationMin` es la del perfil de auto (orientativa para camión). `estimated=true` indica un
  tramo sin dato vial, calculado como línea recta × 1.35.
  Errores: `400 LOGYX-RTE-001` (menos de 2 paradas, `field=stops`), `400 LOGYX-RTE-002` (más de 25 paradas,
  `field=stops`), `400 LOGYX-PRC-001` (ciudad fuera del corredor).
- ⚠️ **Cambio de comportamiento: la distancia es por carretera.** `distanceKm` de `POST /pricing/calculate` y, en
  consecuencia, `suggestedPrice` y `priceFloor` (RN03) de las **solicitudes nuevas** salen de la ruta más rápida
  precalculada con OSRM sobre OpenStreetMap, no de la línea recta × 1.35. Diferencias típicas: Lima → Cusco +42 %,
  Cusco → Abancay +55 %, Lima → Ica −17 % (tabla en ADR-0008). Las solicitudes ya publicadas conservan sus valores.
  Cargas de retorno: `distanceKm` y `matchScore` también usan km por carretera (Juliaca → Puno 42.9 en vez de 44).
  Smart Load: misma proximidad por carretera, y el orden de `route` (primero los recojos, luego las entregas) es el
  de **menos km**, con los recojos en orden de fecha.

## 2026-11-16 — Huecos P1 de la revisión de flujos (V13)

Origen: `docs/reviews/2026-11-16-flow-gaps.md` (F04–F14). `scripts/check-contract.sh`: **compatible** en esquema
(solo paths, schemas, campos y valores de enum nuevos). Hay **cambios de comportamiento** ⚠️, justificados abajo: cierran
reglas del SRS que el backend no hacía cumplir. Migración `V13__flow_gaps.sql`.
Los enums anidados `Status2` (solicitud) y `Status3` (oferta) quedan fijados por configuración
(`mp.openapi.schema.*`): agregar `WITHDRAWN` a la oferta los renumeraba.

### ⚠️ Cambios de comportamiento (justificación)
1. **RN01 / RF08 (F04)** — `rucVerified` ya no significa "pasó el dígito verificador" sino **"verificada por el
   operador LOGYX"** (sello "Verificado"). Antes RN01 no bloqueaba a nadie, porque un RUC inválido ya se rechazaba al
   registrarse. Las organizaciones nuevas quedan `PENDING`, y en prod/test (`logyx.verification.required=true`) no
   pueden publicar solicitudes, ofertar, reservar viajes ni publicar viajes: **403 `LOGYX-ORG-003`**. Una organización
   suspendida no puede hacerlo nunca, ni en dev: **403 `LOGYX-ORG-004`** (también al aceptar una oferta). V13 deja
   `PENDING` a las organizaciones existentes que no son operadoras, porque el sello anterior no verificaba nada. En dev
   la verificación es opcional (`LOGYX_VERIFICATION_REQUIRED`, por omisión `false`) y las cuentas demo quedan verificadas.
2. **RF55 (F13) y F27** — `POST /incidents/{id}/status` a `RESOLVED`, o a `CLOSED` desde `OPEN`/`IN_REVIEW`, exige
   `resolutionNotes` (`LOGYX-INC-003`, antes solo RESOLVED) y **`shipmentOutcome`** (`LOGYX-INC-006`). Sin el estado
   final del envío no había forma de dar por perdida una carga.
3. **F14 / RN06** — `DELIVERY_PHOTO` y `SIGNATURE` solo las sube el **conductor asignado** (403 `LOGYX-DOC-007`, antes
   también la oficina del transportista; la PYME recibía `LOGYX-GEN-403`), y solo entre la llegada y el cierre de la
   parada de entrega. Con la parada completada o el envío entregado se responde **409 `LOGYX-DOC-008`**. Así la evidencia
   que habilita calificar ya no se puede fabricar después.
4. **RN07 (F10)** — Los textos libres visibles a la contraparte o al público rechazan teléfonos, emails, URLs y
   `@usuario` con **400 `LOGYX-NEG-001`** (mismo código del chat, `field` = campo): ver la lista abajo.
5. **RN02 (F09)** — Ofertar (y la asignación manual) valida el **tipo de vehículo contra el tipo de carga**
   (`LOGYX-CAP-003`) y el **volumen** (`LOGYX-CAP-004`), además de los kg. Aceptar una oferta revalida el tipo.
   La matriz está en `docs/domain/business-rules.md`. Los viajes la usan también (`LOGYX-TRP-010`), y es más estricta
   que la regla anterior: por ejemplo, la cisterna ya no lleva carga general y la carga sobredimensionada solo va en
   plataforma.
6. **F11** — Un vehículo o un conductor no pueden estar en dos envíos activos que se solapen: **409 `LOGYX-CAP-005`**
   (vehículo; al ofertar, aceptar, asignar manualmente o cambiar de vehículo) y **409 `LOGYX-SHP-004`** (conductor). La
   excepción son los envíos del mismo viaje o del mismo plan Smart Load (`loadGroupId`).
7. **Reasignar conductor en ruta (F06)** — Antes era `LOGYX-SHP-001`. Ahora se permite con `reason` obligatorio
   (`LOGYX-SHP-006`). `LOGYX-SHP-001` queda solo para envíos cerrados.
8. **Smart Load (F12, RF32)** — `POST /smart-loads/{id}/accept` ahora **oferta en lote**: crea una oferta por carga
   con el vehículo del plan. El aviso a las PYMEs cambia de "Tu carga fue agrupada" a "Oferta consolidada por tu carga".
9. **RN10 (F07)** — El operador puede asignar manualmente en cuanto la subasta **cierra sin ofertas**, sin esperar
   24 h. Las ofertas retiradas no cuentan.

### Paths nuevos
| Método y path | Rol | Qué hace |
|---|---|---|
| `GET /operator/organizations?status=&type=&page=&size=` | OPERATOR | Bandeja de verificación (`OrganizationReviewRow[]`, paginada; `status` por omisión `PENDING`, `ALL` = todos; `type` `PYME`/`CARRIER`) |
| `POST /operator/organizations/{id}/verify` | OPERATOR | Body opcional `{note}`. Verifica (o reactiva una rechazada o suspendida) |
| `POST /operator/organizations/{id}/reject` | OPERATOR | `{reason}` obligatorio. Rechaza (`LOGYX-ORG-005` si es operadora) |
| `POST /operator/organizations/{id}/suspend` | OPERATOR | `{reason}` obligatorio. Suspende |
| `POST /bids/{id}/withdraw` | CARRIER | Retira la oferta propia `PENDING` con la subasta abierta (`LOGYX-BID-005`). No puede volver a ofertar (`LOGYX-BID-003`) |
| `POST /shipment-requests/{id}/reopen` | PYME | Body opcional `{pickupDate}`. Reabre una subasta cerrada por tiempo (`LOGYX-AUC-003`); `LOGYX-REQ-003` si la fecha ya pasó |
| `POST /shipment-requests/{id}/republish` | PYME | 201. Body opcional `{pickupDate}`. Nueva solicitud a partir de una `CANCELLED` (`LOGYX-REQ-004`) |
| `POST /shipments/{id}/cancel` | PYME, CARRIER, OPERATOR | `{reason}` obligatorio. Las partes cancelan antes del recojo (`LOGYX-SHP-003`); el operador también en tránsito. Con una incidencia activa, las partes reciben `LOGYX-TRK-005` |
| `PUT /shipments/{id}/vehicle` | CARRIER | `{vehicleId, reason?}`. Cambia el vehículo (RN02 `CAP-001..004`, `CAP-005`, `SHP-006` en ruta, `SHP-007` mismo vehículo) |

### Endpoints ampliados
- `POST /bids/{id}/counter-offers` también para **CARRIER** (RF26). Hay una sola pendiente por oferta: responder con
  otra reemplaza la de la otra parte, que queda `REJECTED`. Si ya hay una pendiente tuya: `LOGYX-NEG-004`.
  `POST /counter-offers/{id}/accept|reject` también para **PYME** (responde quien la recibió; si no, 403).
  Las pendientes pasan a `EXPIRED` al adjudicar la solicitud o retirar la oferta (F40).
- `PUT /shipments/{id}/driver`: `reason` opcional, obligatorio en ruta.
- `POST /shipment-requests/{id}/cancel`: body opcional `{reason}`. Se avisa a los transportistas que ofertaron (F16).
- `POST /incidents/{id}/status`: `shipmentOutcome` (`RESUME|DELIVERED|CANCELLED`). Las combinaciones válidas
  dependen del estado del envío:
  - `PENDING`: `RESUME` o `CANCELLED`.
  - `IN_TRANSIT`: los tres.
  - `DELIVERED`: solo `DELIVERED`.
  - `CANCELLED`: solo `CANCELLED`.

  `CANCELLED` cancela el envío y la solicitud y libera la flota. `DELIVERED` cierra las paradas.
- `POST /smart-loads/{id}/accept`: body opcional `{items:[{requestId, amount}], notes}`. Es todo o nada
  (`LOGYX-TRP-011`); `LOGYX-TRP-012` si el `requestId` no pertenece al plan.

### Campos nuevos
- `UserInfo`: `rucValid`, `verificationStatus` (`PENDING|VERIFIED|REJECTED|SUSPENDED`) y `verificationNote`.
  `PublicOrganization`: `rucValid` y `verificationStatus`.
- `RequestView`: `compatibleVehicleTypes` (matriz RN02), `cancelReason` y `republishedFrom`.
- `ShipmentView`: `pickupDate`, `vehicleId`, `loadGroupId`, `cancellationReason`, `cancelledAt` y `cancelledByOrgId`.
- `CounterOfferView.proposedByCarrier`, `IncidentView.shipmentOutcome`.
- `SmartLoadView.bids` (`SmartLoadBidView[]`): solo viene en la respuesta de aceptar.
- Enums: `Status3` (oferta) suma `WITHDRAWN`. `NotificationType` suma `AUCTION_CLOSED`, `REQUEST_CANCELLED`,
  `SHIPMENT_CANCELLED`, `BID_WITHDRAWN` y `ORGANIZATION_VERIFICATION`. `NotificationTarget`/`relatedType` suma
  `ORGANIZATION`. `IncidentShipmentOutcome` y `VerificationStatus` son nuevos.

### RN07: campos que validan (`field` del error)
`cargoDescription`, `originAddress`, `destinationAddress` (solicitud), `notes` (oferta, viaje),
`cargoDescription`/`pickupAddress`/`deliveryAddress` (reserva), `reason` (rechazo de reserva, cancelación de solicitud o
envío, reasignación), `description` (incidencia), `body` (comentario de incidencia de las partes), `comment` (reseña),
`legalName` (registro).

### Avisos nuevos (outbox → notificación + `notification.created`)
| Evento | Destinatario | Tipo |
|---|---|---|
| Subasta cerrada por tiempo | PYME. Si cerró sin ofertas, también los operadores | `AUCTION_CLOSED` |
| Solicitud cancelada | Transportistas con oferta pendiente | `REQUEST_CANCELLED` |
| Envío cancelado (motivo en el cuerpo) | La contraparte; ambas partes si cancela el operador | `SHIPMENT_CANCELLED` |
| Cambio de vehículo o de conductor | PYME | `SHIPMENT_STATUS_CHANGE` |
| Oferta retirada | PYME | `BID_WITHDRAWN` |
| Verificación decidida | La organización | `ORGANIZATION_VERIFICATION` (`relatedType=ORGANIZATION`) |
| Contra-oferta del transportista o respuesta de la PYME | La otra parte | `COUNTER_OFFER` |

Tiempo real: la cancelación emite `shipment.status_changed` con `status=CANCELLED` a las partes y al conductor. Los avisos
nuevos llegan por `notification.created`. No hay tipos SSE nuevos.

### Códigos nuevos
`LOGYX-ORG-003` (403, sin verificar), `ORG-004` (403, suspendida), `ORG-005` (operadora), `ORG-006` (falta el motivo),
`BID-005` (no se puede retirar), `CAP-003` (tipo de vehículo), `CAP-004` (volumen), `CAP-005` (vehículo comprometido),
`SHP-004` (conductor comprometido), `SHP-006` (falta el motivo), `SHP-007` (mismo vehículo), `AUC-003` (no se puede
reabrir), `REQ-003` (fecha pasada), `REQ-004` (solo se republica una cancelada), `INC-006` (estado final del envío),
`DOC-007` (no es el conductor asignado), `DOC-008` (evidencia cerrada), `OPS-003` (transportista no verificado o
suspendido), `TRP-012` (monto fuera del plan).

## 2026-11-11 — Listados globales del operador y orden de "mis viajes"

**Aditivo** (`scripts/check-contract.sh` compatible).
- `GET /api/v1/operator/requests` (solo OPERATOR; PYME/CARRIER/DRIVER reciben 403): listado global paginado de solicitudes,
  más recientes primero. Respuesta `{ data: OperatorRequestRow[], meta: { page, size, total } }`. Query: `status`
  (`PUBLISHED|MATCHED|CANCELLED`), `originCity`, `destinationCity` (códigos de ciudad), `fromDate`/`toDate`
  (`yyyy-MM-dd`, inclusive, sobre la fecha de creación en hora de Lima), `page` (0-based) y `size` (1-100, por omisión 20).
- `GET /api/v1/operator/shipments` (solo OPERATOR): igual para envíos, `OperatorShipmentRow`; `status`
  (`PENDING|IN_TRANSIT|DELIVERED|CANCELLED`); las ciudades son las de la solicitud del envío; las fechas, las de creación.
  Los valores inválidos dan 400 `LOGYX-VAL-001` (`field` = parámetro) o `LOGYX-PRC-001` (ciudad fuera del corredor).
  El detalle sigue en `GET /shipments/{id}` y `GET /shipment-requests/{id}`.
- `GET /api/v1/trips/mine` (CARRIER) ya aceptaba `page`/`size` (sin ellos devuelve todos); ahora suma `sort`:
  `departure` (por omisión: salida más próxima primero), `departure_desc` o `recent` (publicados más recientemente
  primero). Sin parámetros la respuesta es la misma de antes; con `page`/`size` suma `meta.page|size|total`.

## 2026-11-10 — Fase 6: tiempo real por SSE (ADR-0007)

**Aditivo**: path nuevo `GET /api/v1/events/stream` y schema `RealtimeStreamEvent` (`scripts/check-contract.sh` en verde).
Código nuevo `LOGYX-RTM-001` (HTTP 429): más de 5 conexiones abiertas del mismo usuario.

### Protocolo
- `GET /api/v1/events/stream` con `Authorization: Bearer <jwt>` y `Accept: text/event-stream`. Roles: PYME, CARRIER,
  DRIVER, OPERATOR. Cada mensaje SSE: `id: <seq>` + `data: {"type","seq","occurredAt","data":{...}}` (sin `event:`:
  todo llega por `onmessage`). Ignora los `type` que no conozcas.
- Al abrir llega `stream.ready` (`data.replayed` = eventos reenviados; su `id` es el último seq visible). Si se pidió
  reanudar y hay más de 500 eventos pendientes llega `stream.resync`: recarga todo lo que muestras.
- Reanudación: cabecera `Last-Event-ID` (EventSource la envía sola) o `?lastEventId=<seq>` si reconectas a mano. Los
  eventos se conservan 24 h.
- Latido: comentario `: ping` cada ~20 s. `retry: 3000` sugerido.
- Al vencer el JWT llega `stream.expired` (`data.reason = TOKEN_EXPIRED`) y el servidor cierra: reconecta con un token
  nuevo (o vuelve a iniciar sesión).
- **Payload mínimo**: ids, estados y contadores; nunca montos, nombres, rutas ni textos. El cliente vuelve a pedir por REST
  lo que necesita (con RLS). Entrega al mejor esfuerzo: refresca al (re)conectar y conserva un polling lento de respaldo.

### Tipos y destinatarios
"Partes" = PYME dueña de la solicitud y transportista; "conductor" = el usuario conductor asignado al envío (el conductor
**solo** recibe eventos de sus envíos, nunca chat, avisos ni subastas de su empresa).

| `type` | Para | `data` |
|---|---|---|
| `bid.placed` | PYME dueña y transportista que ofertó | `requestId`, `bidId`, `bidCount`, `auctionClosed` |
| `auction.updated` | todos los transportistas | `requestId`, `bidCount?`, `auctionClosed` |
| `request.matched` | PYME dueña | `requestId`, `bidId`, `shipmentId`, `byOperator` |
| `bid.accepted` | transportista ganador | `requestId`, `bidId`, `shipmentId`, `byOperator` |
| `bid.rejected` | cada transportista no elegido | `requestId`, `bidId` |
| `counter_offer.updated` | partes | `counterOfferId`, `bidId`, `requestId`, `status` |
| `message.posted` | las dos partes del hilo | `requestId`, `carrierOrgId` (= hilo), `messageId`, `senderOrgId` |
| `shipment.status_changed` | partes + conductor | `shipmentId`, `status` |
| `shipment.stop_updated` | partes + conductor | `shipmentId`, `stopId`, `stopStatus`, `status` |
| `shipment.driver_assigned` | partes + conductor nuevo (`assigned=true`) y anterior (`false`) | `shipmentId`, `assigned` |
| `incident.reported` / `incident.updated` | partes + conductor + operadores | `incidentId`, `shipmentId`, `status` |
| `notification.created` | organización destinataria (no conductores) | `notificationId`, `notificationType`, `relatedType`, `relatedId`, `unreadCount` |
| `review.submitted` | organización calificada | `reviewId`, `shipmentId` |
| `trip_booking.updated` | PYME y transportista | `bookingId`, `tripId`, `status` (PENDING/CONFIRMED/REJECTED/CANCELLED), `shipmentId?` |
| `trip.cancelled` | transportista y cada PYME con reserva | `tripId`, `bookingId?` |
| `smart_load.suggested` | transportista | `suggestionId`, `itemCount` |
| `smart_load.accepted` | transportista (`suggestionId`) y cada PYME agrupada (`requestId`) | |
| `stream.ready` / `stream.resync` / `stream.expired` | control | `replayed` / `replayed` / `reason` |

Eventos de dominio nuevos (solo los consume el tiempo real): `ShipmentStopUpdated`, `DriverAssigned`; `BidPlaced` suma
`bidCount` y `auctionClosed` (nulos en eventos encolados antes).

## 2026-11-10 — Fase 5: preparación para producción

`openapi.yaml`/`openapi.json` **sin cambios** (`scripts/check-contract.sh` en verde). Cambios de comportamiento:
- **Nuevo código `LOGYX-AUTH-429`** (HTTP 429, cabecera `Retry-After` en segundos) en `POST /api/v1/auth/login` y
  `POST /api/v1/auth/register` por rate limiting: login 50 intentos / 15 min por IP, bloqueo de un email tras 10 fallos
  en 15 min (incluso con la contraseña correcta), registro 10 / 15 min por IP. Los clientes deben mostrar el `message`.
- Las notificaciones se crean tras el commit vía outbox (mismo momento que antes; si el despacho falla se reintentan).
- En prod: CORS desactivado, Swagger UI no incluido, `Cache-Control: no-store` y HSTS en las respuestas de la API.

## 2026-11-10 — Fase 4: viajes publicados, reservas, cargas de retorno y Smart Load Planner

Todo es **aditivo** (paths y schemas nuevos; campos nuevos opcionales en `DashboardSummaryView`; valores nuevos en los
enums `NotificationType` y `NotificationTarget`). No cambia ningún código de error ni comportamiento existente.
Los clientes deben **tolerar valores de enum desconocidos** en `NotificationType`/`NotificationTarget`. Enums nuevos con
nombre propio: `TripStatus`, `BookingStatus`, `TripPricingMode`, `SmartLoadStatus`, `StopType`. Todos los endpoints
usan `Authorization: Bearer <jwt>` y responden `{ data, meta }` / `{ error: { code, message, field }, meta }`.
Fechas-hora en ISO-8601 (`departureAt` acepta `Z` u offset, p. ej. `2026-11-14T08:00:00-05:00`).

### 1. Viajes publicados (supply-side, RN12)
El transportista publica un viaje con vehículo propio, capacidad ofrecida y tarifa. RN12: `capacityKg` ≤ capacidad del
vehículo (`LOGYX-TRP-001`, `field=capacityKg`). Las reservas **confirmadas** descuentan `remainingKg`.

| Método | Path | Rol | Notas |
|---|---|---|---|
| POST | `/api/v1/trips` | CARRIER | `CreateTripRequest` → 201 `TripView` |
| GET | `/api/v1/trips/mine?page=&size=` | CARRIER | sin `page`/`size` devuelve todos; ordenados por salida |
| GET | `/api/v1/trips/{id}` | PYME, CARRIER (dueño), OPERATOR | la PYME ve el viaje si está abierto o si reservó en él; 404 si no |
| POST | `/api/v1/trips/{id}/close` | CARRIER (dueño) | cierre manual; rechaza las reservas pendientes |
| POST | `/api/v1/trips/{id}/cancel` | CARRIER (dueño) | cancela reservas y envíos que no salieron; `LOGYX-TRP-007` si alguno ya salió |
| GET | `/api/v1/trips/{id}/bookings` | CARRIER (dueño) | `TripBookingView[]` |
| GET | `/api/v1/trips?originCity=&destinationCity=&fromDate=&toDate=&minWeightKg=&page=&size=` | PYME | solo OPEN/FILLING, salida futura y capacidad restante ≥ `minWeightKg`; `fromDate`/`toDate` = día de salida (Lima), `yyyy-MM-dd`; paginado (`meta.page|size|total`) |

```json
// POST /trips
{ "originCity": "LIMA", "destinationCity": "JULIACA", "departureAt": "2026-11-14T08:00:00-05:00",
  "vehicleId": "…", "capacityKg": 12000, "pricingMode": "PER_KG", "rate": 0.48, "notes": "Salida 6 a. m." }
// → 201 TripView
{ "data": { "id": "…", "carrierOrgId": "…", "carrierName": "Transportes Sur Andino SRL", "carrierTrustScore": 77.8,
            "vehicleId": "…", "vehicleType": "TRUCK_LARGE", "vehiclePlate": "ASA-202",
            "originCity": "LIMA", "destinationCity": "JULIACA", "departureAt": "2026-11-14T13:00:00Z",
            "capacityKg": 12000, "confirmedWeightKg": 0, "remainingKg": 12000,
            "pricingMode": "PER_KG", "rate": 0.48, "notes": "Salida 6 a. m.", "status": "OPEN",
            "createdAt": "…", "pendingBookings": 0, "confirmedBookings": 0 }, "meta": { … } }
```
- `TripView.vehicleId`, `pendingBookings` y `confirmedBookings` solo vienen para el transportista dueño (la PYME recibe `null`;
  la flota es privada: ve `vehicleType` y `vehiclePlate`).
- `TripStatus`: `OPEN` → `FILLING` (primera reserva **confirmada**) → `CLOSED` (se llena, cierre manual o ya salió) →
  `COMPLETED` (24 h después de la salida); `CANCELLED` desde OPEN/FILLING/CLOSED. Si se cancela la única reserva confirmada,
  `FILLING` vuelve a `OPEN`.
- `TripPricingMode`: `PER_KG` (`rate` = S/ por kg) o `PER_TRIP` (`rate` = tarifa de todo el tramo con la capacidad completa).
  Precio de una reserva: `PER_KG` → `rate × kg`; `PER_TRIP` → `rate × kg / capacityKg`; 2 decimales, mínimo S/ 1.00.
- Un job interno cierra los viajes que ya salieron (las reservas pendientes se rechazan) y completa los de hace > 24 h.

### 2. Reservas de espacio
| Método | Path | Rol | Notas |
|---|---|---|---|
| POST | `/api/v1/trips/{id}/bookings` | PYME | `CreateTripBookingRequest` → 201 `TripBookingView` (PENDING) |
| GET | `/api/v1/trip-bookings/mine?status=` | PYME | todas las suyas, recientes primero |
| POST | `/api/v1/trip-bookings/{id}/cancel` | PYME (dueña) | PENDING o CONFIRMED (si el envío aún no salió, si no `LOGYX-SHP-003`) |
| POST | `/api/v1/trip-bookings/{id}/confirm` | CARRIER (dueño del viaje) | genera el envío; `TripBookingView` con `requestId` y `shipmentId` |
| POST | `/api/v1/trip-bookings/{id}/reject` | CARRIER | body opcional `{ "reason": "…" }` (≤ 300) |

```json
// POST /trips/{id}/bookings
{ "weightKg": 2500, "cargoDescription": "Mercadería de bazar en cajas", "cargoType": "GENERAL",
  "pickupAddress": "Av. Argentina 1234, Callao", "deliveryAddress": "Jr. San Román 456, Juliaca" }
// → 201 TripBookingView
{ "data": { "id": "…", "tripId": "…", "status": "PENDING", "requesterOrgId": "…", "requesterName": "Comercial Altiplano SAC",
            "carrierOrgId": "…", "carrierName": "Transportes Sur Andino SRL",
            "originCity": "LIMA", "destinationCity": "JULIACA", "departureAt": "…",
            "weightKg": 2500, "cargoType": "GENERAL", "cargoDescription": "…", "pickupAddress": "…", "deliveryAddress": "…",
            "price": 1200.00, "decisionNote": null, "requestId": null, "shipmentId": null,
            "createdAt": "…", "decidedAt": null }, "meta": { … } }
```
- Al reservar: el peso debe caber en la capacidad restante (`LOGYX-TRP-004`, `field=weightKg`) y el tipo de carga en el
  vehículo (`LOGYX-TRP-010`: perecederos solo en refrigerados y viceversa). Las reservas pendientes **no** descuentan
  capacidad; sí lo hace confirmar (se vuelve a validar al confirmar).
- **Al confirmar se genera un envío** reutilizando shipment: `shipments` exige `request_id` y `bid_id`, así que se crean una
  solicitud ya adjudicada (`RequestView.status = MATCHED`, visible en `GET /shipment-requests/mine` de la PYME), su subasta
  cerrada y una oferta aceptada por el precio de la reserva. El envío tiene paradas recojo (`pickupAddress`) y entrega
  (`deliveryAddress`) y sigue el flujo normal (el transportista asigna conductor, evidencia, calificación, incidencias).
- Si el viaje se llena al confirmar → `CLOSED` y las reservas pendientes pasan a `REJECTED` (con `decisionNote`).
- Cancelar una reserva confirmada cancela su envío (`ShipmentView.status = CANCELLED`) y devuelve la capacidad.
- `BookingStatus`: `PENDING`, `CONFIRMED`, `REJECTED`, `CANCELLED`.

### 3. Cargas de retorno (RF61–RF63)
`GET /api/v1/return-loads` (CARRIER) → `ReturnLoadView[]` (máx. 20, mejor puntaje primero). Para cada envío del transportista
`IN_TRANSIT` o `DELIVERED` hace ≤ 3 días, sugiere solicitudes `PUBLISHED` con subasta abierta que salen de la ciudad de destino
del envío o de una cercana (≤ 50 km por carretera; en el corredor solo Juliaca–Puno = 44 km), recojo entre la disponibilidad del
vehículo y 7 días después, peso ≤ capacidad del vehículo y carga compatible con el vehículo. `matchScore = 100 − 0.6·km − 4·díasEspera`.
Para ofertar (RF63) se usa `POST /shipment-requests/{requestId}/bids` con el `vehicleId` sugerido.
```json
{ "data": [ { "requestId": "…", "requesterOrgId": "…", "requesterName": "Comercial Altiplano SAC",
              "shipmentId": "…", "vehicleId": "…", "vehiclePlate": "ASA-202", "fromCity": "JULIACA", "shipmentDelivered": true,
              "originCity": "JULIACA", "originAddress": "…", "destinationCity": "AREQUIPA", "destinationAddress": "…",
              "cargoType": "GENERAL", "cargoDescription": "Lana de alpaca en fardos", "weightKg": 3500,
              "pickupDate": "2026-11-11", "suggestedPrice": 913.11, "priceFloor": 684.83,
              "distanceKm": 0.0, "waitDays": 1, "matchScore": 96, "alreadyBid": false } ], "meta": { … } }
```

### 4. Smart Load Planner básico (OE07, RF30–RF33)
| Método | Path | Rol | Notas |
|---|---|---|---|
| POST | `/api/v1/smart-loads/generate` | CARRIER | genera ahora; 201 con **solo las sugerencias nuevas** (`[]` si no hay) |
| GET | `/api/v1/smart-loads?status=` | CARRIER | `PENDING`, `ACCEPTED`, `REJECTED`, `EXPIRED`; recientes primero |
| POST | `/api/v1/smart-loads/{id}/accept` | CARRIER | marca ACCEPTED y **avisa a las PYMEs** agrupadas; **no adjudica ni crea ofertas** |
| POST | `/api/v1/smart-loads/{id}/reject` | CARRIER | |
Combina 2–4 solicitudes `PUBLISHED` para un vehículo `AVAILABLE` del transportista: orígenes ≤ 50 km, destinos ≤ 80 km,
recojos ±1 día, peso combinado ≤ capacidad y ocupación ≥ 60 %. Una por vehículo (la de mayor ingreso). Vigencia 4 h
(`expiresAt`); vencida: `LOGYX-TRP-009`. Un job periódico (15 min) genera para todos los transportistas.
```json
{ "data": [ { "id": "…", "vehicleId": "…", "vehicleType": "TRUCK_LARGE", "vehiclePlate": "ASA-202", "capacityKg": 20000,
              "totalWeightKg": 13500, "combinedIncome": 14772.90, "occupancyPct": 67.50, "status": "PENDING",
              "createdAt": "…", "expiresAt": "…", "decidedAt": null,
              "items": [ { "requestId": "…", "originCity": "LIMA", "destinationCity": "JULIACA", "weightKg": 7500,
                           "income": 8211.40, "pickupDate": "2026-11-13", "pickupOrder": 0, "deliveryOrder": 0 } ],
              "route": [ { "type": "PICKUP", "city": "LIMA", "requestId": "…" }, { "type": "DELIVERY", "city": "JULIACA", "requestId": "…" } ] } ],
  "meta": { … } }
```
`income` por ítem y `combinedIncome` = suma de los **precios sugeridos** de las solicitudes. `route` = recojos por orden y luego entregas.

### 5. Dashboard: campos nuevos en `GET /dashboard/summary` (`DashboardSummaryView`)
Siguen omitiéndose los campos que no aplican al rol.
| Rol | Campo | Descripción |
|---|---|---|
| CARRIER | `openTrips` | viajes publicados OPEN/FILLING |
| CARRIER | `pendingTripBookings` | reservas por confirmar |
| CARRIER | `pendingSmartLoads` | sugerencias Smart Load PENDING vigentes |
| CARRIER | `returnLoads` | cantidad de cargas de retorno |
| CARRIER | `topReturnLoads` | hasta 3 `ReturnLoadHintView { requestId, originCity, destinationCity, weightKg, pickupDate, suggestedPrice, distanceKm, matchScore }` |
| PYME | `activeTripBookings` | reservas PENDING o CONFIRMED |

### 6. Notificaciones: valores nuevos
`NotificationType`: `TRIP_BOOKING_REQUESTED` (al transportista), `TRIP_BOOKING_CONFIRMED` (PYME; `relatedType=SHIPMENT`),
`TRIP_BOOKING_REJECTED`, `TRIP_BOOKING_CANCELLED` (al transportista), `TRIP_CANCELLED` (PYME), `SMART_LOAD_SUGGESTED`
(transportista; `relatedType=SMART_LOAD`), `SMART_LOAD_ACCEPTED` (PYME; `relatedType=REQUEST`).
`NotificationTarget`: `TRIP` (el viaje; `relatedId` = id del viaje), `SMART_LOAD` (id de la sugerencia).

### 7. Códigos de error nuevos
| Código | HTTP | Cuándo |
|---|---|---|
| `LOGYX-TRP-001` | 400 | Capacidad publicada mayor a la del vehículo (RN12), `field=capacityKg` |
| `LOGYX-TRP-002` | 400 | Salida no futura, `field=departureAt` |
| `LOGYX-TRP-003` | 409 | El viaje ya no admite reservas (no está OPEN/FILLING o ya salió) |
| `LOGYX-TRP-004` | 409 | El peso excede la capacidad restante, `field=weightKg` |
| `LOGYX-TRP-005` | 409 | Transición de estado de viaje inválida (p. ej. cerrar uno cancelado), `field=status` |
| `LOGYX-TRP-006` | 409 | La reserva ya fue resuelta |
| `LOGYX-TRP-007` | 409 | No se puede cancelar el viaje: tiene envíos que ya salieron |
| `LOGYX-TRP-008` | 409 | La sugerencia Smart Load ya fue resuelta |
| `LOGYX-TRP-009` | 409 | La sugerencia Smart Load venció |
| `LOGYX-TRP-010` | 409 | Tipo de carga no compatible con el vehículo del viaje, `field=cargoType` |
| `LOGYX-TRP-011` | 409 | Alguna carga de la sugerencia ya no está publicada |
| `LOGYX-SHP-003` | 409 | Solo se cancela un envío que aún no salió del recojo (cancelar una reserva confirmada ya en ruta) |
Reutilizados: `LOGYX-PRC-001/002` (ciudad fuera del corredor, origen = destino), `LOGYX-VAL-001`, `LOGYX-GEN-404`.

## 2026-11-10 — Fase 2: negociación, evidencia, reputación, incidencias, notificaciones y operador

Todo es **aditivo** en el contrato (paths y schemas nuevos; campos nuevos en `BidView`, `RequestView`, `ShipmentView`
y `Meta`), con **un cambio de comportamiento** (ver 0). Todos los endpoints usan `Authorization: Bearer <jwt>` y
responden `{ data, meta }` / `{ error: { code, message, field }, meta }`. Enums nuevos con nombre propio en el OpenAPI:
`DocumentType`, `IncidentCategory`, `IncidentStatus`, `CounterOfferStatus`, `NotificationType`,
`NotificationTarget`, `ReviewDirection` (los schemas existentes `Status*`/`Type*` no se renumeraron).

### 0. ⚠️ Cambio de comportamiento: completar la ENTREGA exige foto (RF37/RN06) y una incidencia congela el envío (RN11)
`POST /shipments/{id}/stops/{stopId}/complete` no cambia de forma, pero ahora:
- Si la parada es `DELIVERY` y no tiene al menos una foto subida antes → **409 `LOGYX-TRK-006`**.
  Flujo del conductor: `arrive` → `POST /shipments/{id}/documents` (`type=DELIVERY_PHOTO`, `stopId` = la entrega;
  opcional `SIGNATURE`) → `complete`. Es un upload previo; `complete` no recibe referencias.
- Si el envío tiene una incidencia activa (`OPEN`/`IN_REVIEW`), `arrive` y `complete` → **409 `LOGYX-TRK-005`**.
  `ShipmentView.hasActiveIncident` (boolean, nuevo) lo indica para deshabilitar los botones.
Los clientes que completaban la entrega sin foto (web/Android, app conductor) deben agregar el paso de upload.

### 1. Campos nuevos en schemas existentes
| Schema | Campo | Descripción |
|---|---|---|
| `BidView` | `carrierTrustScore` (number) | Trust Score 0–100 del transportista (RN09; 75 sin calificaciones) |
| `BidView` | `assignedByOperator` (boolean) | La oferta la registró el operador al asignar manualmente (RN10) |
| `RequestView` | `requesterTrustScore` (number) | Trust Score 0–100 de la PYME |
| `ShipmentView` | `hasActiveIncident` (boolean) | Incidencia activa: el conductor no puede avanzar paradas (RN11) |
| `Meta` | `nextCursor` (string) | Solo en listados por cursor (chat): valor para `before`; ausente/null = no hay más |

### 2. Negociación: chat por solicitud (RF25, RN07) y contra-ofertas (RF27, RF28)
Un hilo = (solicitud, transportista). El transportista escribe en **su** hilo (`carrierOrgId` = su organización,
la de `/auth/me`) para preguntar u ofertar; la PYME dueña escribe en hilos de transportistas que ofertaron o le
escribieron. Adjudicada la solicitud, solo sigue abierto el hilo con el ganador; cancelada → solo lectura.

| Método | Path | Rol | Notas |
|---|---|---|---|
| GET | `/api/v1/shipment-requests/{id}/threads` | PYME, CARRIER | PYME: un hilo por transportista; CARRIER: el suyo |
| GET | `/api/v1/shipment-requests/{id}/threads/{carrierOrgId}/messages?before=&size=` | PYME, CARRIER, OPERATOR | cursor, orden cronológico, `size` 1–100 (30) |
| POST | `/api/v1/shipment-requests/{id}/threads/{carrierOrgId}/messages` | PYME, CARRIER | `{ "body": "..." }` (≤1000) → 201 |
| GET | `/api/v1/bids/{id}/counter-offers` | PYME, CARRIER (partes) | recientes primero |
| POST | `/api/v1/bids/{id}/counter-offers` | PYME | `{ "amount": 1700, "message": "..." }` → 201 |
| POST | `/api/v1/counter-offers/{id}/accept` | CARRIER (dueño de la oferta) | el monto de la oferta pasa a `amount` |
| POST | `/api/v1/counter-offers/{id}/reject` | CARRIER | |

```json
// GET /shipment-requests/{id}/threads (PYME)
{ "data": [ { "requestId": "…", "carrierOrgId": "…", "carrierName": "Transportes Sur Andino SRL",
              "bidId": "…", "bidAmount": 1850.00, "bidStatus": "PENDING", "messageCount": 3,
              "lastMessage": "Perfecto, el ASA-303 tiene registrador…", "lastMessageAt": "2026-11-10T15:02:11Z" } ],
  "meta": { "timestamp": "…" } }
// GET …/messages?size=30  → los 30 más recientes en orden cronológico; para ver anteriores: ?before=<meta.nextCursor>
{ "data": [ { "id": "…", "requestId": "…", "carrierOrgId": "…", "senderOrgId": "…", "senderName": "Comercial Altiplano SAC",
              "mine": true, "body": "Sí, entre 2 y 4 °C.", "proposedPrice": null, "system": false,
              "createdAt": "2026-11-10T15:01:00Z" } ],
  "meta": { "timestamp": "…", "size": 30, "nextCursor": "9d3c…" } }
// RN07: teléfono (9 dígitos con o sin espacios/guiones/puntos, +51), email, URL/dominio o @usuario
{ "error": { "code": "LOGYX-NEG-001", "field": "body",
             "message": "El mensaje contiene datos de contacto (teléfono, email, enlace o usuario de red social). Coordina todo por el chat de LOGYX (RN07)" },
  "meta": { "timestamp": "…" } }
// POST /bids/{id}/counter-offers → CounterOfferView
{ "data": { "id": "…", "bidId": "…", "requestId": "…", "carrierOrgId": "…", "offeredByOrgId": "…",
            "previousAmount": 1850.00, "amount": 1700.00, "message": "¿Lo dejamos en este monto?",
            "status": "PENDING", "createdAt": "…", "respondedAt": null }, "meta": { "timestamp": "…" } }
```
Las contra-ofertas dejan avisos en el chat (`system: true`, `proposedPrice` con el monto). `status`:
`PENDING | ACCEPTED | REJECTED | EXPIRED`.

### 3. Documentos y evidencia de entrega (RF37, RF46–RF48)
| Método | Path | Rol | Notas |
|---|---|---|---|
| POST | `/api/v1/shipments/{id}/documents` | PYME, CARRIER, DRIVER (partes) | `multipart/form-data`: `file`, `type`, `stopId?` → 201 |
| GET | `/api/v1/shipments/{id}/documents` | partes + OPERATOR | más antiguos primero |
| GET | `/api/v1/documents?type=&page=&size=` | PYME, CARRIER | todos los de mis envíos (RF48), paginado |
| GET | `/api/v1/documents/{id}/file` | partes + OPERATOR | binario (`image/jpeg`, `image/png`, `application/pdf`), mismo Bearer |

- `type`: `SERVICE_ORDER | REMISSION_GUIDE | RECEIPT | DELIVERY_PHOTO | SIGNATURE | OTHER`.
- Formatos: JPG, PNG, PDF reconocidos por **firma binaria** (no por extensión). Máx. 10 MB por archivo.
- `DELIVERY_PHOTO` / `SIGNATURE`: solo imagen, solo transportista/conductor, `stopId` de la parada `DELIVERY` y con la
  llegada ya registrada.
- El conductor solo accede a los documentos de su envío; otras organizaciones reciben 404.
```bash
curl -H "Authorization: Bearer $T" -F file=@entrega.jpg -F type=DELIVERY_PHOTO -F stopId=$STOP \
     http://localhost:8080/api/v1/shipments/$SHIPMENT/documents
```
```json
{ "data": { "id": "…", "shipmentId": "…", "stopId": "…", "type": "DELIVERY_PHOTO", "fileName": "entrega.jpg",
            "contentType": "image/jpeg", "sizeBytes": 182340, "uploadedByOrgId": "…",
            "createdAt": "…", "downloadUrl": "/api/v1/documents/…/file" }, "meta": { "timestamp": "…" } }
```
En web, `downloadUrl` no lleva token: descargar con `fetch` + Bearer (o proxy SSR) y crear un `blob:`.

### 4. Reputación (RN06, RN09, RF40–RF45)
| Método | Path | Rol | Notas |
|---|---|---|---|
| POST | `/api/v1/shipments/{id}/reviews` | PYME, CARRIER (partes) | `{ punctuality, cargoCondition, communication, comment? }` 1–5 → 201 |
| GET | `/api/v1/shipments/{id}/reviews` | partes, DRIVER, OPERATOR | `canReview` + motivo + calificaciones del envío |
| GET | `/api/v1/organizations/{id}/reputation` | autenticado | perfil público (RF44) |
| GET | `/api/v1/organizations/{id}/reviews?page=&size=` | autenticado | calificaciones recibidas, paginado |

- Dirección automática: la PYME califica al transportista (`PYME_TO_CARRIER`) y viceversa (`CARRIER_TO_PYME`;
  ahí `cargoCondition` = exactitud de la carga declarada). `rating` = promedio de las 3 dimensiones.
- RN06: envío `DELIVERED` + al menos una `DELIVERY_PHOTO`; una vez por organización y envío.
- **Trust Score (RN09)**: `score = (C·m + Σ x_i) / (C + n)`, `x_i = (rating_i − 1) × 25`, `m = 75`, `C = 5`
  (`logyx.reputation.prior-weight`), 1 decimal; se recalcula tras cada calificación. Ej.: una nota 4.67 → 77.8.
```json
// GET /shipments/{id}/reviews
{ "data": { "canReview": false, "blockedCode": "LOGYX-REV-001", "blockedReason": "El envío aún no ha sido entregado (RN06)",
            "reviews": [] }, "meta": { "timestamp": "…" } }
// GET /organizations/{id}/reputation
{ "data": { "organizationId": "…", "legalName": "Transportes Sur Andino SRL", "type": "CARRIER", "rucVerified": true,
            "city": "Arequipa", "trustScore": 77.8, "totalReviews": 1, "averageRating": 4.67,
            "averagePunctuality": 5.00, "averageCargoCondition": 4.00, "averageCommunication": 5.00,
            "priorMean": 75, "priorWeight": 5,
            "recentReviews": [ { "id": "…", "shipmentId": "…", "reviewerOrgId": "…", "reviewerName": "Comercial Altiplano SAC",
                                 "reviewedOrgId": "…", "direction": "PYME_TO_CARRIER", "punctuality": 5,
                                 "cargoCondition": 4, "communication": 5, "rating": 4.67,
                                 "comment": "Puntual y cuidadoso", "createdAt": "…" } ] },
  "meta": { "timestamp": "…" } }
```

### 5. Incidencias (RF52–RF55, RN11)
| Método | Path | Rol | Notas |
|---|---|---|---|
| POST | `/api/v1/shipments/{id}/incidents` | PYME, CARRIER, DRIVER (partes) | `{ "category": "DELAY", "description": "..." }` → 201 |
| GET | `/api/v1/shipments/{id}/incidents` | partes + OPERATOR | |
| GET | `/api/v1/incidents?status=OPEN,IN_REVIEW&active=true&page=&size=` | PYME, CARRIER (mis envíos), OPERATOR (todas) | paginado |
| GET | `/api/v1/incidents/{id}` | partes + OPERATOR | incluye `comments` |
| POST | `/api/v1/incidents/{id}/comments` | partes + OPERATOR | `{ "body": "..." }` → 201, devuelve la incidencia con comentarios |
| POST | `/api/v1/incidents/{id}/status` | OPERATOR | `{ "status": "RESOLVED", "resolutionNotes": "..." }` |

`category`: `CARGO_DAMAGE | DELAY | LOST_CARGO | WRONG_DELIVERY | DRIVER_CONDUCT | OTHER`.
`status`: `OPEN → IN_REVIEW → RESOLVED → CLOSED` (desde OPEN/IN_REVIEW también directo a RESOLVED o CLOSED).
`active` = OPEN o IN_REVIEW (congela el envío). Máximo una activa por envío.
```json
{ "data": { "id": "…", "shipmentId": "…", "requesterOrgId": "…", "requesterName": "Comercial Altiplano SAC",
            "carrierOrgId": "…", "carrierName": "Transportes Sur Andino SRL", "reportedByOrgId": "…",
            "category": "CARGO_DAMAGE", "description": "Dos bolsas llegaron rotas", "status": "OPEN", "active": true,
            "resolutionNotes": null, "createdAt": "…", "updatedAt": "…", "resolvedAt": null,
            "comments": [ { "id": "…", "authorOrgId": "…", "authorName": "LOGYX Operaciones SAC",
                            "body": "Revisando", "createdAt": "…" } ] }, "meta": { "timestamp": "…" } }
```

### 6. Notificaciones in-app (RF49, RF51)
Por organización (PYME, CARRIER, OPERATOR; los conductores no tienen centro propio en el MVP). Se generan por eventos.
| Método | Path | Notas |
|---|---|---|
| GET | `/api/v1/notifications?unread=true&page=&size=` | siempre paginado (20), recientes primero |
| GET | `/api/v1/notifications/unread-count` | `{ "unread": 3 }` |
| POST | `/api/v1/notifications/{id}/read` | devuelve la notificación |
| POST | `/api/v1/notifications/read-all` | `{ "updated": 3 }` |

| `type` | Para | `relatedType` → `relatedId` |
|---|---|---|
| `NEW_BID` | PYME | `REQUEST` |
| `BID_ACCEPTED` | transportista ganador (o asignado por el operador) | `SHIPMENT` |
| `BID_REJECTED` | transportistas no elegidos | `REQUEST` |
| `COUNTER_OFFER` | propuesta → transportista (`COUNTER_OFFER`); aceptada/rechazada → PYME (`BID`) | |
| `NEW_MESSAGE` | la otra parte del hilo | `REQUEST` (el hilo se abre con `carrierOrgId`) |
| `SHIPMENT_STATUS_CHANGE` | PYME (en camino, entregado, asignación del operador); transportista (entregado) | `SHIPMENT` |
| `INCIDENT_REPORTED` | la otra parte + operadores | `INCIDENT` |
| `INCIDENT_RESOLVED` | ambas partes (RESOLVED o CLOSED) | `INCIDENT` |
| `NEW_REVIEW` | organización calificada | `SHIPMENT` |
```json
{ "data": [ { "id": "…", "type": "NEW_BID", "title": "Nueva oferta recibida",
              "body": "Transportes Sur Andino SRL ofertó S/ 1,850.00 por tu carga Lima → Juliaca.",
              "relatedType": "REQUEST", "relatedId": "…", "read": false, "readAt": null, "createdAt": "…" } ],
  "meta": { "timestamp": "…", "page": 0, "size": 20, "total": 15 } }
```

### 7. Operador LOGYX (RF56–RF60, RN10)
Cuentas `OPERATOR` (grupo `OPERATOR` en el JWT); no se registran públicamente (`POST /auth/register` con
`OPERATOR` → 403). Demo: `operator@logyx.pe` / `Logyx2026!`. Además de estos endpoints, el operador puede leer
`GET /shipment-requests/{id}`, `GET /shipments/{id}`, documentos, chats e incidencias de todos.
| Método | Path | Notas |
|---|---|---|
| GET | `/api/v1/operator/sla-alerts` | sin ofertas ≥ 24 h, subastas que cierran en ≤ 10 min, envíos con incidencia activa |
| GET | `/api/v1/operator/metrics` | contadores globales |
| GET | `/api/v1/operator/requests/{id}/candidates` | vehículos `AVAILABLE` con capacidad, mejor Trust Score primero |
| POST | `/api/v1/operator/requests/{id}/assign` | RN10: `{ "carrierOrgId", "vehicleId", "amount"?, "notes"? }` |

La asignación manual equivale a "el transportista ofertó y la PYME aceptó": se crea una oferta con
`assignedByOperator: true` (por el precio sugerido si no se envía `amount`; nunca bajo el piso RN03, con RN02 de
capacidad), se adjudica (RN05) y se genera el envío `PENDING`. El transportista recibe `BID_ACCEPTED` y la PYME
`SHIPMENT_STATUS_CHANGE`. Solo si la solicitud está `PUBLISHED`, sin ofertas y publicada hace ≥ 24 h
(`logyx.operator.sla`); si no, 409 `LOGYX-OPS-001`.
```json
// GET /operator/sla-alerts
{ "data": { "slaHours": 24, "expiringWindowMinutes": 10,
            "requestsWithoutBids": [ { "id": "…", "requesterOrgId": "…", "requesterName": "Comercial Altiplano SAC",
                "originCity": "PUNO", "destinationCity": "TACNA", "cargoType": "HAZARDOUS", "weightKg": 9000,
                "pickupDate": "2026-11-17", "suggestedPrice": 2830.10, "priceFloor": 2122.58,
                "createdAt": "…", "bidCount": 0, "hoursWithoutBids": 30, "auctionStatus": "OPEN", "auctionClosesAt": "…" } ],
            "expiringAuctions": [],
            "shipmentsWithIncidents": [ { "incidentId": "…", "shipmentId": "…", "category": "CARGO_DAMAGE", "status": "OPEN",
                "requesterName": "…", "carrierName": "…", "originCity": "AREQUIPA", "destinationCity": "JULIACA",
                "reportedAt": "…", "hoursOpen": 0 } ] }, "meta": { "timestamp": "…" } }
// GET /operator/metrics
{ "data": { "openRequests": 7, "requestsAtRisk": 2, "matchedRequests": 2, "cancelledRequests": 0,
            "shipmentsPending": 0, "shipmentsInTransit": 1, "shipmentsDelivered": 1, "activeIncidents": 1,
            "pymes": 1, "carriers": 2 }, "meta": { "timestamp": "…" } }
// POST /operator/requests/{id}/assign → ManualAssignmentResult
{ "data": { "shipmentId": "…", "bidId": "…", "requestId": "…", "carrierOrgId": "…", "carrierName": "Logística Titicaca EIRL",
            "amount": 2830.10, "vehiclePlate": "LTT-502", "bidStatus": "ACCEPTED", "assignedByOperator": true,
            "rejectedBidIds": [] }, "meta": { "timestamp": "…" } }
```

### 8. Códigos de error nuevos
| Código | HTTP | `field` | Cuándo |
|---|---|---|---|
| `LOGYX-NEG-001` | 400 | `body` / `message` | RN07: datos de contacto en el chat o en la contra-oferta |
| `LOGYX-NEG-002` | 409 | — | hilo cerrado (solicitud cancelada o adjudicada a otro) |
| `LOGYX-NEG-003` | 400 | `amount` | contra-oferta con el mismo monto de la oferta |
| `LOGYX-NEG-004` | 409 | — | ya hay una contra-oferta pendiente |
| `LOGYX-NEG-005` | 409 | — | la oferta ya no admite contra-ofertas (no pendiente / solicitud no publicada) |
| `LOGYX-NEG-007` | 409 | — | la contra-oferta ya fue respondida |
| `LOGYX-BID-001` | 400 | `amount` | (reutilizado) contra-oferta o asignación manual bajo el piso RN03 |
| `LOGYX-DOC-001` | 400 | `file` | tipo no permitido (o evidencia que no es imagen) |
| `LOGYX-DOC-002` | 400 | `file` | archivo > 10 MB |
| `LOGYX-DOC-003` | 400 | `file` | archivo vacío |
| `LOGYX-DOC-004` | 400 | `stopId` | evidencia sin la parada de entrega |
| `LOGYX-DOC-005` | 409 | `stopId` | evidencia antes de registrar la llegada |
| `LOGYX-DOC-006` | 409 | — | envío cancelado |
| `LOGYX-REV-001/002/003` | 409 | — | no entregado / sin foto de evidencia (RN06) / ya calificó |
| `LOGYX-INC-001` | 409 | — | ya hay una incidencia activa en el envío |
| `LOGYX-INC-002` | 409 | `status` | transición de estado inválida |
| `LOGYX-INC-003` | 400 | `resolutionNotes` | resolver sin notas |
| `LOGYX-INC-004` | 409 | — | comentar una incidencia cerrada |
| `LOGYX-INC-005` | 409 | — | reportar sobre un envío cancelado |
| `LOGYX-TRK-005` | 409 | — | RN11: incidencia activa, no se avanza la parada |
| `LOGYX-TRK-006` | 409 | — | completar la entrega sin foto de evidencia |
| `LOGYX-OPS-001` | 409 | — | RN10: la solicitud no tiene el SLA vencido (o ya no está publicada / tiene ofertas) |
| `LOGYX-OPS-002` | 400 | `carrierOrgId` | la organización no es un transportista |
| `LOGYX-VAL-001` | 400 | `type`, `stopId`, `file`, `before`, `size`, `status` | parámetros inválidos de los endpoints nuevos |

## 2026-11-10 — Contrato aditivo (fase 1)

### 1. `BidView`: `vehicleType`, `vehiclePlate`
Tipo y placa del vehículo ofertado, en todas las respuestas que devuelven ofertas
(`POST /shipment-requests/{id}/bids`, `GET /shipment-requests/{id}/bids`, `myBid` de `RequestView`, `POST /bids/{id}/accept`).
Se copian al ofertar (migración `V3`), porque la flota es privada del transportista (RLS) y la PYME no puede leerla.
`vehicleType` es el enum `Vehicle.Type` (`VAN, TRUCK_SMALL, TRUCK_MEDIUM, TRUCK_LARGE, PLATFORM, REFRIGERATED, TANKER`).
Las ofertas anteriores a V3 se rellenaron con los datos actuales del vehículo.
```json
{ "id": "…", "requestId": "…", "carrierOrgId": "…", "carrierName": "Transportes Sur Andino SRL",
  "carrierRucVerified": true, "vehicleId": "…", "amount": 1850.00, "notes": "Salida 6 am",
  "status": "PENDING", "createdAt": "2026-11-10T10:00:00-05:00",
  "vehicleType": "TRUCK_MEDIUM", "vehiclePlate": "ASA-101" }
```

### 2. `RequestView`: `requesterRucVerified`, `minVehicleType`
- `requesterRucVerified` (boolean): el RUC de la PYME solicitante está verificado.
- `minVehicleType` (`Vehicle.Type`): vehículo mínimo sugerido (orientativo; no bloquea ofertas). Regla
  (`VehicleRequirementPolicy`): carga `PERISHABLE` → `REFRIGERATED`; si no, ≤1 500 kg `VAN`, ≤3 000 `TRUCK_SMALL`,
  ≤8 000 `TRUCK_MEDIUM`, mayor `TRUCK_LARGE`.
```json
{ "id": "…", "cargoType": "PERISHABLE", "weightKg": 2000, "…": "…",
  "requesterRucVerified": true, "minVehicleType": "REFRIGERATED" }
```

### 3. `UserInfo.organizationCity`
En `GET /auth/me`, `POST /auth/login`, `POST /auth/register` (puede ser `null` si la organización no tiene ciudad).
```json
{ "id": "…", "email": "pyme@logyx.pe", "fullName": "…", "role": "ADMIN", "organizationId": "…",
  "organizationName": "Comercial Altiplano SAC", "organizationType": "PYME", "rucVerified": true,
  "organizationCity": "Juliaca" }
```

### 4. `error.field` en errores de negocio
Mismo formato `{ error: { code, message, field }, meta }`; `field` (antes `null`) ahora se completa:

| Situación | Código | HTTP | `field` |
|---|---|---|---|
| RUC inválido | `LOGYX-ORG-001` | 400 | `ruc` |
| RUC repetido | `LOGYX-ORG-002` | 409 | `ruc` |
| Email repetido (registro o alta de conductor) | `LOGYX-AUTH-002` | 409 | `email` |
| Placa repetida | `LOGYX-FLT-001` | 409 | `plate` |
| Oferta bajo el piso | `LOGYX-BID-001` | 400 | `amount` |
| Vehículo sin capacidad | `LOGYX-CAP-001` | 400 | `vehicleId` |
| Vehículo no disponible | `LOGYX-CAP-002` | 409 | `vehicleId` |
| Parámetros de listado inválidos (`sort`, `size`, `page`, `originCity`, `cargoType`, `maxWeightKg`, `pickupFrom`) | `LOGYX-VAL-001` / `LOGYX-PRC-001` | 400 | nombre del parámetro |

### 5. Montos en mensajes con formato peruano
Los montos dentro de `error.message` usan `S/ 1,850.00` (miles con coma, 2 decimales).
```json
{ "error": { "code": "LOGYX-BID-001", "field": "amount",
             "message": "La oferta no puede ser menor al precio mínimo de S/ 1,850.00 (RN03)" },
  "meta": { "timestamp": "2026-11-10T15:00:00Z" } }
```

### 6. Marketplace: filtros, orden y paginación
`GET /api/v1/shipment-requests` (CARRIER) acepta, todos opcionales:
`originCity`, `destinationCity` (códigos, p. ej. `LIMA`), `pickupFrom`, `pickupTo` (ISO `yyyy-MM-dd`, inclusivos),
`maxWeightKg` (peso de la carga ≤), `cargoType`, `sort` = `pickup` (por omisión, recojo más próximo) | `price_desc`
(precio sugerido) | `recent` (publicación), `page` (0-based, por omisión 0), `size` (por omisión 20, máx 100).
**Siempre pagina** (por omisión 20 por página): para ver más de 20 resultados hay que pasar `page`.
`meta` gana `page`, `size`, `total` (total sin paginar); son opcionales y se omiten en endpoints no paginados.
```
GET /api/v1/shipment-requests?originCity=LIMA&cargoType=GENERAL&sort=price_desc&page=0&size=20
```
```json
{ "data": [ { "id": "…", "…": "…" } ],
  "meta": { "timestamp": "2026-11-10T15:00:00Z", "page": 0, "size": 20, "total": 37 } }
```
`GET /shipment-requests/mine` y `GET /shipments` aceptan `page` y `size` **solo si se envían** (si no, devuelven todo
como antes y `meta` no trae `page/size/total`). Errores: `size` fuera de 1–100 o `page` < 0 → 400 `LOGYX-VAL-001`.

### 7. Nuevos endpoints
**`GET /api/v1/driver/shipments/active`** (DRIVER): el envío `IN_TRANSIT` del conductor; si no hay, el `PENDING` más
antiguo. Mismo `ShipmentView` que `GET /shipments/{id}`. Sin envío activo → 404:
```json
{ "error": { "code": "LOGYX-SHP-002", "message": "No tienes un envío activo", "field": null },
  "meta": { "timestamp": "2026-11-10T15:00:00Z" } }
```

**`GET /api/v1/dashboard/summary`** (PYME o CARRIER; DRIVER → 403). Los campos que no aplican al rol se omiten.
"Entregados del mes" cuenta desde el día 1 a las 00:00 hora de Lima. "Cargas compatibles" = solicitudes abiertas cuyo
peso cabe en el vehículo `AVAILABLE` más grande del transportista (incluye las que ya ofertó).
```json
// PYME
{ "data": { "role": "PYME", "activeRequests": 3, "bidsToReview": 5, "shipmentsInTransit": 1, "deliveredThisMonth": 12 },
  "meta": { "timestamp": "2026-11-10T15:00:00Z" } }
// CARRIER
{ "data": { "role": "CARRIER", "compatibleLoads": 8, "pendingBids": 2, "activeShipments": 3, "deliveredThisMonth": 9 },
  "meta": { "timestamp": "2026-11-10T15:00:00Z" } }
```
