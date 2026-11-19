package pe.logyx.core.model

import kotlinx.serialization.Serializable

@Serializable enum class OrgType { PYME, CARRIER, OPERATOR }

/** Rol dentro de la organización (claim `role`). `UNKNOWN` = valor que este cliente no conoce. */
@Serializable enum class Role { ADMIN, OPS, VIEWER, DRIVER, UNKNOWN }

/** Permisos que el backend calcula para el usuario (`UserInfo.permissions`, V14). Úsalos para mostrar u ocultar acciones. */
@Serializable enum class OrgPermission { READ, OPERATE, MANAGE_USERS, MANAGE_ORGANIZATION, MANAGE_DRIVERS }

/** Verificación de la organización por el operador (RN01, V13). `UNKNOWN` = valor que este cliente no conoce. */
@Serializable enum class VerificationStatus { PENDING, VERIFIED, REJECTED, SUSPENDED, UNKNOWN }

@Serializable enum class CargoType { GENERAL, FRAGILE, PERISHABLE, HAZARDOUS, BULK, OVERSIZED }

/** Estado de la solicitud de carga. */
@Serializable enum class RequestStatus { PUBLISHED, MATCHED, CANCELLED, UNKNOWN }

@Serializable enum class AuctionStatus { OPEN, CLOSED, CANCELLED, UNKNOWN }

@Serializable enum class CloseReason { TIMEOUT, MAX_BIDS, ACCEPTED, CANCELLED }

/** `WITHDRAWN` (V13): la retiró el transportista. */
@Serializable enum class BidStatus { PENDING, ACCEPTED, REJECTED, WITHDRAWN, UNKNOWN }

@Serializable enum class ShipmentStatus { PENDING, IN_TRANSIT, DELIVERED, CANCELLED, UNKNOWN }

@Serializable enum class StopStatus { PENDING, ARRIVED, COMPLETED }

@Serializable enum class StopType { PICKUP, DELIVERY }

@Serializable enum class VehicleType { VAN, TRUCK_SMALL, TRUCK_MEDIUM, TRUCK_LARGE, PLATFORM, REFRIGERATED, TANKER }

@Serializable enum class VehicleStatus { AVAILABLE, IN_ROUTE, MAINTENANCE }

@Serializable enum class DriverStatus { AVAILABLE, ON_DUTY, OFF_DUTY }

// --- Fase 2 del contrato ---
/** `PICKUP_PHOTO` (V23, roadmap 2.5): foto de la carga en el recojo (solo el conductor, entre la llegada y el cierre). */
@Serializable enum class DocumentType { SERVICE_ORDER, REMISSION_GUIDE, RECEIPT, DELIVERY_PHOTO, SIGNATURE, OTHER, PICKUP_PHOTO }

/**
 * V23 (roadmap 2.7) suma las de la Ola 2. `DELAY`, `ROAD_BLOCKED` y `PAYMENT_DISPUTE` son informativas (no congelan el
 * viaje, RN11; ver [IncidentCategory.informative]). `UNKNOWN` = valor que este cliente no conoce (no se reporta).
 */
@Serializable enum class IncidentCategory {
    CARGO_DAMAGE, DELAY, LOST_CARGO, WRONG_DELIVERY, DRIVER_CONDUCT, OTHER,
    PICKUP_NO_SHOW, SHIPPER_NO_SHOW, BREAKDOWN, ACCIDENT, THEFT, ROAD_BLOCKED, PARTIAL_DELIVERY, PAYMENT_DISPUTE,
    UNKNOWN,
}

/** Severidad de una incidencia (V23): fija su SLA. `UNKNOWN` = valor nuevo del servidor. */
@Serializable enum class IncidentSeverity { LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN }

@Serializable enum class IncidentStatus { OPEN, IN_REVIEW, RESOLVED, CLOSED }

/** `EXPIRED` (F40): venció al adjudicar la solicitud o retirar la oferta. */
@Serializable enum class CounterOfferStatus { PENDING, ACCEPTED, REJECTED, EXPIRED, UNKNOWN }

@Serializable
enum class NotificationType {
    NEW_BID, BID_ACCEPTED, BID_REJECTED, COUNTER_OFFER, NEW_MESSAGE,
    SHIPMENT_STATUS_CHANGE, INCIDENT_REPORTED, INCIDENT_RESOLVED, NEW_REVIEW,
    // Fase 4
    TRIP_BOOKING_REQUESTED, TRIP_BOOKING_CONFIRMED, TRIP_BOOKING_REJECTED, TRIP_BOOKING_CANCELLED, TRIP_CANCELLED,
    SMART_LOAD_SUGGESTED, SMART_LOAD_ACCEPTED,
    // V13 (huecos P1)
    AUCTION_CLOSED, REQUEST_CANCELLED, SHIPMENT_CANCELLED, BID_WITHDRAWN, ORGANIZATION_VERIFICATION,
    // V16: un Lector pidió acceso (llega a cada ADMIN)
    ACCESS_REQUESTED,
    // V22 (Ola 2, zona T): alerta de carga que calza con una búsqueda guardada, documento de flota por vencer, oferta mejorada
    NEW_LOAD_MATCH, FLEET_DOCUMENT_EXPIRING, BID_IMPROVED,
    // V21 (Ola 2, zona P): plantilla publicada o fallida, invitación a ofertar, segunda oferta pedida/respondida
    TEMPLATE_RUN, REQUEST_INVITATION, BACKUP_REQUESTED, BACKUP_RESPONDED,
    // V23 (Ola 2, zona X): ajustes del precio del envío e incidencia asignada (esta última solo al operador)
    ADJUSTMENT_PROPOSED, ADJUSTMENT_DECIDED, ADJUSTMENT_ESCALATED, INCIDENT_ASSIGNED,
    /** Valor que este cliente no conoce (se mapea con `coerceInputValues`). */
    UNKNOWN,
}

/** V16: `USER` = una persona de tu organización (quien pidió acceso). */
@Serializable enum class NotificationTarget { REQUEST, BID, COUNTER_OFFER, SHIPMENT, INCIDENT, REVIEW, TRIP, SMART_LOAD, ORGANIZATION, USER, VEHICLE, DRIVER, TEMPLATE }

@Serializable enum class ReviewDirection { PYME_TO_CARRIER, CARRIER_TO_PYME }

// --- Fase 4: viajes publicados, reservas, retornos y Smart Load ---
@Serializable enum class TripStatus { OPEN, FILLING, CLOSED, COMPLETED, CANCELLED }

@Serializable enum class BookingStatus { PENDING, CONFIRMED, REJECTED, CANCELLED }

/** `PER_KG`: `rate` = S/ por kg. `PER_TRIP`: `rate` = tarifa de todo el tramo con la capacidad completa. */
@Serializable enum class TripPricingMode { PER_KG, PER_TRIP }

@Serializable enum class SmartLoadStatus { PENDING, ACCEPTED, REJECTED, EXPIRED }

// --- V13–V15 ---
/** Estado final del envío al cerrar una incidencia (`IncidentView.shipmentOutcome`). */
@Serializable enum class IncidentShipmentOutcome { RESUME, DELIVERED, CANCELLED }

/** Banda cualitativa de mi oferta frente a la mejor (`MyBidStanding.vsBest`); nunca trae montos (RN04). */
@Serializable enum class BidStandingBand { BEST, WITHIN_5_PCT, WITHIN_15_PCT, ABOVE_15_PCT, UNKNOWN }

/** Posición aproximada de mi oferta (V22, 2.8) sin montos (RN04): `TOP_TWO` entre las 2 más bajas; `LOWER_HALF` por debajo de la mediana; `UPPER_HALF` por encima. */
@Serializable enum class BidPosition { TOP_TWO, LOWER_HALF, UPPER_HALF, UNKNOWN }

/** `GET /driver/shipments?scope=` (V15, F22). */
enum class DriverShipmentScope { ACTIVE, HISTORY, ALL }
