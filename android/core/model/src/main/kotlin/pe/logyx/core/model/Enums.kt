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
}

@Serializable enum class NotificationTarget { REQUEST, BID, COUNTER_OFFER, SHIPMENT, INCIDENT, REVIEW }

@Serializable enum class ReviewDirection { PYME_TO_CARRIER, CARRIER_TO_PYME }
