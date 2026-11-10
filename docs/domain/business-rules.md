# Reglas de negocio → implementación

Rutas relativas a `backend/src/main/java/pe/logyx/`.

| ID | Regla (SRS 7.3) | Estado | Dónde |
|---|---|---|---|
| RN01 | Solo PYMEs con RUC verificado publican | ✅ | `marketplace/domain/model/ShipmentRequest.ensureRequesterCanPublish` (desde `PublishRequestUseCase`); RUC por módulo 11 al registrarse en `identity/domain/RucValidator` (SUNAT pendiente) |
| RN02 | Ofertar requiere vehículo propio, disponible y con capacidad | ✅ | `marketplace/domain/BidEligibilityPolicy.ensureVehicleEligible`; propiedad vía `fleet/application/FleetService.ownVehicle` (desde `PlaceBidUseCase`) |
| RN03 | Oferta ≥ 75% del precio sugerido | ✅ | Piso: `pricing/domain/PriceFloorPolicy` (`logyx.pricing.floor-ratio`); se exige al ofertar en `marketplace/domain/BidEligibilityPolicy.ensureAboveFloor` |
| RN04 | Subasta cierra a los 20 min o 5 ofertas | ✅ | `marketplace/domain/AuctionClosingPolicy`; MAX_BIDS en `PlaceBidUseCase`, TIMEOUT en `CloseExpiredAuctionsUseCase` (vía `infrastructure/scheduling/AuctionScheduler`) |
| RN05 | Aceptar una oferta rechaza las demás | ✅ | `marketplace/application/AcceptBidUseCase` |
| RN06 | Calificar requiere foto de evidencia + entregado | ✅ | `reputation/domain/ReviewEligibilityPolicy` (desde `reputation/application/ReviewService.submit`; `GET /shipments/{id}/reviews` expone `canReview`/motivo); la foto la exige además `shipment/domain/DeliveryEvidencePolicy` al completar la parada DELIVERY (vía puerto `DeliveryEvidenceProvider` → `documents.application.DocumentQueries`) |
| RN07 | Bloquear teléfonos/emails/URLs en el chat | ✅ | `negotiation/domain/ContactInfoPolicy` (9 dígitos con espacio/punto/guion opcional entre dígitos y prefijo +51/51, emails, URLs/dominios sueltos, `@usuario`); se aplica en `Message.write` (field `body`) y `CounterOffer.propose` (field `message`) → `LOGYX-NEG-001` |
| RN08 | Paradas en orden secuencial | ✅ | `shipment/domain/StopSequencePolicy` (desde `TrackStopsUseCase` arrive/complete) |
| RN09 | Trust Score bayesiano (prior 75/100) | ✅ | `reputation/domain/TrustScoreCalculator`: `score = (C·m + Σ x_i)/(C + n)`, `x_i = (rating_i − 1)·25`, `m = logyx.reputation.prior-mean` (75), `C = logyx.reputation.prior-weight` (5). Recalculado tras cada calificación en `reputation/application/ReputationRecalculator` (observer AFTER_SUCCESS, SystemTx). Expuesto en `GET /organizations/{id}/reputation`, `BidView.carrierTrustScore`, `RequestView.requesterTrustScore`. Las métricas compuestas de RF42/RF43 (cancelación, pago…) quedan pendientes |
| RN10 | Operador asigna manualmente tras 24 h sin ofertas | ✅ | `marketplace/domain/ManualAssignmentPolicy` (`logyx.operator.sla`, PT24H) desde `marketplace/application/ManualAssignmentUseCase` (oferta `assignedByOperator` + mismo `AcceptBidUseCase.award`); endpoint en `operator/infrastructure/web/OperatorResource`; RLS de escritura del operador en `V9__operator.sql` |
| RN11 | Incidencia activa congela el estado del envío | ✅ | Estados en `incident/domain/IncidentStatusPolicy` (solo el operador los mueve); `shipment/application/TrackStopsUseCase` rechaza arrive/complete con `LOGYX-TRK-005` vía puerto `IncidentHoldProvider` → `incident.application.IncidentQueries`; `ShipmentView.hasActiveIncident`. No se agregó un estado `ISSUE` al envío (habría cambiado el enum del contrato) |
| RN12 | Viaje publicado ≤ capacidad de la flota | ⬜ | `trip`/`returnload` (pendiente) |
| (apoyo RN02) | Vehículo mínimo sugerido por carga: PERISHABLE → `REFRIGERATED`; ≤1 500 kg `VAN`; ≤3 000 `TRUCK_SMALL`; ≤8 000 `TRUCK_MEDIUM`; mayor `TRUCK_LARGE`. Es orientativo (`RequestView.minVehicleType`); lo que bloquea una oferta sigue siendo la capacidad en kg | ✅ | `marketplace/domain/VehicleRequirementPolicy` |
| (apoyo RN03/RN02) | Los errores de oferta indican el campo (`amount`, `vehicleId`) y los montos en mensajes usan `S/ 1,850.00` | ✅ | `BidEligibilityPolicy`, `shared/domain/Money` |
| (apoyo RF37) | Completar la parada de entrega exige al menos una foto `DELIVERY_PHOTO` subida antes | ✅ | `shipment/domain/DeliveryEvidencePolicy` (`LOGYX-TRK-006`); reglas de archivo en `documents/domain/DocumentUploadPolicy` + `FileFormat` (jpg/png/pdf por firma binaria, máx. 10 MB) |
| (apoyo RF25) | Quién escribe en un hilo de chat | ✅ | `negotiation/domain/ThreadAccessPolicy` + `negotiation/application/ChatThreads` |
| RNF18 | Consentimiento Ley 29733 al registrarse | ✅ | `identity/infrastructure/web/AuthDtos.RegisterRequest` (`@AssertTrue`) + `identity/application/AuthService.register` (`consent_accepted_at`, auditoría) |
