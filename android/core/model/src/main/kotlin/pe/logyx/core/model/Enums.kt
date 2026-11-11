package pe.logyx.core.model

import kotlinx.serialization.Serializable

@Serializable enum class OrgType { PYME, CARRIER, OPERATOR }

@Serializable enum class Role { ADMIN, OPS, VIEWER, DRIVER }

@Serializable enum class CargoType { GENERAL, FRAGILE, PERISHABLE, HAZARDOUS, BULK, OVERSIZED }

/** Estado de la solicitud de carga. */
@Serializable enum class RequestStatus { PUBLISHED, MATCHED, CANCELLED }

@Serializable enum class AuctionStatus { OPEN, CLOSED, CANCELLED }

@Serializable enum class CloseReason { TIMEOUT, MAX_BIDS, ACCEPTED, CANCELLED }

@Serializable enum class BidStatus { PENDING, ACCEPTED, REJECTED }

@Serializable enum class ShipmentStatus { PENDING, IN_TRANSIT, DELIVERED, CANCELLED }

@Serializable enum class StopStatus { PENDING, ARRIVED, COMPLETED }

@Serializable enum class StopType { PICKUP, DELIVERY }

@Serializable enum class VehicleType { VAN, TRUCK_SMALL, TRUCK_MEDIUM, TRUCK_LARGE, PLATFORM, REFRIGERATED, TANKER }

@Serializable enum class VehicleStatus { AVAILABLE, IN_ROUTE, MAINTENANCE }

@Serializable enum class DriverStatus { AVAILABLE, ON_DUTY, OFF_DUTY }

// --- Fase 2 del contrato ---
@Serializable enum class DocumentType { SERVICE_ORDER, REMISSION_GUIDE, RECEIPT, DELIVERY_PHOTO, SIGNATURE, OTHER }

@Serializable enum class IncidentCategory { CARGO_DAMAGE, DELAY, LOST_CARGO, WRONG_DELIVERY, DRIVER_CONDUCT, OTHER }

@Serializable enum class IncidentStatus { OPEN, IN_REVIEW, RESOLVED, CLOSED }

@Serializable enum class CounterOfferStatus { PENDING, ACCEPTED, REJECTED, EXPIRED }

@Serializable
enum class NotificationType {
    NEW_BID, BID_ACCEPTED, BID_REJECTED, COUNTER_OFFER, NEW_MESSAGE,
    SHIPMENT_STATUS_CHANGE, INCIDENT_REPORTED, INCIDENT_RESOLVED, NEW_REVIEW,
    // Fase 4
    TRIP_BOOKING_REQUESTED, TRIP_BOOKING_CONFIRMED, TRIP_BOOKING_REJECTED, TRIP_BOOKING_CANCELLED, TRIP_CANCELLED,
    SMART_LOAD_SUGGESTED, SMART_LOAD_ACCEPTED,
    /** Valor que este cliente no conoce (se mapea con `coerceInputValues`). */
    UNKNOWN,
}

@Serializable enum class NotificationTarget { REQUEST, BID, COUNTER_OFFER, SHIPMENT, INCIDENT, REVIEW, TRIP, SMART_LOAD }

@Serializable enum class ReviewDirection { PYME_TO_CARRIER, CARRIER_TO_PYME }

// --- Fase 4: viajes publicados, reservas, retornos y Smart Load ---
@Serializable enum class TripStatus { OPEN, FILLING, CLOSED, COMPLETED, CANCELLED }

@Serializable enum class BookingStatus { PENDING, CONFIRMED, REJECTED, CANCELLED }

/** `PER_KG`: `rate` = S/ por kg. `PER_TRIP`: `rate` = tarifa de todo el tramo con la capacidad completa. */
@Serializable enum class TripPricingMode { PER_KG, PER_TRIP }

@Serializable enum class SmartLoadStatus { PENDING, ACCEPTED, REJECTED, EXPIRED }
