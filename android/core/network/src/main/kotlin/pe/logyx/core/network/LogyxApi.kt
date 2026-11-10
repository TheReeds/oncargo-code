package pe.logyx.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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
import pe.logyx.core.model.AcceptResult
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
            throw ApiException(0, "NETWORK", "No pudimos conectar con LOGYX. Revisa tu conexión e inténtalo de nuevo.")
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
        in 500..599 -> "LOGYX tuvo un problema. Inténtalo en unos minutos."
        else -> "Ocurrió un error inesperado."
    }
}
