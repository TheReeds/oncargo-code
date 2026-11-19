package pe.logyx.core.model

import kotlinx.serialization.Serializable

// Ola 1, zona B (roadmap v2 1.3 y 1.11): hora del dispositivo en arrive/complete y enlaces de seguimiento.

/**
 * Cuerpo opcional de `POST /shipments/{id}/stops/{stopId}/arrive|complete`: la hora del dispositivo cuando el conductor
 * tocó el botón (ISO-8601 con offset). El servidor la acepta si no está más de 2 min en el futuro, no tiene más de 48 h y
 * no es anterior al paso previo; si no, 400 `LOGYX-TRK-008` (`field = occurredAt`) y la app repite **sin** ella.
 */
@Serializable
data class StopEventRequest(val occurredAt: String? = null)

/** `POST /shipments/{id}/share-links` (PYME ADMIN/OPS): vigencia opcional de 1 a 30 días. */
@Serializable
data class CreateShareLinkRequest(val expiresInDays: Int? = null)

@Serializable
data class ShareLinkView(
    val id: String,
    /** Últimos caracteres del token, para reconocer el enlace sin exponerlo. */
    val tokenHint: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val revokedAt: String? = null,
    val lastViewedAt: String? = null,
    val views: Int = 0,
)

/** Respuesta 201: el token solo se ve una vez; [url] es la página pública para el cliente final. */
@Serializable
data class ShareLinkCreated(val link: ShareLinkView, val token: String? = null, val url: String)

/** Códigos de error del seguimiento y de los enlaces públicos. */
object TrackingErrors {
    /** Paradas fuera de orden (RN08). */
    const val OUT_OF_ORDER = "LOGYX-TRK-001"
    const val NOT_ARRIVED = "LOGYX-TRK-003"
    const val INVALID_STATE = "LOGYX-TRK-004"
    /** Incidencia activa (RN11). */
    const val ACTIVE_INCIDENT = "LOGYX-TRK-005"
    /** Falta la foto de entrega (RN06). */
    const val MISSING_PHOTO = "LOGYX-TRK-006"
    /** `occurredAt` fuera de rango: repetir sin él (hora del servidor). */
    const val BAD_OCCURRED_AT = "LOGYX-TRK-008"
    const val SHARE_CANCELLED = "LOGYX-SHL-002"
    const val SHARE_DELIVERED_LONG_AGO = "LOGYX-SHL-003"
    const val SHARE_TOO_MANY = "LOGYX-SHL-004"
}

/** La parada trae algún dato de contacto, ventana o instrucciones (solo las partes los reciben). */
val StopView.hasContactInfo: Boolean
    get() = !contactName.isNullOrBlank() || !contactPhone.isNullOrBlank() || !windowFrom.isNullOrBlank() ||
        !windowTo.isNullOrBlank() || !instructions.isNullOrBlank()
