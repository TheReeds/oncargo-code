package pe.logyx.core.model

import kotlinx.serialization.Serializable

// Espejo de docs/api/openapi.yaml. Fechas como String ISO-8601 (se formatean en la capa de UI).
// Las ciudades viajan como código (`LIMA`, `AREQUIPA`...); coordenadas en /api/v1/catalog/cities.

@Serializable
data class UserInfo(
    val id: String,
    val email: String,
    val fullName: String,
    val role: Role,
    val organizationId: String,
    val organizationName: String? = null,
    val organizationType: OrgType,
    val rucVerified: Boolean = false,
    val organizationCity: String? = null,
)

@Serializable
data class AuthResponse(val accessToken: String, val expiresIn: Long = 0, val user: UserInfo)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class CityItem(val code: String, val name: String, val lat: Double, val lng: Double)

@Serializable
data class CargoItem(val code: String, val name: String)

@Serializable
data class PublicOrganization(
    val id: String,
    val type: OrgType,
    val legalName: String,
    val ruc: String? = null,
    val rucVerified: Boolean = false,
    val city: String? = null,
)

@Serializable
data class AuctionView(
    val id: String,
    val status: AuctionStatus,
    val closesAt: String,
    val maxBids: Int = 5,
    val bidCount: Long = 0,
    val closeReason: CloseReason? = null,
)

@Serializable
data class BidView(
    val id: String,
    val requestId: String,
    val carrierOrgId: String? = null,
    val carrierName: String? = null,
    val carrierRucVerified: Boolean = false,
    val vehicleId: String? = null,
    val amount: Double,
    val notes: String? = null,
    val status: BidStatus,
    val createdAt: String? = null,
    val vehicleType: VehicleType? = null,
    val vehiclePlate: String? = null,
)

@Serializable
data class RequestView(
    val id: String,
    val requesterOrgId: String? = null,
    val requesterName: String? = null,
    val originCity: String,
    val originAddress: String? = null,
    val destinationCity: String,
    val destinationAddress: String? = null,
    val cargoType: CargoType,
    val cargoDescription: String? = null,
    val weightKg: Double,
    val volumeM3: Double? = null,
    val pickupDate: String? = null,
    val distanceKm: Double? = null,
    val suggestedPrice: Double? = null,
    val priceFloor: Double? = null,
    val status: RequestStatus,
    val createdAt: String? = null,
    val auction: AuctionView? = null,
    val myBid: BidView? = null,
    val requesterRucVerified: Boolean = false,
    val minVehicleType: VehicleType? = null,
)

@Serializable
data class PlaceBidRequest(val vehicleId: String, val amount: Double, val notes: String? = null)

@Serializable
data class AcceptResult(
    val shipmentId: String,
    val acceptedBid: BidView? = null,
    val rejectedBidIds: List<String> = emptyList(),
)

@Serializable
data class StopView(
    val id: String,
    val sequence: Int,
    val type: StopType,
    val city: String,
    val address: String? = null,
    val status: StopStatus,
    val arrivedAt: String? = null,
    val completedAt: String? = null,
)

@Serializable
data class ShipmentView(
    val id: String,
    val requestId: String? = null,
    val requesterName: String? = null,
    val carrierName: String? = null,
    val originCity: String,
    val destinationCity: String,
    val cargoDescription: String? = null,
    val weightKg: Double? = null,
    val agreedPrice: Double? = null,
    val vehiclePlate: String? = null,
    val driverId: String? = null,
    val driverName: String? = null,
    val status: ShipmentStatus,
    val createdAt: String? = null,
    val deliveredAt: String? = null,
    val stops: List<StopView> = emptyList(),
)

@Serializable
data class AssignDriverRequest(val driverId: String)

@Serializable
data class VehicleView(
    val id: String,
    val plate: String,
    val type: VehicleType,
    val maxWeightKg: Double,
    val maxVolumeM3: Double? = null,
    val status: VehicleStatus,
)

@Serializable
data class DriverView(
    val id: String,
    val fullName: String,
    val email: String? = null,
    val licenseNumber: String? = null,
    val status: DriverStatus,
)

/** Resumen de `GET /dashboard/summary`; los campos que no aplican al rol vienen null. */
@Serializable
data class DashboardSummary(
    val role: String? = null,
    // PYME
    val activeRequests: Int? = null,
    val bidsToReview: Int? = null,
    val shipmentsInTransit: Int? = null,
    // CARRIER
    val compatibleLoads: Int? = null,
    val pendingBids: Int? = null,
    val activeShipments: Int? = null,
    // ambos
    val deliveredThisMonth: Int? = null,
)

/** Orden del marketplace (`sort`). */
enum class MarketplaceSort(val param: String) { PICKUP("pickup"), PRICE_DESC("price_desc"), RECENT("recent") }

/** Filtros del marketplace enviados al servidor (todos opcionales). Fechas ISO `yyyy-MM-dd`. */
data class MarketplaceFilter(
    val originCity: String? = null,
    val destinationCity: String? = null,
    val pickupFrom: String? = null,
    val pickupTo: String? = null,
    val maxWeightKg: Double? = null,
    val cargoType: CargoType? = null,
    val sort: MarketplaceSort = MarketplaceSort.PICKUP,
)

/** Página de resultados con `meta.page/size/total` (total sin paginar). */
data class Page<T>(val items: List<T>, val page: Int, val size: Int, val total: Int) {
    val hasMore: Boolean get() = (page + 1) * size < total
}

/** Punto geográfico para mapas. */
data class GeoPoint(val lat: Double, val lng: Double)

fun CityItem.toGeoPoint() = GeoPoint(lat, lng)
