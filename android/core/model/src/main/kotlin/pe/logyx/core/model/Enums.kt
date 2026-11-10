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
