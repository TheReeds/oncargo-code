package pe.logyx.feature.loads

import pe.logyx.core.designsystem.components.UiText
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

private val user = UserInfo("u1", "c@logyx.pe", "Carla Quispe", Role.ADMIN, "org1", "Andes SAC", OrgType.CARRIER, true, organizationCity = "Lima")

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
    var lastFilter: MarketplaceFilter? = null
    var dash: Result<DashboardSummary>? = null
    override suspend fun marketplace() = market
    override suspend fun marketplacePage(filter: MarketplaceFilter, page: Int, size: Int): Result<Page<RequestView>> {
        lastFilter = filter
        return market.map { all ->
            var l = all
            filter.originCity?.let { c -> l = l.filter { it.originCity == c } }
            filter.maxWeightKg?.let { w -> l = l.filter { it.weightKg <= w } }
            if (filter.sort == MarketplaceSort.PRICE_DESC) l = l.sortedByDescending { it.suggestedPrice ?: 0.0 }
            Page(l.drop(page * size).take(size), page, size, l.size)
        }
    }
    override suspend fun dashboard() = dash ?: Result.failure(UnsupportedOperationException())
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

    @Test fun `feed manda filtros al servidor`() = runTest {
        val repo = FakeLoads(Result.success(loads), fleet = listOf(truck("1", 5000.0)))
        val vm = LoadsFeedViewModel(FakeAuth(), repo, FakeCatalog("Lima"))
        assertEquals(3, vm.state.value.visible.size)
        assertEquals(3, vm.state.value.total)
        assertEquals(3, vm.state.value.cities.size)

        vm.toggle(LoadFilter.MyVehicle)
        assertEquals(5000.0, repo.lastFilter?.maxWeightKg)
        assertEquals(listOf("1", "3"), vm.state.value.visible.map { it.id }.sorted(), "2 pesa 12 t y no cabe")

        vm.toggle(LoadFilter.BestPay)
        assertEquals(MarketplaceSort.PRICE_DESC, repo.lastFilter?.sort)
        assertEquals(listOf("3", "1"), vm.state.value.visible.map { it.id })

        vm.toggle(LoadFilter.Nearby)
        assertEquals("LIMA", repo.lastFilter?.originCity)

        vm.clearFilters()
        assertEquals(MarketplaceFilter(), repo.lastFilter)
        assertEquals(3, vm.state.value.visible.size)
    }

    @Test fun `origen y destino elegidos se envian y excluyen cerca de mi`() = runTest {
        val repo = FakeLoads(Result.success(loads))
        val vm = LoadsFeedViewModel(FakeAuth(), repo, FakeCatalog())
        vm.toggle(LoadFilter.Nearby)
        vm.setOrigin(CityItem("AREQUIPA", "Arequipa", -16.4, -71.5))
        assertEquals("AREQUIPA", repo.lastFilter?.originCity)
        assertTrue(LoadFilter.Nearby !in vm.state.value.filters)
        assertEquals(listOf("2"), vm.state.value.visible.map { it.id })
        vm.setCargoType(CargoType.FRAGILE)
        assertEquals(CargoType.FRAGILE, repo.lastFilter?.cargoType)
        assertEquals(2, vm.state.value.advancedCount)
    }

    @Test fun `scroll infinito agrega paginas sin duplicar`() = runTest {
        val many = (1..45).map { req("$it", "LIMA", 1000.0, it.toDouble()) }
        val vm = LoadsFeedViewModel(FakeAuth(), FakeLoads(Result.success(many)), FakeCatalog())
        assertEquals(20, vm.state.value.items.size)
        assertEquals(45, vm.state.value.total)
        assertTrue(vm.state.value.hasMore)
        vm.loadMore(); vm.loadMore()
        assertEquals(45, vm.state.value.items.size)
        assertEquals(45, vm.state.value.items.map { it.id }.toSet().size)
        assertTrue(!vm.state.value.hasMore)
        vm.loadMore()
        assertEquals(45, vm.state.value.items.size)
    }

    @Test fun `cerca de mi sin ciudad de organizacion avisa`() = runTest {
        val noCity = object : AuthRepository {
            override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.LoggedIn(user.copy(organizationCity = null)))
            override suspend fun login(email: String, password: String) = Result.success(user)
            override suspend fun logout() {}
        }
        val repo = FakeLoads(Result.success(loads))
        val vm = LoadsFeedViewModel(noCity, repo, FakeCatalog(orgCity = null))
        vm.toggle(LoadFilter.Nearby)
        assertTrue(vm.state.value.nearbyUnavailable)
        assertNull(repo.lastFilter?.originCity)
        assertEquals(3, vm.state.value.visible.size)
    }

    @Test fun `feed muestra error del API si falla`() = runTest {
        val vm = LoadsFeedViewModel(FakeAuth(), FakeLoads(Result.failure(ApiException(0, "NETWORK", "Sin conexión"))), FakeCatalog())
        assertEquals("Sin conexión", vm.state.value.error)
        assertTrue(vm.state.value.items.isEmpty())
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
        assertTrue((vm.state.value.form?.amountError as UiText.Res).id == R.string.loads_error_amount_below_floor, "monto bajo el piso local")
        assertNull(repo.lastBid)

        repo.placeResult = Result.failure(ApiException(400, "LOGYX-MKT-004", "La subasta ya cerró", "amount"))
        vm.onAmountChange("1,250.50")
        vm.submitBid()
        assertEquals(UiText.Raw("La subasta ya cerró"), vm.state.value.form?.amountError)
        assertEquals(1250.5, repo.lastBid?.second)

        repo.placeResult = null
        vm.submitBid()
        assertNull(vm.state.value.form)
        assertEquals(R.string.loads_bid_sent, vm.state.value.message?.textRes)
    }

    @Test fun `error de campo de la API se muestra en el campo`() = runTest {
        val repo = FakeLoads(Result.success(loads), fleet = listOf(truck("1", 5000.0)))
        val vm = LoadDetailViewModel("1", repo, FakeCatalog())
        vm.openBidSheet()
        vm.onAmountChange("1200")
        repo.placeResult = Result.failure(ApiException(400, "X", "Monto bajo el piso", "amount"))
        vm.requestConfirm()
        assertTrue(vm.state.value.form?.confirming == true)
        vm.submitBid()
        assertEquals(UiText.Raw("Monto bajo el piso"), vm.state.value.form?.amountError)
        assertNull(vm.state.value.form?.error)
    }

    @Test fun `home usa el resumen del dashboard`() = runTest {
        val repo = FakeLoads(Result.success(loads), fleet = listOf(truck("1", 5000.0)))
        repo.dash = Result.success(DashboardSummary(compatibleLoads = 9, pendingBids = 4, activeShipments = 2, deliveredThisMonth = 7))
        val s = CarrierHomeViewModel(FakeAuth(), repo, FakeShipments()).state.value
        assertEquals(9, s.compatibleCount)
        assertEquals(4, s.pendingBids)
        assertEquals(7, s.deliveredThisMonth)
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

private class FakeNegotiation(var counters: Map<String, List<CounterOfferView>> = emptyMap()) : pe.logyx.core.data.NegotiationRepository {
    val queried = mutableListOf<String>()
    var accepted: String? = null
    var rejected: String? = null
    override suspend fun threads(requestId: String) = Result.success(emptyList<ChatThreadView>())
    override suspend fun messages(requestId: String, carrierOrgId: String, before: String?) = Result.success(CursorPage<MessageView>(emptyList(), null))
    override suspend fun send(requestId: String, carrierOrgId: String, body: String) = Result.failure<MessageView>(IllegalStateException())
    override suspend fun counterOffers(bidId: String): Result<List<CounterOfferView>> { queried += bidId; return Result.success(counters[bidId].orEmpty()) }
    override suspend fun propose(bidId: String, amount: Double, message: String?) = Result.failure<CounterOfferView>(IllegalStateException())
    override suspend fun accept(counterOfferId: String): Result<CounterOfferView> { accepted = counterOfferId; return Result.success(CounterOfferView(counterOfferId, "b1", amount = 1700.0, status = CounterOfferStatus.ACCEPTED)) }
    override suspend fun reject(counterOfferId: String): Result<CounterOfferView> { rejected = counterOfferId; return Result.success(CounterOfferView(counterOfferId, "b1", amount = 1700.0, status = CounterOfferStatus.REJECTED)) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MyBidsCounterOfferTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun bid(id: String, status: BidStatus) = BidView(id, "r$id", amount = 1900.0, status = status, createdAt = "2026-11-10T10:00:00-05:00")

    @Test fun `solo consulta contraofertas de ofertas PENDING y muestra la pendiente`() = runTest {
        val nego = FakeNegotiation(mapOf("b1" to listOf(CounterOfferView("co1", "b1", amount = 1700.0, status = CounterOfferStatus.PENDING))))
        val loads = FakeLoads(bids = listOf(req("r1", "LIMA", 1000.0, 2000.0, myBid = bid("b1", BidStatus.PENDING)), req("r2", "LIMA", 1000.0, 2000.0, myBid = bid("b2", BidStatus.ACCEPTED))))
        val vm = MyBidsViewModel(loads, nego)
        assertEquals(listOf("b1"), nego.queried)
        assertEquals("co1", vm.state.value.counters["b1"]?.id)
        assertNull(vm.state.value.counters["b2"])
    }

    @Test fun `aceptar contraoferta pide confirmacion, acepta y refresca`() = runTest {
        val co = CounterOfferView("co1", "b1", amount = 1700.0, status = CounterOfferStatus.PENDING)
        val nego = FakeNegotiation(mapOf("b1" to listOf(co)))
        val vm = MyBidsViewModel(FakeLoads(bids = listOf(req("r1", "LIMA", 1000.0, 2000.0, myBid = bid("b1", BidStatus.PENDING)))), nego)
        vm.askAccept(co)
        assertTrue(vm.state.value.confirm?.accept == true)
        assertNull(nego.accepted)
        vm.confirm()
        assertEquals("co1", nego.accepted)
        assertNull(vm.state.value.confirm)
        assertEquals(R.string.loads_counter_accepted, vm.state.value.message?.textRes)
    }

    @Test fun `rechazar contraoferta llama a reject`() = runTest {
        val co = CounterOfferView("co1", "b1", amount = 1700.0, status = CounterOfferStatus.PENDING)
        val nego = FakeNegotiation(mapOf("b1" to listOf(co)))
        val vm = MyBidsViewModel(FakeLoads(bids = listOf(req("r1", "LIMA", 1000.0, 2000.0, myBid = bid("b1", BidStatus.PENDING)))), nego)
        vm.askReject(co); vm.confirm()
        assertEquals("co1", nego.rejected)
    }
}
