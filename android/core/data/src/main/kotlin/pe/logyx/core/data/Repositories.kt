package pe.logyx.core.data

import pe.logyx.core.model.AcceptResult
import pe.logyx.core.model.BidView
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.DashboardSummary
import pe.logyx.core.model.DriverView
import pe.logyx.core.model.MarketplaceFilter
import pe.logyx.core.model.Page
import pe.logyx.core.model.PlaceBidRequest
import pe.logyx.core.model.PublicOrganization
import pe.logyx.core.model.RequestView
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
}

/** Solicitudes de la PYME y ofertas recibidas. */
interface RequestsRepository {
    suspend fun mine(): Result<List<RequestView>>
    suspend fun detail(id: String): Result<RequestView>
    suspend fun bids(requestId: String): Result<List<BidView>>
    suspend fun acceptBid(bidId: String): Result<AcceptResult>
    suspend fun cancel(requestId: String): Result<RequestView>
}

/** Envíos: seguimiento (PYME/transportista) y ejecución (conductor). */
interface ShipmentsRepository {
    suspend fun list(): Result<List<ShipmentView>>
    suspend fun detail(id: String): Result<ShipmentView>
    suspend fun drivers(): Result<List<DriverView>>
    suspend fun assignDriver(shipmentId: String, driverId: String): Result<ShipmentView>
    suspend fun driverShipments(): Result<List<ShipmentView>>
    /** Envío activo del conductor, o null si no tiene (404 LOGYX-SHP-002). */
    suspend fun driverActive(): Result<ShipmentView?> = Result.failure(UnsupportedOperationException())
    suspend fun arrive(shipmentId: String, stopId: String): Result<ShipmentView>
    suspend fun complete(shipmentId: String, stopId: String): Result<ShipmentView>
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
}

class DefaultRequestsRepository(private val api: LogyxApi) : RequestsRepository {
    override suspend fun mine() = apiCall { api.myRequests() }
    override suspend fun detail(id: String) = apiCall { api.request(id) }
    override suspend fun bids(requestId: String) = apiCall { api.bids(requestId) }
    override suspend fun acceptBid(bidId: String) = apiCall { api.acceptBid(bidId) }
    override suspend fun cancel(requestId: String) = apiCall { api.cancelRequest(requestId) }
}

class DefaultShipmentsRepository(private val api: LogyxApi) : ShipmentsRepository {
    override suspend fun list() = apiCall { api.shipments() }
    override suspend fun detail(id: String) = apiCall { api.shipment(id) }
    override suspend fun drivers() = apiCall { api.drivers() }
    override suspend fun assignDriver(shipmentId: String, driverId: String) =
        apiCall { api.assignDriver(shipmentId, driverId) }

    override suspend fun driverShipments() = apiCall { api.driverShipments() }
    override suspend fun driverActive(): Result<ShipmentView?> =
        apiCall { api.driverActiveShipment() as ShipmentView? }.recoverCatching { e ->
            if (e is pe.logyx.core.network.ApiException && e.status == 404) null else throw e
        }
    override suspend fun arrive(shipmentId: String, stopId: String) = apiCall { api.arriveAtStop(shipmentId, stopId) }
    override suspend fun complete(shipmentId: String, stopId: String) = apiCall { api.completeStop(shipmentId, stopId) }
}
