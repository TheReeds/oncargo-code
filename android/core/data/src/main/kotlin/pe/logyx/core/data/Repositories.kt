package pe.logyx.core.data

import pe.logyx.core.model.AcceptResult
import pe.logyx.core.model.BidView
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.DashboardSummary
import pe.logyx.core.model.DriverShipmentScope
import pe.logyx.core.model.DriverView
import pe.logyx.core.model.FleetEligibility
import pe.logyx.core.model.MarketplaceFilter
import pe.logyx.core.model.Page
import pe.logyx.core.model.PlaceBidRequest
import pe.logyx.core.model.PublicOrganization
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.ShareLinkCreated
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.VehicleView
import pe.logyx.core.network.LogyxApi

/** Catálogos (ciudades con coordenadas) y datos públicos de organizaciones. */
interface CatalogRepository {
    suspend fun cities(): Result<List<CityItem>>
    suspend fun organization(id: String): Result<PublicOrganization>
}

/** Marketplace del transportista: cargas disponibles, ofertas y flota. */
interface LoadsRepository {
    suspend fun marketplace(): Result<List<RequestView>>
    /** Marketplace con filtros y paginación en servidor. */
    suspend fun marketplacePage(filter: MarketplaceFilter = MarketplaceFilter(), page: Int = 0, size: Int = 20): Result<Page<RequestView>> =
        Result.failure(UnsupportedOperationException())
    suspend fun dashboard(): Result<DashboardSummary> = Result.failure(UnsupportedOperationException())
    suspend fun load(id: String): Result<RequestView>
    suspend fun myBids(): Result<List<RequestView>>
    suspend fun vehicles(): Result<List<VehicleView>>
    suspend fun placeBid(requestId: String, vehicleId: String, amount: Double, notes: String?): Result<BidView>
    /** RN02 por fecha: elegibilidad de cada vehículo de mi flota para la carga (agenda y avisos). */
    suspend fun eligibility(requestId: String): Result<FleetEligibility> = Result.failure(UnsupportedOperationException())
    /** Tasa de comisión vigente de OnCargo (0–1), para «cobras S/ X neto». */
    suspend fun commissionRate(): Result<Double> = Result.failure(UnsupportedOperationException())
    /** Retira mi oferta pendiente con la subasta abierta (`BID-005`). Después no se puede volver a ofertar (`BID-003`). */
    suspend fun withdrawBid(bidId: String): Result<BidView> = Result.failure(UnsupportedOperationException())
    /** Búsquedas guardadas (V22, 2.3): solo las mías. `ALT-001/002/003` llegan como `ApiException` en español. */
    suspend fun savedSearches(): Result<List<pe.logyx.core.model.SavedSearch>> = Result.failure(UnsupportedOperationException())
    suspend fun createSavedSearch(body: pe.logyx.core.model.SavedSearchRequest): Result<pe.logyx.core.model.SavedSearch> =
        Result.failure(UnsupportedOperationException())
    suspend fun updateSavedSearch(id: String, body: pe.logyx.core.model.SavedSearchRequest): Result<pe.logyx.core.model.SavedSearch> =
        Result.failure(UnsupportedOperationException())
    suspend fun deleteSavedSearch(id: String): Result<Unit> = Result.failure(UnsupportedOperationException())
}

/** Solicitudes de la PYME y ofertas recibidas. */
interface RequestsRepository {
    suspend fun mine(): Result<List<RequestView>>
    suspend fun detail(id: String): Result<RequestView>
    suspend fun bids(requestId: String): Result<List<BidView>>
    suspend fun acceptBid(bidId: String): Result<AcceptResult>
    /** Cancela con motivo opcional (RN07 en `reason`, máx. 500): se avisa a los transportistas que ofertaron. */
    suspend fun cancel(requestId: String, reason: String? = null): Result<RequestView>
    /** Reabre la subasta cerrada por tiempo; `pickupDate` (`yyyy-MM-dd`) opcional. `AUC-003`/`REQ-003`. */
    suspend fun reopen(requestId: String, pickupDate: String? = null): Result<RequestView> = Result.failure(UnsupportedOperationException())
    /** Republica una solicitud cancelada; devuelve la **nueva** solicitud. `REQ-004`/`REQ-003`. */
    suspend fun republish(requestId: String, pickupDate: String? = null): Result<RequestView> = Result.failure(UnsupportedOperationException())
    /** Publica una solicitud nueva (2.16). Con la misma `idempotencyKey` un reintento devuelve la ya creada. Errores con `field` (RN07, ventanas). */
    suspend fun create(body: pe.logyx.core.model.CreateRequestBody, idempotencyKey: String? = null): Result<RequestView> =
        Result.failure(UnsupportedOperationException())
    /** Cotización en vivo del formulario (`POST /pricing/calculate`). */
    suspend fun quote(origin: String, destination: String, weightKg: Double, cargoType: pe.logyx.core.model.CargoType): Result<pe.logyx.core.model.QuoteView> =
        Result.failure(UnsupportedOperationException())
    /** Libreta de direcciones de la organización (activas). */
    suspend fun places(): Result<List<pe.logyx.core.model.PlaceView>> = Result.failure(UnsupportedOperationException())
    /** Plantillas de solicitud de la organización (2.1). */
    suspend fun templates(): Result<List<pe.logyx.core.model.TemplateView>> = Result.failure(UnsupportedOperationException())
    /** «Publicar ahora» una plantilla con la fecha de recojo dada. */
    suspend fun publishTemplate(templateId: String, pickupDate: String, idempotencyKey: String? = null): Result<pe.logyx.core.model.PublishedFromTemplate> =
        Result.failure(UnsupportedOperationException())
}

/** Envíos: seguimiento (PYME/transportista) y ejecución (conductor). */
interface ShipmentsRepository {
    suspend fun list(): Result<List<ShipmentView>>
    suspend fun detail(id: String): Result<ShipmentView>
    suspend fun drivers(): Result<List<DriverView>>
    /** Asigna o releva al conductor. `reason` es obligatorio en ruta (`SHP-006`); `SHP-004`/`FLT-003` llegan con `field = driverId`. */
    suspend fun assignDriver(shipmentId: String, driverId: String, reason: String? = null): Result<ShipmentView>
    /** Cancela el envío con motivo (PYME o transportista antes del recojo: `SHP-003`, `TRK-005`). */
    suspend fun cancel(shipmentId: String, reason: String): Result<ShipmentView> = Result.failure(UnsupportedOperationException())
    /** Cambia el vehículo (transportista). `CAP-001..005` y `SHP-007` con `field = vehicleId`; en ruta `reason` es obligatorio. */
    suspend fun changeVehicle(shipmentId: String, vehicleId: String, reason: String? = null): Result<ShipmentView> =
        Result.failure(UnsupportedOperationException())
    suspend fun driverShipments(): Result<List<ShipmentView>>
    /** Viajes anteriores del conductor: entregados y cancelados, recientes primero (`scope=HISTORY`, V15, F22). */
    suspend fun driverHistory(): Result<List<ShipmentView>> = Result.failure(UnsupportedOperationException())
    /** Envío activo del conductor, o null si no tiene (404 LOGYX-SHP-002). */
    suspend fun driverActive(): Result<ShipmentView?> = Result.failure(UnsupportedOperationException())
    suspend fun arrive(shipmentId: String, stopId: String): Result<ShipmentView>
    suspend fun complete(shipmentId: String, stopId: String): Result<ShipmentView>
    /**
     * Con la hora del dispositivo (roadmap 1.11; null = hora del servidor). Por omisión delega en la versión sin hora para
     * no romper fakes antiguos. Idempotentes: repetir una acción ya aplicada devuelve 200 con el envío.
     */
    suspend fun arrive(shipmentId: String, stopId: String, occurredAt: String?): Result<ShipmentView> = arrive(shipmentId, stopId)
    suspend fun complete(shipmentId: String, stopId: String, occurredAt: String?): Result<ShipmentView> = complete(shipmentId, stopId)
    /**
     * Cierre con los datos de la parada (V23, 2.5): peso medido del recojo y quién recibe en la entrega. Por omisión delega en
     * [complete] con la hora (los fakes viejos ignoran los datos).
     */
    suspend fun completeStop(shipmentId: String, stopId: String, event: pe.logyx.core.model.StopEventRequest): Result<ShipmentView> =
        complete(shipmentId, stopId, event.occurredAt)
    /** Enlace público de seguimiento (PYME ADMIN/OPS). `SHL-002/003/004`, `AUTH-007` por `userMessage()`. */
    suspend fun createShareLink(shipmentId: String, expiresInDays: Int? = null): Result<ShareLinkCreated> =
        Result.failure(UnsupportedOperationException())
}

class DefaultCatalogRepository(private val api: LogyxApi) : CatalogRepository {
    @Volatile private var cached: List<CityItem>? = null

    override suspend fun cities() = cached?.let { Result.success(it) }
        ?: apiCall { api.cities() }.onSuccess { cached = it }

    override suspend fun organization(id: String) = apiCall { api.organization(id) }
}

class DefaultLoadsRepository(private val api: LogyxApi) : LoadsRepository {
    override suspend fun marketplace() = apiCall { api.marketplace() }
    override suspend fun marketplacePage(filter: MarketplaceFilter, page: Int, size: Int) = apiCall { api.marketplacePage(filter, page, size) }
    override suspend fun dashboard() = apiCall { api.dashboardSummary() }
    override suspend fun load(id: String) = apiCall { api.request(id) }
    override suspend fun myBids() = apiCall { api.myBids() }
    override suspend fun vehicles() = apiCall { api.vehicles() }
    override suspend fun placeBid(requestId: String, vehicleId: String, amount: Double, notes: String?) =
        apiCall { api.placeBid(requestId, PlaceBidRequest(vehicleId, amount, notes?.takeIf { it.isNotBlank() })) }
    override suspend fun withdrawBid(bidId: String) = apiCall { api.withdrawBid(bidId) }
    override suspend fun eligibility(requestId: String) = apiCall { api.vehicleEligibility(requestId) }
    override suspend fun commissionRate() = apiCall { api.commission().commissionRate }
    override suspend fun savedSearches() = apiCall { api.savedSearches() }
    override suspend fun createSavedSearch(body: pe.logyx.core.model.SavedSearchRequest) = apiCall { api.createSavedSearch(body) }
    override suspend fun updateSavedSearch(id: String, body: pe.logyx.core.model.SavedSearchRequest) = apiCall { api.updateSavedSearch(id, body) }
    override suspend fun deleteSavedSearch(id: String) = apiCall { api.deleteSavedSearch(id) }
}

class DefaultRequestsRepository(private val api: LogyxApi) : RequestsRepository {
    override suspend fun mine() = apiCall { api.myRequests() }
    override suspend fun detail(id: String) = apiCall { api.request(id) }
    override suspend fun bids(requestId: String) = apiCall { api.bids(requestId) }
    override suspend fun acceptBid(bidId: String) = apiCall { api.acceptBid(bidId) }
    override suspend fun cancel(requestId: String, reason: String?) = apiCall { api.cancelRequest(requestId, reason) }
    override suspend fun reopen(requestId: String, pickupDate: String?) = apiCall { api.reopenRequest(requestId, pickupDate) }
    override suspend fun republish(requestId: String, pickupDate: String?) = apiCall { api.republishRequest(requestId, pickupDate) }
    override suspend fun create(body: pe.logyx.core.model.CreateRequestBody, idempotencyKey: String?) = apiCall { api.createRequest(body, idempotencyKey) }
    override suspend fun quote(origin: String, destination: String, weightKg: Double, cargoType: pe.logyx.core.model.CargoType) =
        apiCall { api.quote(pe.logyx.core.model.QuoteRequestBody(origin, destination, weightKg, cargoType)) }
    override suspend fun places() = apiCall { api.places().filter { !it.archived } }
    override suspend fun templates() = apiCall { api.templates().filter { !it.archived } }
    override suspend fun publishTemplate(templateId: String, pickupDate: String, idempotencyKey: String?) =
        apiCall { api.publishTemplate(templateId, pickupDate, idempotencyKey) }
}

class DefaultShipmentsRepository(private val api: LogyxApi) : ShipmentsRepository {
    override suspend fun list() = apiCall { api.shipments() }
    override suspend fun detail(id: String) = apiCall { api.shipment(id) }
    override suspend fun drivers() = apiCall { api.drivers() }
    override suspend fun assignDriver(shipmentId: String, driverId: String, reason: String?) =
        apiCall { api.assignDriver(shipmentId, driverId, reason) }
    override suspend fun cancel(shipmentId: String, reason: String) = apiCall { api.cancelShipment(shipmentId, reason) }
    override suspend fun changeVehicle(shipmentId: String, vehicleId: String, reason: String?) =
        apiCall { api.changeVehicle(shipmentId, vehicleId, reason) }

    override suspend fun driverShipments() = apiCall { api.driverShipments() }
    override suspend fun driverHistory() = apiCall { api.driverShipments(DriverShipmentScope.HISTORY) }
    override suspend fun driverActive(): Result<ShipmentView?> =
        apiCall { api.driverActiveShipment() as ShipmentView? }.recoverCatching { e ->
            if (e is pe.logyx.core.network.ApiException && e.status == 404) null else throw e
        }
    override suspend fun arrive(shipmentId: String, stopId: String) = apiCall { api.arriveAtStop(shipmentId, stopId) }
    override suspend fun complete(shipmentId: String, stopId: String) = apiCall { api.completeStop(shipmentId, stopId) }
    override suspend fun arrive(shipmentId: String, stopId: String, occurredAt: String?) =
        apiCall { api.arriveAtStop(shipmentId, stopId, occurredAt) }
    override suspend fun complete(shipmentId: String, stopId: String, occurredAt: String?) =
        apiCall { api.completeStop(shipmentId, stopId, occurredAt) }
    override suspend fun completeStop(shipmentId: String, stopId: String, event: pe.logyx.core.model.StopEventRequest) =
        apiCall { api.completeStop(shipmentId, stopId, event) }
    override suspend fun createShareLink(shipmentId: String, expiresInDays: Int?) = apiCall { api.createShareLink(shipmentId, expiresInDays) }
}
