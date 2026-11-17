package pe.logyx.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.client.statement.readRawBytes
import pe.logyx.core.model.AcceptResult
import pe.logyx.core.model.BookingStatus
import pe.logyx.core.model.CreateTripBookingRequest
import pe.logyx.core.model.CreateTripRequest
import pe.logyx.core.model.RejectBookingRequest
import pe.logyx.core.model.ReturnLoadView
import pe.logyx.core.model.SmartLoadStatus
import pe.logyx.core.model.SmartLoadView
import pe.logyx.core.model.TripBookingView
import pe.logyx.core.model.TripFilter
import pe.logyx.core.model.TripView
import pe.logyx.core.model.AddCommentRequest
import pe.logyx.core.model.ChatThreadView
import pe.logyx.core.model.CounterOfferView
import pe.logyx.core.model.CursorPage
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.DocumentView
import pe.logyx.core.model.IncidentView
import pe.logyx.core.model.MarkedRead
import pe.logyx.core.model.MessageView
import pe.logyx.core.model.NotificationView
import pe.logyx.core.model.PostMessageRequest
import pe.logyx.core.model.ProposeCounterOfferRequest
import pe.logyx.core.model.ReportIncidentRequest
import pe.logyx.core.model.ReputationView
import pe.logyx.core.model.ReviewView
import pe.logyx.core.model.ShipmentReviewsView
import pe.logyx.core.model.SubmitReviewRequest
import pe.logyx.core.model.UnreadCount
import pe.logyx.core.model.AssignDriverRequest
import pe.logyx.core.model.AuthResponse
import pe.logyx.core.model.BidView
import pe.logyx.core.model.CargoItem
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.DashboardSummary
import pe.logyx.core.model.DriverView
import pe.logyx.core.model.MarketplaceFilter
import pe.logyx.core.model.Page
import pe.logyx.core.model.LoginRequest
import pe.logyx.core.model.PlaceBidRequest
import pe.logyx.core.model.PublicOrganization
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.UserInfo
import pe.logyx.core.model.VehicleView
import java.io.IOException

/**
 * Cliente tipado del contrato `docs/api/openapi.yaml`. Desenvuelve `{data, meta}` y lanza [ApiException]
 * con mensajes en español ante errores de la API o de red.
 * @param onUnauthorized se invoca ante un 401 (token vencido) para cerrar la sesión.
 */
class LogyxApi(
    private val client: HttpClient,
    private val onUnauthorized: () -> Unit = {},
) {
    // --- Auth ---
    suspend fun login(email: String, password: String): AuthResponse =
        post("api/v1/auth/login", LoginRequest(email.trim(), password))

    suspend fun me(): UserInfo = get("api/v1/auth/me")

    // --- Catálogo ---
    suspend fun cities(): List<CityItem> = get("api/v1/catalog/cities")
    suspend fun cargoTypes(): List<CargoItem> = get("api/v1/catalog/cargo-types")
    suspend fun organization(id: String): PublicOrganization = get("api/v1/organizations/$id")

    // --- Marketplace (transportista) ---
    suspend fun marketplace(): List<RequestView> = get("api/v1/shipment-requests")

    /** Marketplace con filtros/orden y paginación en servidor (por omisión 20 por página). */
    suspend fun marketplacePage(filter: MarketplaceFilter = MarketplaceFilter(), page: Int = 0, size: Int = 20): Page<RequestView> {
        val envelope = callEnvelope<List<RequestView>> {
            client.get("api/v1/shipment-requests") {
                filter.originCity?.let { parameter("originCity", it) }
                filter.destinationCity?.let { parameter("destinationCity", it) }
                filter.pickupFrom?.let { parameter("pickupFrom", it) }
                filter.pickupTo?.let { parameter("pickupTo", it) }
                filter.maxWeightKg?.let { parameter("maxWeightKg", it) }
                filter.cargoType?.let { parameter("cargoType", it.name) }
                parameter("sort", filter.sort.param)
                parameter("page", page)
                parameter("size", size)
            }
        }
        return Page(envelope.data, envelope.meta?.page ?: page, envelope.meta?.size ?: size, envelope.meta?.total ?: envelope.data.size)
    }
    suspend fun myBids(): List<RequestView> = get("api/v1/shipment-requests/my-bids")
    suspend fun request(id: String): RequestView = get("api/v1/shipment-requests/$id")
    suspend fun placeBid(requestId: String, body: PlaceBidRequest): BidView =
        post("api/v1/shipment-requests/$requestId/bids", body)

    // --- Solicitudes (PYME) ---
    suspend fun myRequests(): List<RequestView> = get("api/v1/shipment-requests/mine")
    suspend fun bids(requestId: String): List<BidView> = get("api/v1/shipment-requests/$requestId/bids")
    suspend fun acceptBid(bidId: String): AcceptResult = post("api/v1/bids/$bidId/accept")
    suspend fun cancelRequest(requestId: String): RequestView = post("api/v1/shipment-requests/$requestId/cancel")

    // --- Flota ---
    suspend fun vehicles(): List<VehicleView> = get("api/v1/vehicles")
    suspend fun drivers(): List<DriverView> = get("api/v1/drivers")

    // --- Envíos ---
    suspend fun shipments(): List<ShipmentView> = get("api/v1/shipments")
    suspend fun shipment(id: String): ShipmentView = get("api/v1/shipments/$id")
    suspend fun assignDriver(shipmentId: String, driverId: String): ShipmentView =
        put("api/v1/shipments/$shipmentId/driver", AssignDriverRequest(driverId))

    suspend fun driverShipments(): List<ShipmentView> = get("api/v1/driver/shipments")

    /** Envío activo del conductor; lanza [ApiException] 404 `LOGYX-SHP-002` si no tiene. */
    suspend fun driverActiveShipment(): ShipmentView = get("api/v1/driver/shipments/active")

    // --- Dashboard ---
    suspend fun dashboardSummary(): DashboardSummary = get("api/v1/dashboard/summary")
    suspend fun arriveAtStop(shipmentId: String, stopId: String): ShipmentView =
        post("api/v1/shipments/$shipmentId/stops/$stopId/arrive")

    suspend fun completeStop(shipmentId: String, stopId: String): ShipmentView =
        post("api/v1/shipments/$shipmentId/stops/$stopId/complete")

    // --- Documentos y evidencia ---
    /**
     * Sube un archivo del envío (`multipart/form-data`: `file`, `type`, `stopId?`).
     * @param onProgress bytes enviados / totales (total puede ser null si se desconoce).
     */
    suspend fun uploadDocument(
        shipmentId: String,
        bytes: ByteArray,
        fileName: String,
        contentType: String,
        type: DocumentType,
        stopId: String? = null,
        onProgress: (sent: Long, total: Long?) -> Unit = { _, _ -> },
    ): DocumentView = call {
        client.post("api/v1/shipments/$shipmentId/documents") {
            timeout { requestTimeoutMillis = 120_000 }
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file", bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, contentType)
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                        append("type", type.name)
                        stopId?.let { append("stopId", it) }
                    },
                ),
            )
            onUpload { sent, total -> onProgress(sent, total) }
        }
    }

    suspend fun shipmentDocuments(shipmentId: String): List<DocumentView> = get("api/v1/shipments/$shipmentId/documents")

    /** Descarga el binario de un documento (mismo Bearer). */
    suspend fun documentFile(documentId: String): ByteArray {
        val response = try {
            client.get("api/v1/documents/$documentId/file") { header(HttpHeaders.Accept, "*/*") } // el binario responde 406 si solo se acepta JSON
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ApiException(0, "NETWORK", "No pudimos conectar con OnCargo. Revisa tu conexión e inténtalo de nuevo.")
        }
        if (!response.status.isSuccess()) throw response.toApiException()
        return response.readRawBytes()
    }

    // --- Incidencias ---
    suspend fun reportIncident(shipmentId: String, body: ReportIncidentRequest): IncidentView =
        post("api/v1/shipments/$shipmentId/incidents", body)

    suspend fun shipmentIncidents(shipmentId: String): List<IncidentView> = get("api/v1/shipments/$shipmentId/incidents")

    suspend fun incident(id: String): IncidentView = get("api/v1/incidents/$id")

    /** Mis incidencias (paginado); [active] true = solo OPEN/IN_REVIEW. */
    suspend fun incidents(active: Boolean? = null, page: Int = 0, size: Int = 20): pe.logyx.core.model.Page<IncidentView> {
        val env = callEnvelope<List<IncidentView>> {
            client.get("api/v1/incidents") {
                active?.let { parameter("active", it) }
                parameter("page", page); parameter("size", size)
            }
        }
        return pe.logyx.core.model.Page(env.data, env.meta?.page ?: page, env.meta?.size ?: size, env.meta?.total ?: env.data.size)
    }

    suspend fun commentIncident(id: String, body: String): IncidentView =
        post("api/v1/incidents/$id/comments", AddCommentRequest(body))

    // --- Negociación ---
    suspend fun threads(requestId: String): List<ChatThreadView> = get("api/v1/shipment-requests/$requestId/threads")

    suspend fun messages(requestId: String, carrierOrgId: String, before: String? = null, size: Int = 30): CursorPage<MessageView> {
        val env = callEnvelope<List<MessageView>> {
            client.get("api/v1/shipment-requests/$requestId/threads/$carrierOrgId/messages") {
                before?.let { parameter("before", it) }
                parameter("size", size)
            }
        }
        return CursorPage(env.data, env.meta?.nextCursor)
    }

    suspend fun postMessage(requestId: String, carrierOrgId: String, body: String): MessageView =
        post("api/v1/shipment-requests/$requestId/threads/$carrierOrgId/messages", PostMessageRequest(body))

    suspend fun counterOffers(bidId: String): List<CounterOfferView> = get("api/v1/bids/$bidId/counter-offers")

    suspend fun proposeCounterOffer(bidId: String, amount: Double, message: String?): CounterOfferView =
        post("api/v1/bids/$bidId/counter-offers", ProposeCounterOfferRequest(amount, message?.takeIf { it.isNotBlank() }))

    suspend fun acceptCounterOffer(id: String): CounterOfferView = post("api/v1/counter-offers/$id/accept")
    suspend fun rejectCounterOffer(id: String): CounterOfferView = post("api/v1/counter-offers/$id/reject")

    // --- Reputación ---
    suspend fun shipmentReviews(shipmentId: String): ShipmentReviewsView = get("api/v1/shipments/$shipmentId/reviews")
    suspend fun submitReview(shipmentId: String, body: SubmitReviewRequest): ReviewView =
        post("api/v1/shipments/$shipmentId/reviews", body)

    suspend fun reputation(organizationId: String): ReputationView = get("api/v1/organizations/$organizationId/reputation")

    suspend fun organizationReviews(organizationId: String, page: Int = 0, size: Int = 20): pe.logyx.core.model.Page<ReviewView> {
        val env = callEnvelope<List<ReviewView>> {
            client.get("api/v1/organizations/$organizationId/reviews") { parameter("page", page); parameter("size", size) }
        }
        return pe.logyx.core.model.Page(env.data, env.meta?.page ?: page, env.meta?.size ?: size, env.meta?.total ?: env.data.size)
    }

    // --- Notificaciones ---
    suspend fun notifications(unreadOnly: Boolean = false, page: Int = 0, size: Int = 20): pe.logyx.core.model.Page<NotificationView> {
        val env = callEnvelope<List<NotificationView>> {
            client.get("api/v1/notifications") {
                if (unreadOnly) parameter("unread", true)
                parameter("page", page); parameter("size", size)
            }
        }
        return pe.logyx.core.model.Page(env.data, env.meta?.page ?: page, env.meta?.size ?: size, env.meta?.total ?: env.data.size)
    }

    suspend fun unreadCount(): UnreadCount = get("api/v1/notifications/unread-count")
    suspend fun markNotificationRead(id: String): NotificationView = post("api/v1/notifications/$id/read")
    suspend fun markAllNotificationsRead(): MarkedRead = post("api/v1/notifications/read-all")

    // --- Viajes publicados (fase 4) ---
    suspend fun createTrip(body: CreateTripRequest): TripView = post("api/v1/trips", body)
    suspend fun myTrips(): List<TripView> = get("api/v1/trips/mine")
    suspend fun trip(id: String): TripView = get("api/v1/trips/$id")
    suspend fun closeTrip(id: String): TripView = post("api/v1/trips/$id/close")
    suspend fun cancelTrip(id: String): TripView = post("api/v1/trips/$id/cancel")
    suspend fun tripBookings(tripId: String): List<TripBookingView> = get("api/v1/trips/$tripId/bookings")
    suspend fun confirmBooking(id: String): TripBookingView = post("api/v1/trip-bookings/$id/confirm")
    suspend fun rejectBooking(id: String, reason: String?): TripBookingView =
        post("api/v1/trip-bookings/$id/reject", RejectBookingRequest(reason?.takeIf { it.isNotBlank() }))

    /** Búsqueda de viajes abiertos (PYME) con filtros y paginación en servidor. */
    suspend fun searchTrips(filter: TripFilter = TripFilter(), page: Int = 0, size: Int = 20): pe.logyx.core.model.Page<TripView> {
        val env = callEnvelope<List<TripView>> {
            client.get("api/v1/trips") {
                filter.originCity?.let { parameter("originCity", it) }
                filter.destinationCity?.let { parameter("destinationCity", it) }
                filter.fromDate?.let { parameter("fromDate", it) }
                filter.toDate?.let { parameter("toDate", it) }
                filter.minWeightKg?.let { parameter("minWeightKg", it) }
                parameter("page", page); parameter("size", size)
            }
        }
        return pe.logyx.core.model.Page(env.data, env.meta?.page ?: page, env.meta?.size ?: size, env.meta?.total ?: env.data.size)
    }

    suspend fun bookTrip(tripId: String, body: CreateTripBookingRequest): TripBookingView = post("api/v1/trips/$tripId/bookings", body)
    suspend fun myBookings(status: BookingStatus? = null): List<TripBookingView> = call {
        client.get("api/v1/trip-bookings/mine") { status?.let { parameter("status", it.name) } }
    }
    suspend fun cancelBooking(id: String): TripBookingView = post("api/v1/trip-bookings/$id/cancel")

    // --- Cargas de retorno y Smart Load (fase 4) ---
    suspend fun returnLoads(): List<ReturnLoadView> = get("api/v1/return-loads")
    suspend fun smartLoads(status: SmartLoadStatus? = null): List<SmartLoadView> = call {
        client.get("api/v1/smart-loads") { status?.let { parameter("status", it.name) } }
    }
    suspend fun generateSmartLoads(): List<SmartLoadView> = post("api/v1/smart-loads/generate")
    suspend fun acceptSmartLoad(id: String): SmartLoadView = post("api/v1/smart-loads/$id/accept")
    suspend fun rejectSmartLoad(id: String): SmartLoadView = post("api/v1/smart-loads/$id/reject")

    // --- Infraestructura ---
    private suspend inline fun <reified T> get(path: String): T = call { client.get(path) }

    private suspend inline fun <reified T> post(path: String): T = call { client.post(path) }

    private suspend inline fun <reified T, reified B : Any> post(path: String, body: B): T = call {
        client.post(path) { contentType(ContentType.Application.Json); setBody(body) }
    }

    private suspend inline fun <reified T, reified B : Any> put(path: String, body: B): T = call {
        client.put(path) { contentType(ContentType.Application.Json); setBody(body) }
    }

    @Suppress("unused")
    private suspend inline fun <reified T, reified B : Any> patch(path: String, body: B): T = call {
        client.patch(path) { contentType(ContentType.Application.Json); setBody(body) }
    }

    private suspend inline fun <reified T> call(request: () -> HttpResponse): T = callEnvelope<T>(request).data

    private suspend inline fun <reified T> callEnvelope(request: () -> HttpResponse): ApiEnvelope<T> {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ApiException(0, "NETWORK", "No pudimos conectar con OnCargo. Revisa tu conexión e inténtalo de nuevo.")
        }
        if (!response.status.isSuccess()) throw response.toApiException()
        return try {
            response.body<ApiEnvelope<T>>()
        } catch (e: SerializationException) {
            throw ApiException(response.status.value, "PARSE", "Respuesta inesperada del servidor.")
        }
    }

    private suspend fun HttpResponse.toApiException(): ApiException {
        val status = this.status.value
        if (status == 401) onUnauthorized()
        val parsed = runCatching { LogyxJson.decodeFromString<ApiErrorEnvelope>(bodyAsText()).error }.getOrNull()
        val message = parsed?.message?.takeIf { it.isNotBlank() } ?: defaultMessage(status)
        return ApiException(status, parsed?.code, message, parsed?.field)
    }

    private fun defaultMessage(status: Int) = when (status) {
        400 -> "Los datos enviados no son válidos."
        401 -> "Tu sesión venció. Inicia sesión de nuevo."
        403 -> "No tienes permiso para realizar esta acción."
        404 -> "No encontramos lo que buscas."
        409 -> "La operación ya no es posible; actualiza e inténtalo otra vez."
        in 500..599 -> "OnCargo tuvo un problema. Inténtalo en unos minutos."
        else -> "Ocurrió un error inesperado."
    }
}
