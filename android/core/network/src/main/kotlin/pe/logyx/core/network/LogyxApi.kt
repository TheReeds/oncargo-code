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
import io.ktor.client.statement.request
import pe.logyx.core.model.AuthMessage
import pe.logyx.core.model.ChangePasswordRequest
import pe.logyx.core.model.DriverShipmentScope
import pe.logyx.core.model.ForgotPasswordRequest
import pe.logyx.core.model.LogoutRequest
import pe.logyx.core.model.RefreshRequest
import pe.logyx.core.model.AcceptResult
import pe.logyx.core.model.AcceptSmartLoadRequest
import pe.logyx.core.model.CancelRequestBody
import pe.logyx.core.model.CancelShipmentRequest
import pe.logyx.core.model.ChangeVehicleRequest
import pe.logyx.core.model.RescheduleRequest
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
import pe.logyx.core.model.RouteView
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.UserInfo
import pe.logyx.core.model.VehicleView
import java.io.IOException

/**
 * Cliente tipado del contrato `docs/api/openapi.yaml`. Desenvuelve `{data, meta}` y lanza [ApiException]
 * con mensajes en español ante errores de la API o de red.
 * @param onUnauthorized se invoca ante un 401 que no se pudo resolver renovando la sesión, para cerrarla.
 * @param refresher (móvil, F24) ante un 401 de una llamada autenticada hace **un** intento de renovar la sesión y repite la
 *  petición una vez; si no hay refresher (o la sesión terminó) se cierra la sesión como siempre. Se pide perezosamente
 *  porque el repositorio de sesión también usa esta API.
 */
class LogyxApi(
    private val client: HttpClient,
    private val onUnauthorized: () -> Unit = {},
    private val refresher: () -> SessionRefresher? = { null },
) : RoutesApi {
    // --- Auth ---
    /** [refresh] = true (app móvil): pide `refreshToken` y un access de 1 h en lugar de 8 h sin renovación. */
    suspend fun login(email: String, password: String, refresh: Boolean = false): AuthResponse =
        post("api/v1/auth/login", LoginRequest(email.trim(), password, refresh), auth = false)

    suspend fun me(): UserInfo = get("api/v1/auth/me")

    /** Rota el refresh token (el anterior deja de valer). 401 `LOGYX-AUTH-009` si no sirve. Sin `Authorization`. */
    suspend fun refresh(refreshToken: String): AuthResponse =
        post("api/v1/auth/refresh", RefreshRequest(refreshToken), auth = false)

    /** Cierra la sesión de este dispositivo en el servidor (revoca el refresh). 204. Mejor esfuerzo: el llamador ignora errores. */
    suspend fun logout(refreshToken: String?) {
        callNoContent(auth = false) {
            client.post("api/v1/auth/logout") { contentType(ContentType.Application.Json); setBody(LogoutRequest(refreshToken)) }
        }
    }

    /** Invalida todos los tokens del usuario, incluido el actual. 204. */
    suspend fun logoutAll() {
        callNoContent { client.post("api/v1/auth/logout-all") }
    }

    /**
     * Cambia la contraseña y devuelve una sesión **nueva** (las demás quedan cerradas). Errores: `AUTH-006`
     * (`field=currentPassword`), `AUTH-010` (`field=newPassword`), 429 tras 10 fallos.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String, refresh: Boolean = true): AuthResponse =
        post("api/v1/auth/change-password", ChangePasswordRequest(currentPassword, newPassword, refresh))

    /** 202 con el mismo mensaje exista o no el correo. 429 `AUTH-429` por IP. */
    suspend fun forgotPassword(email: String): AuthMessage =
        post("api/v1/auth/forgot-password", ForgotPasswordRequest(email.trim()), auth = false)

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

    /**
     * Retira mi oferta `PENDING` mientras la subasta acepta ofertas (V13, F08). Errores: `LOGYX-BID-005` (ya no se puede);
     * después no puedo volver a ofertar en esa subasta (`BID-003`). Las contraofertas pendientes pasan a `EXPIRED`.
     */
    suspend fun withdrawBid(bidId: String): BidView = post("api/v1/bids/$bidId/withdraw")

    // --- Solicitudes (PYME) ---
    suspend fun myRequests(): List<RequestView> = get("api/v1/shipment-requests/mine")
    suspend fun bids(requestId: String): List<BidView> = get("api/v1/shipment-requests/$requestId/bids")
    suspend fun acceptBid(bidId: String): AcceptResult = post("api/v1/bids/$bidId/accept")
    /** Cancela la solicitud con motivo opcional (máx. 500, RN07 en `reason`); se avisa a los transportistas que ofertaron (F16). */
    suspend fun cancelRequest(requestId: String, reason: String? = null): RequestView =
        post("api/v1/shipment-requests/$requestId/cancel", CancelRequestBody(reason?.trim()?.takeIf { it.isNotEmpty() }))

    /** Reabre la subasta cerrada por tiempo (`closeReason = TIMEOUT`); fecha de recojo nueva opcional (`yyyy-MM-dd`). `AUC-003`/`REQ-003`. */
    suspend fun reopenRequest(requestId: String, pickupDate: String? = null): RequestView =
        post("api/v1/shipment-requests/$requestId/reopen", RescheduleRequest(pickupDate))

    /** Publica de nuevo una solicitud `CANCELLED`: devuelve la solicitud **nueva** (`republishedFrom` = la anterior). `REQ-004`/`REQ-003`. */
    suspend fun republishRequest(requestId: String, pickupDate: String? = null): RequestView =
        post("api/v1/shipment-requests/$requestId/republish", RescheduleRequest(pickupDate))

    // --- Flota ---
    suspend fun vehicles(): List<VehicleView> = get("api/v1/vehicles")
    suspend fun drivers(): List<DriverView> = get("api/v1/drivers")

    // --- Envíos ---
    suspend fun shipments(): List<ShipmentView> = get("api/v1/shipments")
    suspend fun shipment(id: String): ShipmentView = get("api/v1/shipments/$id")
    /** `reason`: opcional antes del recojo; obligatorio con el envío en ruta (relevo, `LOGYX-SHP-006`). Errores `SHP-004`/`FLT-003` con `field = driverId`. */
    suspend fun assignDriver(shipmentId: String, driverId: String, reason: String? = null): ShipmentView =
        put("api/v1/shipments/$shipmentId/driver", AssignDriverRequest(driverId, reason?.trim()?.takeIf { it.isNotEmpty() }))

    /** Cancela el envío con motivo obligatorio (máx. 500). Las partes, solo antes del recojo (`SHP-003`) y sin incidencia activa (`TRK-005`). */
    suspend fun cancelShipment(shipmentId: String, reason: String): ShipmentView =
        post("api/v1/shipments/$shipmentId/cancel", CancelShipmentRequest(reason.trim()))

    /**
     * Cambia el vehículo (transportista). RN02 `CAP-001..004` y `CAP-005` con `field = vehicleId`, `SHP-007` (mismo vehículo);
     * con el envío en ruta el `reason` es obligatorio (`SHP-006`).
     */
    suspend fun changeVehicle(shipmentId: String, vehicleId: String, reason: String? = null): ShipmentView =
        put("api/v1/shipments/$shipmentId/vehicle", ChangeVehicleRequest(vehicleId, reason?.trim()?.takeIf { it.isNotEmpty() }))

    /** `scope`: ACTIVE (por omisión), HISTORY (entregados y cancelados, recientes primero) o ALL (V15, F22). */
    suspend fun driverShipments(scope: DriverShipmentScope = DriverShipmentScope.ACTIVE): List<ShipmentView> = call {
        client.get("api/v1/driver/shipments") { if (scope != DriverShipmentScope.ACTIVE) parameter("scope", scope.name) }
    }

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
        val response = send(auth = true) {
            client.get("api/v1/documents/$documentId/file") { header(HttpHeaders.Accept, "*/*") } // el binario responde 406 si solo se acepta JSON
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
    /**
     * Acepta el plan y **oferta en lote** (V13, F12): una oferta por carga con el vehículo del plan. `items` vacío = precio sugerido
     * de cada carga; si se manda, es todo o nada (`TRP-011`) y el `requestId` debe ser del plan (`TRP-012`). La respuesta trae `bids`.
     */
    suspend fun acceptSmartLoad(id: String, body: AcceptSmartLoadRequest = AcceptSmartLoadRequest()): SmartLoadView =
        post("api/v1/smart-loads/$id/accept", body)
    suspend fun rejectSmartLoad(id: String): SmartLoadView = post("api/v1/smart-loads/$id/reject")

    // --- Rutas por carretera (ADR-0008) ---
    override suspend fun route(stops: List<String>, optimize: Boolean): RouteView = call {
        client.get("api/v1/routes") {
            parameter("stops", stops.joinToString(","))
            if (optimize) parameter("optimize", true)
        }
    }

    // --- Infraestructura ---
    private suspend inline fun <reified T> get(path: String): T = call { client.get(path) }

    private suspend inline fun <reified T> post(path: String): T = call { client.post(path) }

    private suspend inline fun <reified T, reified B : Any> post(path: String, body: B, auth: Boolean = true): T = call(auth) {
        client.post(path) { contentType(ContentType.Application.Json); setBody(body) }
    }

    private suspend inline fun <reified T, reified B : Any> put(path: String, body: B): T = call {
        client.put(path) { contentType(ContentType.Application.Json); setBody(body) }
    }

    @Suppress("unused")
    private suspend inline fun <reified T, reified B : Any> patch(path: String, body: B): T = call {
        client.patch(path) { contentType(ContentType.Application.Json); setBody(body) }
    }

    private suspend inline fun <reified T> call(auth: Boolean = true, noinline request: suspend () -> HttpResponse): T =
        callEnvelope<T>(auth, request).data

    private suspend inline fun <reified T> callEnvelope(auth: Boolean = true, noinline request: suspend () -> HttpResponse): ApiEnvelope<T> {
        val response = send(auth, request)
        if (!response.status.isSuccess()) throw response.toApiException()
        return try {
            response.body<ApiEnvelope<T>>()
        } catch (e: SerializationException) {
            throw ApiException(response.status.value, "PARSE", "Respuesta inesperada del servidor.")
        }
    }

    /** Respuestas 204 sin cuerpo. */
    private suspend fun callNoContent(auth: Boolean = true, request: suspend () -> HttpResponse) {
        val response = send(auth, request)
        if (!response.status.isSuccess()) throw response.toApiException()
    }

    /**
     * Ejecuta la petición. Ante un 401 de una llamada autenticada hace un solo intento de renovar la sesión
     * ([SessionRefresher]) y repite la petición una vez con el token nuevo; si no se puede (o sigue en 401) cierra la sesión.
     */
    private suspend fun send(auth: Boolean, request: suspend () -> HttpResponse): HttpResponse {
        var response = execute(request)
        if (auth && response.status.value == 401) {
            val rejected = response.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
            when (refresher()?.refreshAfterUnauthorized(rejected) ?: RefreshOutcome.SESSION_ENDED) {
                RefreshOutcome.RETRY -> response = execute(request)
                RefreshOutcome.UNAVAILABLE -> throw networkError()
                RefreshOutcome.SESSION_ENDED -> Unit
            }
            if (response.status.value == 401) onUnauthorized()
        }
        return response
    }

    private suspend fun execute(request: suspend () -> HttpResponse): HttpResponse = try {
        request()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw networkError()
    }

    private fun networkError() =
        ApiException(0, "NETWORK", "No pudimos conectar con OnCargo. Revisa tu conexión e inténtalo de nuevo.")

    private suspend fun HttpResponse.toApiException(): ApiException {
        val status = this.status.value
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
        429 -> "Hiciste demasiados intentos. Espera unos minutos e inténtalo de nuevo."
        in 500..599 -> "OnCargo tuvo un problema. Inténtalo en unos minutos."
        else -> "Ocurrió un error inesperado."
    }
}
