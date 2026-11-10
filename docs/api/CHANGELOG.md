# Changelog del contrato API

Fuente de verdad: `docs/api/openapi.yaml` (generado por el backend). Todos los cambios listados son **aditivos y
retrocompatibles** (no se renombró ni eliminó ningún campo, path o schema), salvo el cambio de comportamiento
señalado con ⚠️ en la fase 2. Los clientes deben regenerar/actualizar
sus DTOs; los campos nuevos de respuesta son siempre seguros de ignorar.

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
