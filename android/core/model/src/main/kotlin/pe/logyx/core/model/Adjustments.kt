package pe.logyx.core.model

import kotlinx.serialization.Serializable

// Ola 2, zona X (roadmap 2.6 y 2.7): ajustes del envío, precio final e incidencias v2. Espejo de AdjustmentDtos/IncidentDtos.

/**
 * Tipo de ajuste: `WEIGHT_DIFF` peso real distinto del declarado (puede ser descuento), `DETENTION` estadía, `TOLL` peajes,
 * `HANDLING` maniobras, `OTHER` otro (con nota). `UNKNOWN` = valor que este cliente no conoce.
 */
@Serializable enum class AdjustmentType { WEIGHT_DIFF, DETENTION, TOLL, HANDLING, OTHER, UNKNOWN }

@Serializable enum class AdjustmentStatus { PENDING, ACCEPTED, REJECTED, WITHDRAWN, UNKNOWN }

/**
 * Desglose de un precio con IGV (ADR-0010). La PYME y el conductor reciben solo IGV; el transportista además la comisión,
 * su IGV y `carrierNet` («cobras S/ X neto»).
 */
@Serializable
data class PriceBreakdownView(
    val amount: Double? = null,
    val vatRate: Double? = null,
    val netOfVat: Double? = null,
    val vat: Double? = null,
    val commissionRate: Double? = null,
    val commission: Double? = null,
    val commissionVat: Double? = null,
    val carrierNet: Double? = null,
)

/**
 * Un ajuste del precio del envío. Montos con IGV. [escalated]: pasó [respondBy] sin respuesta de la PYME y decide el
 * operador. [decisionNote] = motivo del rechazo (o nota al aceptar).
 */
@Serializable
data class AdjustmentView(
    val id: String,
    val shipmentId: String? = null,
    val stopId: String? = null,
    val type: AdjustmentType = AdjustmentType.UNKNOWN,
    val amount: Double = 0.0,
    val note: String? = null,
    val evidenceDocumentId: String? = null,
    val status: AdjustmentStatus = AdjustmentStatus.UNKNOWN,
    val createdAt: String? = null,
    val respondBy: String? = null,
    val escalated: Boolean = false,
    val escalatedAt: String? = null,
    val decidedAt: String? = null,
    val decisionNote: String? = null,
    val decidedByOrgId: String? = null,
    val requesterOrgId: String? = null,
    val carrierOrgId: String? = null,
) {
    val isPending: Boolean get() = status == AdjustmentStatus.PENDING
}

/** Sugerencia que el transportista puede proponer con un toque (estadía por parada, diferencia de peso). No se cobra sola. */
@Serializable
data class AdjustmentSuggestionView(
    val type: AdjustmentType = AdjustmentType.UNKNOWN,
    val stopId: String? = null,
    val amount: Double = 0.0,
    val detail: String? = null,
)

/** `GET /shipments/{id}/adjustments`: precio final = acordado + aceptados. [suggestions] solo para el transportista. */
@Serializable
data class ShipmentAdjustmentsView(
    val agreedPrice: Double? = null,
    val acceptedTotal: Double? = null,
    val finalPrice: Double? = null,
    val finalPriceBreakdown: PriceBreakdownView? = null,
    val adjustments: List<AdjustmentView> = emptyList(),
    val suggestions: List<AdjustmentSuggestionView> = emptyList(),
)

/**
 * `POST /shipments/{id}/adjustments` (transportista o conductor; cabecera `Idempotency-Key` opcional). `amount` con IGV,
 * distinto de 0; negativo solo en `WEIGHT_DIFF`/`OTHER` (`ADJ-003`); nota obligatoria en `OTHER` (`ADJ-004`, RN07).
 */
@Serializable
data class ProposeAdjustmentRequest(
    val type: AdjustmentType,
    val amount: Double,
    val note: String? = null,
    val stopId: String? = null,
    val evidenceDocumentId: String? = null,
)

/** `POST /adjustments/{id}/accept|reject`: al rechazar la nota es obligatoria (`LOGYX-ADJ-004`, field `note`). */
@Serializable
data class DecideAdjustmentRequest(val note: String? = null)

/** Adjunto del alta de una incidencia (V23, solo en el detalle). */
@Serializable
data class IncidentAttachmentView(
    val documentId: String,
    val fileName: String? = null,
    val type: String? = null,
    val contentType: String? = null,
)

/** Códigos de error de ajustes (2.6). */
object AdjustmentErrors {
    /** Nota obligatoria (al rechazar u `OTHER`): field `note`. */
    const val NOTE_REQUIRED = "LOGYX-ADJ-004"
    const val ALREADY_DECIDED = "LOGYX-ADJ-007"
}

/**
 * Informativa (F21, RN11): no congela el viaje. Espejo de `IncidentCategory.blocking()` del backend; el servidor manda
 * (`IncidentView.blocking`), esto solo sirve para avisar antes de reportar.
 */
val IncidentCategory.informative: Boolean
    get() = this == IncidentCategory.DELAY || this == IncidentCategory.ROAD_BLOCKED || this == IncidentCategory.PAYMENT_DISPUTE

/** Categorías que se pueden elegir al reportar (todas menos `UNKNOWN`). */
val reportableIncidentCategories: List<IncidentCategory> = IncidentCategory.entries.filter { it != IncidentCategory.UNKNOWN }

/** Las paradas que registró la oficina del transportista (2.5): la llegada o el cierre traen motivo. */
val StopView.registeredByOffice: Boolean
    get() = !arrivalOfficeReason.isNullOrBlank() || !completionOfficeReason.isNullOrBlank()
