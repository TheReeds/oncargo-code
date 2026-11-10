# Reglas de negocio → implementación

| ID | Regla (SRS 7.3) | Estado | Dónde |
|---|---|---|---|
| RN01 | Solo PYMEs con RUC verificado publican | ✅ | `marketplace` publish (RUC por módulo 11 al registrarse; SUNAT pendiente) |
| RN02 | Ofertar requiere vehículo propio, disponible y con capacidad | ✅ | `marketplace` placeBid |
| RN03 | Oferta ≥ 75% del precio sugerido | ✅ | `marketplace` placeBid, `pricing` (floor-ratio) |
| RN04 | Subasta cierra a los 20 min o 5 ofertas | ✅ | placeBid (MAX_BIDS) + `AuctionScheduler` (TIMEOUT) |
| RN05 | Aceptar una oferta rechaza las demás | ✅ | `marketplace` accept |
| RN06 | Calificar requiere foto de evidencia + entregado | ⬜ | `reputation` (pendiente) |
| RN07 | Bloquear teléfonos/emails/URLs en el chat | ⬜ | `negotiation` (pendiente) |
| RN08 | Paradas en orden secuencial | ✅ | `shipment` arrive/complete |
| RN09 | Trust Score bayesiano (prior 75/100) | ⬜ | `reputation` (pendiente) |
| RN10 | Operador asigna manualmente tras 24 h sin ofertas | ⬜ | `operator` (pendiente) |
| RN11 | Incidencia activa congela el estado del envío | ⬜ | `incident` (pendiente) |
| RN12 | Viaje publicado ≤ capacidad de la flota | ⬜ | `trip`/`returnload` (pendiente) |
| RNF18 | Consentimiento Ley 29733 al registrarse | ✅ | `identity` register (`consent_accepted_at`) |
