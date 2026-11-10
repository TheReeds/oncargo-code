# Reglas de negocio → implementación

Rutas relativas a `backend/src/main/java/pe/logyx/`.

| ID | Regla (SRS 7.3) | Estado | Dónde |
|---|---|---|---|
| RN01 | Solo PYMEs con RUC verificado publican | ✅ | `marketplace/domain/model/ShipmentRequest.ensureRequesterCanPublish` (desde `PublishRequestUseCase`); RUC por módulo 11 al registrarse en `identity/domain/RucValidator` (SUNAT pendiente) |
| RN02 | Ofertar requiere vehículo propio, disponible y con capacidad | ✅ | `marketplace/domain/BidEligibilityPolicy.ensureVehicleEligible`; propiedad vía `fleet/application/FleetService.ownVehicle` (desde `PlaceBidUseCase`) |
| RN03 | Oferta ≥ 75% del precio sugerido | ✅ | Piso: `pricing/domain/PriceFloorPolicy` (`logyx.pricing.floor-ratio`); se exige al ofertar en `marketplace/domain/BidEligibilityPolicy.ensureAboveFloor` |
| RN04 | Subasta cierra a los 20 min o 5 ofertas | ✅ | `marketplace/domain/AuctionClosingPolicy`; MAX_BIDS en `PlaceBidUseCase`, TIMEOUT en `CloseExpiredAuctionsUseCase` (vía `infrastructure/scheduling/AuctionScheduler`) |
| RN05 | Aceptar una oferta rechaza las demás | ✅ | `marketplace/application/AcceptBidUseCase` |
| RN06 | Calificar requiere foto de evidencia + entregado | ⬜ | `reputation` (pendiente) |
| RN07 | Bloquear teléfonos/emails/URLs en el chat | ⬜ | `negotiation` (pendiente) |
| RN08 | Paradas en orden secuencial | ✅ | `shipment/domain/StopSequencePolicy` (desde `TrackStopsUseCase` arrive/complete) |
| RN09 | Trust Score bayesiano (prior 75/100) | ⬜ | `reputation` (pendiente) |
| RN10 | Operador asigna manualmente tras 24 h sin ofertas | ⬜ | `operator` (pendiente) |
| RN11 | Incidencia activa congela el estado del envío | ⬜ | `incident` (pendiente) |
| RN12 | Viaje publicado ≤ capacidad de la flota | ⬜ | `trip`/`returnload` (pendiente) |
| (apoyo RN02) | Vehículo mínimo sugerido por carga: PERISHABLE → `REFRIGERATED`; ≤1 500 kg `VAN`; ≤3 000 `TRUCK_SMALL`; ≤8 000 `TRUCK_MEDIUM`; mayor `TRUCK_LARGE`. Es orientativo (`RequestView.minVehicleType`); lo que bloquea una oferta sigue siendo la capacidad en kg | ✅ | `marketplace/domain/VehicleRequirementPolicy` |
| (apoyo RN03/RN02) | Los errores de oferta indican el campo (`amount`, `vehicleId`) y los montos en mensajes usan `S/ 1,850.00` | ✅ | `BidEligibilityPolicy`, `shared/domain/Money` |
| RNF18 | Consentimiento Ley 29733 al registrarse | ✅ | `identity/infrastructure/web/AuthDtos.RegisterRequest` (`@AssertTrue`) + `identity/application/AuthService.register` (`consent_accepted_at`, auditoría) |
