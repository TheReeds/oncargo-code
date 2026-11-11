package pe.logyx.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.EvidenceRepository
import pe.logyx.core.data.LoadsRepository
import pe.logyx.core.data.RequestsRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException

/** Repositorios en memoria: los tests de UI no necesitan backend. */
object Data {
    fun stop(id: String, seq: Int, type: StopType, status: StopStatus = StopStatus.PENDING) =
        StopView(id, seq, type, if (type == StopType.PICKUP) "LIMA" else "JULIACA", "Dirección $seq", status)

    fun user(role: Role, org: OrgType, name: String = "Carlos Rojas") =
        UserInfo("u-$role-$org", "$role@logyx.pe", name, role, "org1", "Org Demo", org, rucVerified = true, organizationCity = "LIMA")

    val carrier = user(Role.ADMIN, OrgType.CARRIER)
    val pyme = user(Role.ADMIN, OrgType.PYME, "Ana Torres")
    val driver = user(Role.DRIVER, OrgType.CARRIER, "Juan Pari")

    val vehicle = VehicleView("v1", "ASA-202", VehicleType.TRUCK_LARGE, 20000.0, null, VehicleStatus.AVAILABLE)

    fun load(id: String = "r1") = RequestView(
        id = id, requesterName = "Comercial Altiplano SAC", originCity = "TACNA", destinationCity = "AREQUIPA",
        cargoType = CargoType.GENERAL, cargoDescription = "Repuestos automotrices", weightKg = 2500.0,
        pickupDate = "2026-11-13", distanceKm = 303.0, suggestedPrice = 870.0, priceFloor = 650.0,
        status = RequestStatus.PUBLISHED, requesterRucVerified = true, minVehicleType = VehicleType.TRUCK_SMALL,
        auction = AuctionView("a1", AuctionStatus.OPEN, "2099-01-01T00:00:00Z", 5, 1),
    )

    fun trip(vararg stops: StopView) = ShipmentView(
        "s1", originCity = "LIMA", destinationCity = "JULIACA", cargoDescription = "Abarrotes", weightKg = 6000.0,
        vehiclePlate = "ASA-101", status = ShipmentStatus.PENDING, stops = stops.toList(),
    )
}

class FakeAuth(initial: SessionState = SessionState.LoggedOut, private val loginAs: UserInfo = Data.carrier) : AuthRepository {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<SessionState> = _state
    var loginCalls = 0
    fun setLoggedIn(user: UserInfo) { _state.value = SessionState.LoggedIn(user) }
    override suspend fun login(email: String, password: String): Result<UserInfo> {
        loginCalls++
        _state.value = SessionState.LoggedIn(loginAs)
        return Result.success(loginAs)
    }
    override suspend fun logout() { _state.value = SessionState.LoggedOut }
}

class FakeCatalog : CatalogRepository {
    override suspend fun cities() = Result.success(
        listOf(CityItem("LIMA", "Lima", -12.04, -77.04), CityItem("TACNA", "Tacna", -18.0, -70.25), CityItem("AREQUIPA", "Arequipa", -16.4, -71.5), CityItem("JULIACA", "Juliaca", -15.5, -70.1)),
    )
    override suspend fun organization(id: String) = Result.failure<PublicOrganization>(IllegalStateException())
}

class FakeLoads(var items: List<RequestView> = listOf(Data.load())) : LoadsRepository {
    val placed = mutableListOf<Triple<String, Double, String>>()
    override suspend fun marketplace() = Result.success(items)
    override suspend fun marketplacePage(filter: MarketplaceFilter, page: Int, size: Int) = Result.success(Page(items, 0, size, items.size))
    override suspend fun dashboard() = Result.success(DashboardSummary("CARRIER", compatibleLoads = items.size, pendingBids = 0, activeShipments = 0, deliveredThisMonth = 0))
    override suspend fun load(id: String) = Result.success(items.first { it.id == id })
    override suspend fun myBids() = Result.success(emptyList<RequestView>())
    override suspend fun vehicles() = Result.success(listOf(Data.vehicle))
    override suspend fun placeBid(requestId: String, vehicleId: String, amount: Double, notes: String?): Result<BidView> {
        placed += Triple(requestId, amount, vehicleId)
        val bid = BidView("b1", requestId, amount = amount, status = BidStatus.PENDING, vehicleType = Data.vehicle.type, vehiclePlate = Data.vehicle.plate)
        items = items.map { if (it.id == requestId) it.copy(myBid = bid) else it }
        return Result.success(bid)
    }
}

class FakeRequests : RequestsRepository {
    override suspend fun mine() = Result.success(listOf(Data.load("r1")))
    override suspend fun detail(id: String) = Result.success(Data.load(id))
    override suspend fun bids(requestId: String) = Result.success(emptyList<BidView>())
    override suspend fun acceptBid(bidId: String) = Result.failure<AcceptResult>(IllegalStateException())
    override suspend fun cancel(requestId: String) = Result.failure<RequestView>(IllegalStateException())
}

/** Simula la máquina de estados de paradas del backend. */
class FakeShipments(var current: ShipmentView, private val evidence: FakeEvidence? = null) : ShipmentsRepository {
    private fun update(stopId: String, to: StopStatus): Result<ShipmentView> {
        val stops = current.stops.map { if (it.id == stopId) it.copy(status = to) else it }
        current = current.copy(stops = stops, status = if (stops.all { it.status == StopStatus.COMPLETED }) ShipmentStatus.DELIVERED else ShipmentStatus.IN_TRANSIT)
        return Result.success(current)
    }
    override suspend fun list() = Result.success(listOf(current))
    override suspend fun detail(id: String) = Result.success(current)
    override suspend fun drivers() = Result.success(emptyList<DriverView>())
    override suspend fun assignDriver(shipmentId: String, driverId: String) = Result.success(current)
    override suspend fun driverShipments() = Result.success(listOf(current))
    override suspend fun driverActive() = Result.success<ShipmentView?>(current)
    override suspend fun arrive(shipmentId: String, stopId: String) = update(stopId, StopStatus.ARRIVED)
    override suspend fun complete(shipmentId: String, stopId: String): Result<ShipmentView> {
        // RN06: la parada de entrega exige al menos una foto de entrega subida.
        val stop = current.stops.firstOrNull { it.id == stopId }
        if (evidence != null && stop?.type == StopType.DELIVERY && !evidence.hasUploadedPhoto(shipmentId)) {
            return Result.failure(ApiException(409, "LOGYX-TRK-006", "Falta la evidencia fotográfica de la entrega"))
        }
        return update(stopId, StopStatus.COMPLETED)
    }
}

/** Sustituye los repositorios reales de Koin por fakes durante el test y los restaura al terminar. */
class FakeKoinRule(
    private val auth: FakeAuth,
    private val loads: FakeLoads = FakeLoads(),
    private val shipments: FakeShipments = FakeShipments(Data.trip(Data.stop("p", 1, StopType.PICKUP), Data.stop("d", 2, StopType.DELIVERY))),
    private val evidence: FakeEvidence = FakeEvidence(),
) : TestWatcher() {
    private var overrides: Module? = null
    override fun starting(description: Description) {
        overrides = module {
            single<AuthRepository> { auth }
            single<CatalogRepository> { FakeCatalog() }
            single<LoadsRepository> { loads }
            single<RequestsRepository> { FakeRequests() }
            single<ShipmentsRepository> { shipments }
            single<EvidenceRepository> { evidence }
        }.also { loadKoinModules(it) }
    }
    override fun finished(description: Description) { overrides?.let { unloadKoinModules(it) } }
}
