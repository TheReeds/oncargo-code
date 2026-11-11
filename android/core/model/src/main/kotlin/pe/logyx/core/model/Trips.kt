package pe.logyx.core.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

// Fase 4 (docs/api/CHANGELOG.md): viajes publicados, reservas de espacio, cargas de retorno y Smart Load.

@Serializable
data class TripView(
    val id: String,
    val carrierOrgId: String? = null,
    val carrierName: String? = null,
    val carrierTrustScore: Double? = null,
    /** Solo para el transportista dueño. */
    val vehicleId: String? = null,
    val vehicleType: VehicleType? = null,
    val vehiclePlate: String? = null,
    val originCity: String,
    val destinationCity: String,
    val departureAt: String,
    val capacityKg: Double,
    val confirmedWeightKg: Double = 0.0,
    val remainingKg: Double = 0.0,
    val pricingMode: TripPricingMode,
    val rate: Double,
    val notes: String? = null,
    val status: TripStatus,
    val createdAt: String? = null,
    /** Solo para el transportista dueño. */
    val pendingBookings: Int? = null,
    val confirmedBookings: Int? = null,
)

@Serializable
data class CreateTripRequest(
    val originCity: String,
    val destinationCity: String,
    /** ISO-8601 con offset, p. ej. `2026-11-14T08:00:00-05:00`. */
    val departureAt: String,
    val vehicleId: String,
    val capacityKg: Double,
    val pricingMode: TripPricingMode,
    val rate: Double,
    val notes: String? = null,
)

@Serializable
data class TripBookingView(
    val id: String,
    val tripId: String,
    val status: BookingStatus,
    val requesterOrgId: String? = null,
    val requesterName: String? = null,
    val carrierOrgId: String? = null,
    val carrierName: String? = null,
    val originCity: String? = null,
    val destinationCity: String? = null,
    val departureAt: String? = null,
    val weightKg: Double,
    val cargoType: CargoType? = null,
    val cargoDescription: String? = null,
    val pickupAddress: String? = null,
    val deliveryAddress: String? = null,
    val price: Double? = null,
    val decisionNote: String? = null,
    val requestId: String? = null,
    val shipmentId: String? = null,
    val createdAt: String? = null,
    val decidedAt: String? = null,
)

@Serializable
data class CreateTripBookingRequest(
    val weightKg: Double,
    val cargoDescription: String,
    val cargoType: CargoType,
    val pickupAddress: String,
    val deliveryAddress: String,
)

@Serializable
data class RejectBookingRequest(val reason: String? = null)

@Serializable
data class ReturnLoadView(
    val requestId: String,
    val requesterOrgId: String? = null,
    val requesterName: String? = null,
    val shipmentId: String? = null,
    /** Vehículo con el que se sugiere ofertar. */
    val vehicleId: String,
    val vehiclePlate: String? = null,
    val fromCity: String? = null,
    val shipmentDelivered: Boolean = false,
    val originCity: String,
    val originAddress: String? = null,
    val destinationCity: String,
    val destinationAddress: String? = null,
    val cargoType: CargoType? = null,
    val cargoDescription: String? = null,
    val weightKg: Double,
    val pickupDate: String? = null,
    val suggestedPrice: Double? = null,
    val priceFloor: Double? = null,
    val distanceKm: Double? = null,
    val waitDays: Int? = null,
    val matchScore: Double = 0.0,
    val alreadyBid: Boolean = false,
)

/** Resumen de una carga de retorno dentro de `GET /dashboard/summary`. */
@Serializable
data class ReturnLoadHintView(
    val requestId: String,
    val originCity: String,
    val destinationCity: String,
    val weightKg: Double = 0.0,
    val pickupDate: String? = null,
    val suggestedPrice: Double? = null,
    val distanceKm: Double? = null,
    val matchScore: Double = 0.0,
)

@Serializable
data class SmartLoadItemView(
    val requestId: String,
    val originCity: String,
    val destinationCity: String,
    val weightKg: Double,
    val income: Double = 0.0,
    val pickupDate: String? = null,
    val pickupOrder: Int = 0,
    val deliveryOrder: Int = 0,
)

@Serializable
data class SmartLoadRouteStop(val type: StopType, val city: String, val requestId: String? = null)

@Serializable
data class SmartLoadView(
    val id: String,
    val vehicleId: String? = null,
    val vehicleType: VehicleType? = null,
    val vehiclePlate: String? = null,
    val capacityKg: Double = 0.0,
    val totalWeightKg: Double = 0.0,
    val combinedIncome: Double = 0.0,
    val occupancyPct: Double = 0.0,
    val status: SmartLoadStatus,
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val decidedAt: String? = null,
    val items: List<SmartLoadItemView> = emptyList(),
    val route: List<SmartLoadRouteStop> = emptyList(),
)

/** Filtros de la búsqueda de viajes de la PYME (todos opcionales). Fechas ISO `yyyy-MM-dd` (día de salida, Lima). */
data class TripFilter(
    val originCity: String? = null,
    val destinationCity: String? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
    val minWeightKg: Double? = null,
) {
    val activeCount: Int
        get() = listOfNotNull(originCity, destinationCity, fromDate, minWeightKg).size
}

/**
 * Precio de una reserva (espejo de `TripPricingPolicy`): `PER_KG` = tarifa × kg; `PER_TRIP` = tarifa × kg / capacidad.
 * 2 decimales y mínimo S/ 1.00. Es una estimación: el servidor calcula el precio final.
 */
fun TripView.priceFor(weightKg: Double): Double {
    val raw = when (pricingMode) {
        TripPricingMode.PER_KG -> rate * weightKg
        TripPricingMode.PER_TRIP -> if (capacityKg > 0) rate * weightKg / capacityKg else rate
    }
    return maxOf(1.0, BigDecimal(raw).setScale(2, RoundingMode.HALF_UP).toDouble())
}

/** El viaje admite reservas nuevas desde la app (OPEN/FILLING con espacio). */
val TripView.isBookable: Boolean
    get() = (status == TripStatus.OPEN || status == TripStatus.FILLING) && remainingKg > 0

/** El transportista aún puede cerrar o cancelar el viaje. */
val TripView.isManageable: Boolean
    get() = status == TripStatus.OPEN || status == TripStatus.FILLING

/** La reserva admite acción de la PYME (cancelar). */
val TripBookingView.isCancellable: Boolean
    get() = status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED

/** Parte de la ocupación confirmada del viaje (0..1). */
val TripView.fillRatio: Float
    get() = if (capacityKg <= 0) 0f else (confirmedWeightKg / capacityKg).coerceIn(0.0, 1.0).toFloat()
