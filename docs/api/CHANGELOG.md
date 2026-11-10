# Changelog del contrato API

Fuente de verdad: `docs/api/openapi.yaml` (generado por el backend). Todos los cambios listados son **aditivos y
retrocompatibles**: no se renombró ni eliminó ningún campo, path o schema. Los clientes deben regenerar/actualizar
sus DTOs; los campos nuevos de respuesta son siempre seguros de ignorar.

## 2026-11-10

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
