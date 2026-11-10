package pe.logyx.feature.loads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.LoadsRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val user = UserInfo("u1", "c@logyx.pe", "Carla Quispe", Role.ADMIN, "org1", "Andes SAC", OrgType.CARRIER, true)

private class FakeAuth : AuthRepository {
    override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.LoggedIn(user))
    override suspend fun login(email: String, password: String) = Result.success(user)
    override suspend fun logout() {}
}

private class FakeCatalog(val orgCity: String? = "Lima") : CatalogRepository {
    override suspend fun cities() = Result.success(
        listOf(CityItem("LIMA", "Lima", -12.04, -77.04), CityItem("JULIACA", "Juliaca", -15.5, -70.1), CityItem("AREQUIPA", "Arequipa", -16.4, -71.5)),
    )
    override suspend fun organization(id: String) = Result.success(PublicOrganization(id, OrgType.CARRIER, "Andes SAC", city = orgCity))
}

private fun req(id: String, origin: String, kg: Double, price: Double, floor: Double = 1000.0, myBid: BidView? = null, created: String = "2026-11-10T10:00:00-05:00") =
    RequestView(
        id = id, originCity = origin, destinationCity = "JULIACA", cargoType = CargoType.GENERAL, weightKg = kg,
        suggestedPrice = price, priceFloor = floor, status = RequestStatus.PUBLISHED, createdAt = created, myBid = myBid,
        auction = AuctionView("a$id", AuctionStatus.OPEN, "2099-01-01T00:00:00Z", 5, 1),
    )

private fun truck(id: String, kg: Double) = VehicleView(id, "ABC-$id", VehicleType.TRUCK_MEDIUM, kg, 20.0, VehicleStatus.AVAILABLE)

private class FakeLoads(
    var market: Result<List<RequestView>> = Result.success(emptyList()),
    var fleet: List<VehicleView> = emptyList(),
    var bids: List<RequestView> = emptyList(),
    var placeResult: Result<BidView>? = null,
) : LoadsRepository {
    var lastBid: Triple<String, Double, String?>? = null
    override suspend fun marketplace() = market
    override suspend fun load(id: String) = market.map { l -> l.first { it.id == id } }
    override suspend fun myBids() = Result.success(bids)
    override suspend fun vehicles() = Result.success(fleet)
    override suspend fun placeBid(requestId: String, vehicleId: String, amount: Double, notes: String?): Result<BidView> {
        lastBid = Triple(vehicleId, amount, notes)
        return placeResult ?: Result.success(BidView("b1", requestId, amount = amount, status = BidStatus.PENDING))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoadsViewModelsTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val loads = listOf(
        req("1", "LIMA", 3000.0, 1500.0, created = "2026-11-10T09:00:00-05:00"),
        req("2", "AREQUIPA", 12000.0, 4200.0, created = "2026-11-10T10:00:00-05:00"),
        req("3", "LIMA", 4000.0, 2100.0, created = "2026-11-10T11:00:00-05:00"),
    )

    @Test fun `feed filtra por mi vehiculo, mejor pago y cerca de mi`() = runTest {
        val repo = FakeLoads(Result.success(loads), fleet = listOf(truck("1", 5000.0)))
        val vm = LoadsFeedViewModel(FakeAuth(), repo, FakeCatalog("Lima"))
        val s = vm.state.value
        assertEquals(listOf("3", "2", "1"), s.visible.map { it.id }, "por defecto: más recientes primero")

        vm.toggle(LoadFilter.MyVehicle)
        assertEquals(listOf("3", "1"), vm.state.value.visible.map { it.id }, "2 pesa 12 t y no cabe")

        vm.toggle(LoadFilter.BestPay)
        assertEquals(listOf("3", "1"), vm.state.value.visible.map { it.id })
        vm.toggle(LoadFilter.MyVehicle)
        assertEquals(listOf("2", "3", "1"), vm.state.value.visible.map { it.id }, "mejor pago = mayor precio")

        vm.toggle(LoadFilter.BestPay); vm.toggle(LoadFilter.Nearby)
        assertEquals(listOf("3", "1"), vm.state.value.visible.map { it.id }, "origen en Lima")
    }

    @Test fun `cerca de mi sin ciudad de organizacion avisa`() = runTest {
        val vm = LoadsFeedViewModel(FakeAuth(), FakeLoads(Result.success(loads)), FakeCatalog(orgCity = null))
        vm.toggle(LoadFilter.Nearby)
        assertTrue(vm.state.value.nearbyUnavailable)
        assertEquals(3, vm.state.value.visible.size)
    }

    @Test fun `feed muestra error del API si falla`() = runTest {
        val vm = LoadsFeedViewModel(FakeAuth(), FakeLoads(Result.failure(ApiException(0, "NETWORK", "Sin conexión"))), FakeCatalog())
        assertEquals("Sin conexión", vm.state.value.error)
        assertTrue(vm.state.value.all.isEmpty())
    }

    @Test fun `home sugiere solo cargas compatibles y sin oferta`() = runTest {
        val withBid = req("4", "LIMA", 1000.0, 999.0, myBid = BidView("b", "4", amount = 900.0, status = BidStatus.PENDING))
        val repo = FakeLoads(Result.success(loads + withBid), fleet = listOf(truck("1", 5000.0)), bids = listOf(withBid))
        val vm = CarrierHomeViewModel(FakeAuth(), repo, FakeShipments())
        val s = vm.state.value
        assertEquals(listOf("3", "1"), s.suggested.map { it.id })
        assertEquals(2, s.compatibleCount)
        assertEquals(1, s.pendingBids)
        assertEquals("Carla Quispe", s.userName)
    }

    @Test fun `ofertar valida piso localmente y muestra error de la API`() = runTest {
        val repo = FakeLoads(Result.success(loads), fleet = listOf(truck("1", 5000.0)))
        val vm = LoadDetailViewModel("1", repo, FakeCatalog())
        assertNotNull(vm.state.value.origin)
        vm.openBidSheet()
        assertEquals("1", vm.state.value.form?.vehicleId, "un solo vehículo compatible se preselecciona")

        vm.onAmountChange("500")
        vm.submitBid()
        assertTrue(vm.state.value.form?.error!!.contains("piso"), "monto bajo el piso local")
        assertNull(repo.lastBid)

        repo.placeResult = Result.failure(ApiException(400, "LOGYX-MKT-004", "La subasta ya cerró", "amount"))
        vm.onAmountChange("1,250.50")
        vm.submitBid()
        assertEquals("La subasta ya cerró", vm.state.value.form?.error)
        assertEquals(1250.5, repo.lastBid?.second)

        repo.placeResult = null
        vm.submitBid()
        assertNull(vm.state.value.form)
        assertEquals("¡Oferta enviada!", vm.state.value.message)
    }

    @Test fun `sin vehiculo compatible no hay opciones`() = runTest {
        val vm = LoadDetailViewModel("2", FakeLoads(Result.success(loads), fleet = listOf(truck("1", 5000.0))), FakeCatalog())
        assertTrue(vm.state.value.compatibleVehicles.isEmpty())
    }
}

private class FakeShipments : pe.logyx.core.data.ShipmentsRepository {
    override suspend fun list() = Result.success(emptyList<ShipmentView>())
    override suspend fun detail(id: String) = Result.failure<ShipmentView>(IllegalStateException())
    override suspend fun drivers() = Result.success(emptyList<DriverView>())
    override suspend fun assignDriver(shipmentId: String, driverId: String) = Result.failure<ShipmentView>(IllegalStateException())
    override suspend fun driverShipments() = Result.success(emptyList<ShipmentView>())
    override suspend fun arrive(shipmentId: String, stopId: String) = Result.failure<ShipmentView>(IllegalStateException())
    override suspend fun complete(shipmentId: String, stopId: String) = Result.failure<ShipmentView>(IllegalStateException())
}

class ParseAmountTest {
    @Test fun parses() {
        assertEquals(1850.0, parseAmount("1850"))
        assertEquals(1850.5, parseAmount("1850,50"))
        assertEquals(1850.5, parseAmount("1,850.50"))
        assertNull(parseAmount(""))
    }
}
