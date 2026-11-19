package pe.logyx.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Mensaje del stream `GET /api/v1/events/stream` (schema `RealtimeStreamEvent`, ADR-0007). Payload mínimo: ids,
 * estados y contadores; la app vuelve a pedir por REST lo que muestra. Ignora los `type` que no conozcas.
 */
@Serializable
data class RealtimeEvent(
    val type: String,
    val seq: Long? = null,
    val occurredAt: String? = null,
    val data: JsonObject = JsonObject(emptyMap()),
) {
    fun string(key: String): String? = (data[key] as? JsonPrimitive)?.contentOrNull
    fun long(key: String): Long? = (data[key] as? JsonPrimitive)?.longOrNull
}

/** Estado de la conexión en vivo (para el indicador y para activar el polling de respaldo). */
enum class RealtimeStatus { CONNECTING, LIVE, RECONNECTING, OFFLINE }

/** Tipos del stream (`docs/api/CHANGELOG.md`, fase 6). */
object RealtimeTypes {
    const val BID_PLACED = "bid.placed"
    const val AUCTION_UPDATED = "auction.updated"
    const val BID_ACCEPTED = "bid.accepted"
    const val BID_REJECTED = "bid.rejected"
    const val REQUEST_MATCHED = "request.matched"
    const val COUNTER_OFFER_UPDATED = "counter_offer.updated"
    const val MESSAGE_POSTED = "message.posted"
    const val SHIPMENT_STATUS_CHANGED = "shipment.status_changed"
    const val SHIPMENT_STOP_UPDATED = "shipment.stop_updated"
    const val SHIPMENT_DRIVER_ASSIGNED = "shipment.driver_assigned"
    /** V23 (2.6): ajuste del envío propuesto, decidido o retirado (payload `shipmentId`, `adjustmentId`, `status`). */
    const val SHIPMENT_ADJUSTMENT_UPDATED = "shipment.adjustment_updated"
    const val INCIDENT_REPORTED = "incident.reported"
    const val INCIDENT_UPDATED = "incident.updated"
    const val NOTIFICATION_CREATED = "notification.created"
    const val REVIEW_SUBMITTED = "review.submitted"
    const val TRIP_BOOKING_UPDATED = "trip_booking.updated"
    const val TRIP_CANCELLED = "trip.cancelled"
    const val SMART_LOAD_SUGGESTED = "smart_load.suggested"
    const val SMART_LOAD_ACCEPTED = "smart_load.accepted"
    /** Control: se (re)conectó tras una caída o el servidor no pudo reanudar → refresca todo lo que muestras. */
    const val STREAM_RESYNC = "stream.resync"
    const val STREAM_READY = "stream.ready"
    const val STREAM_EXPIRED = "stream.expired"

    val SHIPMENT = setOf(SHIPMENT_STATUS_CHANGED, SHIPMENT_STOP_UPDATED, SHIPMENT_DRIVER_ASSIGNED, SHIPMENT_ADJUSTMENT_UPDATED, INCIDENT_REPORTED, INCIDENT_UPDATED)
    val AUCTION = setOf(BID_PLACED, AUCTION_UPDATED, BID_ACCEPTED, BID_REJECTED, REQUEST_MATCHED, COUNTER_OFFER_UPDATED)
}
