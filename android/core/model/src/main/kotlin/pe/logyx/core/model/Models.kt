package pe.logyx.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Lista de enums tolerante: los valores que este cliente no conoce se descartan en lugar de romper todo el objeto
 * (`coerceInputValues` solo cubre propiedades con valor por omisión, no elementos de una lista).
 */
abstract class LenientEnumListSerializer<E : Enum<E>>(private val entries: List<E>) : KSerializer<List<E>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun deserialize(decoder: Decoder): List<E> =
        delegate.deserialize(decoder).mapNotNull { name -> entries.firstOrNull { it.name == name } }
    override fun serialize(encoder: Encoder, value: List<E>) = delegate.serialize(encoder, value.map { it.name })
}

object VehicleTypeListSerializer : LenientEnumListSerializer<VehicleType>(VehicleType.entries)
object OrgPermissionListSerializer : LenientEnumListSerializer<OrgPermission>(OrgPermission.entries)

// Espejo de docs/api/openapi.yaml. Fechas como String ISO-8601 (se formatean en la capa de UI).
// Las ciudades viajan como código (`LIMA`, `AREQUIPA`...); coordenadas en /api/v1/catalog/cities.

@Serializable
data class UserInfo(
    val id: String,
    val email: String,
    val fullName: String,
    val role: Role = Role.UNKNOWN,
    val organizationId: String,
    val organizationName: String? = null,
    val organizationType: OrgType,
    val rucVerified: Boolean = false,
    val organizationCity: String? = null,
    /** El RUC pasa el dígito verificador (no es lo mismo que [rucVerified], que lo decide el operador). */
    val rucValid: Boolean = false,
    /** RN01: PENDING/REJECTED/SUSPENDED restringen publicar, ofertar y reservar. Null en sesiones guardadas antes de V13. */
    val verificationStatus: VerificationStatus? = null,
    /** Motivo o comentario del operador (rechazo, suspensión, nota de verificación). */
    val verificationNote: String? = null,
    /** Permisos calculados por el backend (V14). Vacío en sesiones antiguas: usa los helpers de `Access.kt`, que derivan del rol. */
    @Serializable(with = OrgPermissionListSerializer::class)
    val permissions: List<OrgPermission> = emptyList(),
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val expiresIn: Long = 0,
    val user: UserInfo,
    /** Solo con `refresh = true` (móvil): token opaco rotativo (~30 días). */
    val refreshToken: String? = null,
    val refreshExpiresIn: Long? = null,
)

@Serializable
data class LoginRequest(val email: String, val password: String, val refresh: Boolean = false)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String? = null)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String, val refresh: Boolean = false)

@Serializable
data class ForgotPasswordRequest(val email: String)

/** Respuesta `{message}` de `forgot-password` (siempre el mismo texto, exista o no el correo). */
@Serializable
data class AuthMessage(val message: String = "")

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
    val rucValid: Boolean = false,
    val verificationStatus: VerificationStatus? = null,
)

@Serializable
data class AuctionView(
    val id: String,
    val status: AuctionStatus = AuctionStatus.UNKNOWN,
    val closesAt: String,
    val maxBids: Int = 5,
    val bidCount: Long = 0,
    val closeReason: CloseReason? = null,
    /** Cuándo cerró la subasta; null si sigue abierta (V15). */
    val closedAt: String? = null,
)

/**
 * Mi posición en la subasta (V15, F15). Solo en las vistas del transportista con una oferta no retirada.
 * Ofertas selladas (RN04): nunca trae montos ni porcentajes exactos de otras ofertas.
 */
@Serializable
data class MyBidStanding(
    val rank: Int = 0,
    val bidCount: Int = 0,
    val isBest: Boolean = false,
    /** Nulo cuando la solicitud ya se adjudicó o canceló (no se insinúa el precio del ganador). */
    val vsBest: BidStandingBand? = null,
    /** La subasta ya cerró: es la posición final. */
    val finalStanding: Boolean = false,
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
    val status: BidStatus = BidStatus.UNKNOWN,
    val createdAt: String? = null,
    val vehicleType: VehicleType? = null,
    val vehiclePlate: String? = null,
    /** Trust Score 0-100 del transportista (RN09; 75 sin calificaciones). */
    val carrierTrustScore: Double? = null,
    val assignedByOperator: Boolean = false,
    /** Solo al ofertar (POST): avisos de agenda del vehículo, p. ej. `LOGYX-CAP-006`. */
    val warnings: List<BidWarning> = emptyList(),
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
    val status: RequestStatus = RequestStatus.UNKNOWN,
    val createdAt: String? = null,
    val auction: AuctionView? = null,
    val myBid: BidView? = null,
    val requesterRucVerified: Boolean = false,
    val minVehicleType: VehicleType? = null,
    val requesterTrustScore: Double? = null,
    /** Tipos de vehículo que pueden llevar la carga (matriz RN02, V13). */
    @Serializable(with = VehicleTypeListSerializer::class)
    val compatibleVehicleTypes: List<VehicleType> = emptyList(),
    /** Motivo de la cancelación (V13). */
    val cancelReason: String? = null,
    /** Id de la solicitud cancelada de la que se republicó esta (V13). */
    val republishedFrom: String? = null,
    // Solo para la PYME dueña (V15): la oferta PENDING más baja.
    val bestBidAmount: Double? = null,
    val bestBidCarrierName: String? = null,
    val bestBidCarrierTrustScore: Double? = null,
    /** Solo para el transportista con oferta no retirada (V15). */
    val myBidStanding: MyBidStanding? = null,
    /** Código legible y único de la solicitud, p. ej. `OC-7K2QX` (V17); el envío lo hereda. */
    val code: String? = null,
    /** Valor declarado de la carga en soles, opcional (V17). */
    val declaredValue: Double? = null,
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
    /** Hora en que ocurrió la llegada (hora del dispositivo del conductor, roadmap 1.11). */
    val arrivedAt: String? = null,
    /** Hora en que ocurrió el cierre de la parada (hora del dispositivo). */
    val completedAt: String? = null,
    // V16–V19 (roadmap 1.3 y 1.11). Todo opcional: un backend anterior no los manda.
    /** Hora del servidor al recibir la llegada (puede ser posterior a [arrivedAt] si el conductor estaba sin señal). */
    val arrivedRecordedAt: String? = null,
    val completedRecordedAt: String? = null,
    /** Contacto en la parada: solo lo ven las partes, el conductor asignado y el operador (null para el resto). */
    val contactName: String? = null,
    /** 9 dígitos, p. ej. `987654321`. */
    val contactPhone: String? = null,
    /** Ventana horaria de atención `HH:mm` (hora local de la parada). */
    val windowFrom: String? = null,
    val windowTo: String? = null,
    val instructions: String? = null,
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
    val status: ShipmentStatus = ShipmentStatus.UNKNOWN,
    val createdAt: String? = null,
    val deliveredAt: String? = null,
    val stops: List<StopView> = emptyList(),
    /** Incidencia activa (RN11): el conductor no puede avanzar paradas. */
    val hasActiveIncident: Boolean = false,
    // V13
    val pickupDate: String? = null,
    val vehicleId: String? = null,
    /** Envíos del mismo viaje o plan Smart Load comparten flota (F11). */
    val loadGroupId: String? = null,
    val cancellationReason: String? = null,
    val cancelledAt: String? = null,
    val cancelledByOrgId: String? = null,
    // V16 (roadmap 1.3/1.4)
    /** Código legible heredado de la solicitud (`OC-7K2QX`); se muestra en lugar del id. */
    val code: String? = null,
    /** Referencia propia de la PYME (OC o pedido, máx. 40). */
    val customerReference: String? = null,
    val requesterOrgId: String? = null,
    val carrierOrgId: String? = null,
)

/** `reason` (V13, F06): opcional; obligatorio al reasignar el conductor con el envío en ruta (`LOGYX-SHP-006`). */
@Serializable
data class AssignDriverRequest(val driverId: String, val reason: String? = null)

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
    /** V14: un conductor desactivado no inicia sesión ni se le puede asignar un envío (409 `LOGYX-FLT-003`). */
    val active: Boolean = true,
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
    // CARRIER (fase 4)
    val openTrips: Int? = null,
    val pendingTripBookings: Int? = null,
    val pendingSmartLoads: Int? = null,
    val returnLoads: Int? = null,
    val topReturnLoads: List<ReturnLoadHintView> = emptyList(),
    // PYME (fase 4)
    val activeTripBookings: Int? = null,
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

// --- Documentos y evidencia (RF37, RF46-48) ---
@Serializable
data class DocumentView(
    val id: String,
    val shipmentId: String? = null,
    val stopId: String? = null,
    val type: DocumentType = DocumentType.OTHER,
    val fileName: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long = 0,
    val uploadedByOrgId: String? = null,
    val createdAt: String? = null,
    val downloadUrl: String? = null,
) {
    val isImage: Boolean get() = contentType?.startsWith("image/") == true
    val isPdf: Boolean get() = contentType == "application/pdf"
}

// --- Incidencias (RF52-55, RN11) ---
@Serializable
data class IncidentCommentView(
    val id: String,
    val authorOrgId: String? = null,
    val authorName: String? = null,
    val body: String,
    val createdAt: String? = null,
)

@Serializable
data class IncidentView(
    val id: String,
    val shipmentId: String,
    val requesterOrgId: String? = null,
    val requesterName: String? = null,
    val carrierOrgId: String? = null,
    val carrierName: String? = null,
    val reportedByOrgId: String? = null,
    val category: IncidentCategory = IncidentCategory.OTHER,
    val description: String,
    val status: IncidentStatus,
    val active: Boolean = false,
    val resolutionNotes: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val resolvedAt: String? = null,
    val comments: List<IncidentCommentView> = emptyList(),
    /** Estado final del envío decidido al resolver o cerrar (V13, RF55). */
    val shipmentOutcome: IncidentShipmentOutcome? = null,
)

@Serializable
data class ReportIncidentRequest(val category: IncidentCategory, val description: String)

@Serializable
data class AddCommentRequest(val body: String)

// --- Negociación (RF25-28, RN07) ---
@Serializable
data class ChatThreadView(
    val requestId: String,
    val carrierOrgId: String,
    val carrierName: String? = null,
    val bidId: String? = null,
    val bidAmount: Double? = null,
    val bidStatus: BidStatus? = null,
    val messageCount: Int = 0,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
)

@Serializable
data class MessageView(
    val id: String,
    val requestId: String? = null,
    val carrierOrgId: String? = null,
    val senderOrgId: String? = null,
    val senderName: String? = null,
    val mine: Boolean = false,
    val body: String,
    val proposedPrice: Double? = null,
    val system: Boolean = false,
    val createdAt: String? = null,
)

@Serializable
data class PostMessageRequest(val body: String)

@Serializable
data class ProposeCounterOfferRequest(val amount: Double, val message: String? = null)

@Serializable
data class CounterOfferView(
    val id: String,
    val bidId: String,
    val requestId: String? = null,
    val carrierOrgId: String? = null,
    val offeredByOrgId: String? = null,
    val previousAmount: Double? = null,
    val amount: Double,
    val message: String? = null,
    val status: CounterOfferStatus = CounterOfferStatus.UNKNOWN,
    val createdAt: String? = null,
    val respondedAt: String? = null,
    /** V13 (RF26): la propuso el transportista (antes solo la PYME). Responde quien la recibió. */
    val proposedByCarrier: Boolean = false,
)

// --- Reputación (RN06, RN09, RF40-45) ---
@Serializable
data class ReviewView(
    val id: String,
    val shipmentId: String? = null,
    val reviewerOrgId: String? = null,
    val reviewerName: String? = null,
    val reviewedOrgId: String? = null,
    val direction: ReviewDirection? = null,
    val punctuality: Int = 0,
    val cargoCondition: Int = 0,
    val communication: Int = 0,
    val rating: Double = 0.0,
    val comment: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class ShipmentReviewsView(
    val canReview: Boolean = false,
    val blockedCode: String? = null,
    val blockedReason: String? = null,
    val reviews: List<ReviewView> = emptyList(),
)

@Serializable
data class SubmitReviewRequest(val punctuality: Int, val cargoCondition: Int, val communication: Int, val comment: String? = null)

@Serializable
data class ReputationView(
    val organizationId: String,
    val legalName: String,
    val type: OrgType,
    val rucVerified: Boolean = false,
    val city: String? = null,
    val trustScore: Double = 75.0,
    val totalReviews: Int = 0,
    val averageRating: Double? = null,
    val averagePunctuality: Double? = null,
    val averageCargoCondition: Double? = null,
    val averageCommunication: Double? = null,
    val priorMean: Double? = null,
    val priorWeight: Int? = null,
    val recentReviews: List<ReviewView> = emptyList(),
)

// --- Notificaciones (RF49, RF51) ---
@Serializable
data class NotificationView(
    val id: String,
    val type: NotificationType = NotificationType.UNKNOWN,
    val title: String,
    val body: String,
    val relatedType: NotificationTarget? = null,
    val relatedId: String? = null,
    val read: Boolean = false,
    val readAt: String? = null,
    val createdAt: String? = null,
    /** V16: el aviso es de algo que tú creaste (tu solicitud, tu oferta): marca «Tuyo». */
    val mine: Boolean = false,
)

@Serializable
data class UnreadCount(val unread: Int = 0)

/** V16: «Pedir acceso» a los ADMIN (`POST /organizations/me/access-requests`). */
@Serializable
data class CreateAccessRequest(val permission: OrgPermission, val reason: String? = null)

@Serializable
data class AccessRequestView(val permission: OrgPermission? = null, val notifiedAdmins: Int = 0)

@Serializable
data class MarkedRead(val updated: Int = 0)

/** Página por cursor (chat): [nextCursor] null = no hay mensajes anteriores. */
data class CursorPage<T>(val items: List<T>, val nextCursor: String?)
