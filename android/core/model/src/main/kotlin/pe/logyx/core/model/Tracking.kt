package pe.logyx.core.model

import kotlinx.serialization.Serializable

// Ola 1, zona B (roadmap v2 1.3 y 1.11): hora del dispositivo en arrive/complete y enlaces de seguimiento.

/**
 * Cuerpo opcional de `POST /shipments/{id}/stops/{stopId}/arrive|complete`: la hora del dispositivo cuando el conductor
 * tocó el botón (ISO-8601 con offset). El servidor la acepta si no está más de 2 min en el futuro, no tiene más de 48 h y
 * no es anterior al paso previo; si no, 400 `LOGYX-TRK-008` (`field = occurredAt`) y la app repite **sin** ella.
 */
@Serializable
data class StopEventRequest(
    val occurredAt: String? = null,
    /** Recojo (2.5): peso medido en kg (obligatorio si `ShipmentView.pickupWeightRequired`, `TRK-010` field `weightKg`). */
    val weightKg: Double? = null,
    /** Entrega (2.5): nombre y DNI (8 dígitos) o CE de quien recibe (`TRK-011`). El servidor los acepta opcionales; la app los pide. */
    val receivedByName: String? = null,
    val receivedByDoc: String? = null,
) {
    /** Sin nada que enviar: la petición va sin cuerpo (hora del servidor). */
    val isEmpty: Boolean get() = occurredAt == null && weightKg == null && receivedByName == null && receivedByDoc == null
}

/** Peso medido válido en el recojo (mismo rango que el backend: 1 a 60 000 kg). */
const val MIN_LOADED_WEIGHT_KG = 1.0
const val MAX_LOADED_WEIGHT_KG = 60_000.0

/** Texto del campo de peso → kg (acepta coma o punto decimal y separadores de miles); null si no es un número válido. */
fun parseWeightKg(text: String): Double? {
    val clean = text.trim().replace(" ", "").let { t ->
        // «2,450.5» o «2450,5»: si hay punto, las comas son de miles; si no, la coma es decimal.
        if (t.contains('.')) t.replace(",", "") else t.replace(',', '.')
    }
    return clean.toDoubleOrNull()?.takeIf { it.isFinite() }
}

fun isValidLoadedWeight(kg: Double?): Boolean = kg != null && kg >= MIN_LOADED_WEIGHT_KG && kg <= MAX_LOADED_WEIGHT_KG

/**
 * Documento de quien recibe: DNI (8 dígitos) o carné de extranjería (9 a 12 letras o dígitos). Se normaliza como el
 * backend (sin espacios, puntos ni guiones, en mayúsculas).
 */
object ReceiverDoc {
    private val DNI = Regex("\\d{8}")
    private val CE = Regex("[A-Z0-9]{9,12}")
    fun normalize(raw: String): String = raw.replace(Regex("[\\s.-]"), "").uppercase()
    fun isValid(raw: String): Boolean = normalize(raw).let { DNI.matches(it) || CE.matches(it) }
}

/** Nombre de quien recibe: 2 a 120 caracteres (`@Size(max = 120)` del backend). */
const val MAX_RECEIVER_NAME = 120

fun isValidReceiverName(name: String): Boolean = name.trim().length in 2..MAX_RECEIVER_NAME

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
    /** Recojo sin la foto de la carga que pidió la PYME (2.5). */
    const val MISSING_PICKUP_PHOTO = "LOGYX-TRK-009"
    /** Peso medido faltante o fuera de rango (field `weightKg`). */
    const val BAD_WEIGHT = "LOGYX-TRK-010"
    /** Nombre o documento de quien recibe inválido (field `receivedByName`/`receivedByDoc`). */
    const val BAD_RECEIVER = "LOGYX-TRK-011"
    const val SHARE_CANCELLED = "LOGYX-SHL-002"
    const val SHARE_DELIVERED_LONG_AGO = "LOGYX-SHL-003"
    const val SHARE_TOO_MANY = "LOGYX-SHL-004"
}

/** La parada trae algún dato de contacto, ventana o instrucciones (solo las partes los reciben). */
val StopView.hasContactInfo: Boolean
    get() = !contactName.isNullOrBlank() || !contactPhone.isNullOrBlank() || !windowFrom.isNullOrBlank() ||
        !windowTo.isNullOrBlank() || !instructions.isNullOrBlank()
