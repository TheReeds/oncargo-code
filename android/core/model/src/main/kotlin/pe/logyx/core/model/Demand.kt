package pe.logyx.core.model

import kotlinx.serialization.Serializable

// Ola 2, zona P (demanda / PYME): publicar desde el móvil (2.16), libreta de direcciones (1.2), plantillas (2.1).
// Contrato: `docs/api/openapi.yaml` (`CreateRequest`, `Quote`, `PlaceView`, `TemplateView`, `PublishedFromTemplate`).

/**
 * `POST /shipment-requests` (cabecera `Idempotency-Key`). Los contactos y ventanas (`HH:mm`) son **privados** hasta adjudicar
 * (RN07 tras el match: son campos propios, no texto libre). La referencia, el valor declarado y los textos libres de la carga
 * sí pasan por RN07 en el servidor (`LOGYX-NEG-001`, `field` = nombre).
 */
@Serializable
data class CreateRequestBody(
    val originCity: String,
    val originAddress: String,
    val destinationCity: String,
    val destinationAddress: String,
    val cargoType: CargoType,
    val cargoDescription: String,
    val weightKg: Double,
    val volumeM3: Double? = null,
    val pickupDate: String,
    val customerReference: String? = null,
    val declaredValue: Double? = null,
    val pickupContactName: String? = null,
    val pickupContactPhone: String? = null,
    val pickupWindowFrom: String? = null,
    val pickupWindowTo: String? = null,
    val pickupInstructions: String? = null,
    val deliveryContactName: String? = null,
    val deliveryContactPhone: String? = null,
    val deliveryWindowFrom: String? = null,
    val deliveryWindowTo: String? = null,
    val deliveryInstructions: String? = null,
    val originPlaceId: String? = null,
    val destinationPlaceId: String? = null,
    /** Avisa a los transportistas preferidos al publicar (2.9). */
    val invitePreferred: Boolean? = null,
)

/** `POST /pricing/calculate`: la cotización en vivo del formulario. */
@Serializable
data class QuoteRequestBody(val originCity: String, val destinationCity: String, val weightKg: Double, val cargoType: CargoType)

@Serializable
data class QuoteView(
    val distanceKm: Double = 0.0,
    val fuelCost: Double = 0.0,
    val transportCost: Double = 0.0,
    val tollCost: Double = 0.0,
    val handlingFee: Double = 0.0,
    val suggestedPrice: Double = 0.0,
    val priceFloor: Double = 0.0,
)

@Serializable
enum class PlaceKind { PICKUP, DELIVERY, BOTH }

/** Dirección guardada de la libreta (`GET /places`). Ventana por omisión `HH:mm`. */
@Serializable
data class PlaceView(
    val id: String,
    val label: String,
    val city: String,
    val address: String,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val openingHours: String? = null,
    val windowFrom: String? = null,
    val windowTo: String? = null,
    val instructions: String? = null,
    val kind: PlaceKind = PlaceKind.BOTH,
    val archived: Boolean = false,
    val lastUsedAt: String? = null,
)

/** Programación de una plantilla (`frequency` WEEKLY|BIWEEKLY; `pickupWeekday` 1 = lunes … 7 = domingo; `mode` PUBLISH|DRAFT). */
@Serializable
data class TemplateScheduleView(
    val frequency: String = "WEEKLY",
    val pickupWeekday: Int = 1,
    val leadDays: Int = 0,
    val endsOn: String? = null,
    val skipHolidays: Boolean = true,
    val mode: String = "PUBLISH",
    val paused: Boolean = false,
    val finished: Boolean = false,
    val nextPickupDate: String? = null,
    val failures: Int = 0,
)

/** Plantilla de solicitud (`GET /request-templates`). Solo lo que el móvil muestra o precarga. */
@Serializable
data class TemplateView(
    val id: String,
    val name: String,
    val originCity: String,
    val originAddress: String? = null,
    val destinationCity: String,
    val destinationAddress: String? = null,
    val cargoType: CargoType,
    val cargoDescription: String? = null,
    val weightKg: Double,
    val volumeM3: Double? = null,
    val customerReference: String? = null,
    val declaredValue: Double? = null,
    val pickupContactName: String? = null,
    val pickupContactPhone: String? = null,
    val pickupWindowFrom: String? = null,
    val pickupWindowTo: String? = null,
    val pickupInstructions: String? = null,
    val deliveryContactName: String? = null,
    val deliveryContactPhone: String? = null,
    val deliveryWindowFrom: String? = null,
    val deliveryWindowTo: String? = null,
    val deliveryInstructions: String? = null,
    val originPlaceId: String? = null,
    val destinationPlaceId: String? = null,
    val invitePreferred: Boolean = false,
    val archived: Boolean = false,
    val lastPublishedAt: String? = null,
    val schedule: TemplateScheduleView? = null,
)

/** `POST /request-templates/{id}/publish` (cabecera `Idempotency-Key`): fecha de recojo no pasada. */
@Serializable
data class PublishTemplateBody(val pickupDate: String)

@Serializable
data class PublishedFromTemplate(
    val requestId: String,
    val code: String? = null,
    val pickupDate: String? = null,
    val replayed: Boolean = false,
)

/** Datos de una solicitud para precargar el formulario de «Nueva solicitud» al duplicar (todo salvo la fecha). */
fun RequestView.nextWeekPickup(today: java.time.LocalDate): String {
    var d = pickupDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }?.plusDays(7) ?: today
    while (d.isBefore(today)) d = d.plusDays(7)
    return d.toString()
}
