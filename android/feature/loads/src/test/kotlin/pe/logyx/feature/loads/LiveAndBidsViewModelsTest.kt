package pe.logyx.feature.loads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
import pe.logyx.core.data.NegotiationRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val me = UserInfo("u1", "c@logyx.pe", "Carla Quispe", Role.ADMIN, "org1", "Andes SAC", OrgType.CARRIER, true, organizationCity = "Lima")

private class Auth : AuthRepository {
    override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.LoggedIn(me))
    override suspend fun login(email: String, password: String) = Result.success(me)
    override suspend fun logout() {}
}

private class Catalog : CatalogRepository {
    override suspend fun cities() = Result.success(emptyList<CityItem>())
    override suspend fun organization(id: String) = Result.failure<PublicOrganization>(IllegalStateException())
}

private class NoNegotiation : NegotiationRepository {
    override suspend fun threads(requestId: String) = Result.success(emptyList<ChatThreadView>())
    override suspend fun messages(requestId: String, carrierOrgId: String, before: String?) = Result.success(CursorPage<MessageView>(emptyList(), null))
    override suspend fun send(requestId: String, carrierOrgId: String, body: String) = Result.failure<MessageView>(IllegalStateException())
    override suspend fun counterOffers(bidId: String) = Result.success(emptyList<CounterOfferView>())
    override suspend fun propose(bidId: String, amount: Double, message: String?) = Result.failure<CounterOfferView>(IllegalStateException())
    override suspend fun accept(counterOfferId: String) = Result.failure<CounterOfferView>(IllegalStateException())
    override suspend fun reject(counterOfferId: String) = Result.failure<CounterOfferView>(IllegalStateException())
}

private fun r(id: String, bidCreated: String? = null, bidStatus: BidStatus = BidStatus.PENDING) = RequestView(
    id = id, originCity = "LIMA", destinationCity = "JULIACA", cargoType = CargoType.GENERAL, weightKg = 1000.0,
    suggestedPrice = 1000.0, priceFloor = 800.0, status = RequestStatus.PUBLISHED,
    myBid = bidCreated?.let { BidView("b$id", id, amount = 900.0, status = bidStatus, createdAt = it) },
)

private class Loads(var bids: Result<List<RequestView>> = Result.success(emptyList()), var market: List<RequestView> = emptyList()) : LoadsRepository {
    var marketCalls = 0
    override suspend fun marketplace() = Result.success(market)
    override suspend fun marketplacePage(filter: MarketplaceFilter, page: Int, size: Int): Result<Page<RequestView>> {
        marketCalls++
        return Result.success(Page(market.drop(page * size).take(size), page, size, market.size))
    }
    override suspend fun load(id: String) = Result.failure<RequestView>(IllegalStateException())
    override suspend fun myBids() = bids
    override suspend fun vehicles() = Result.success(emptyList<VehicleView>())
    override suspend fun placeBid(requestId: String, vehicleId: String, amount: Double, notes: String?) = Result.failure<BidView>(IllegalStateException())
}

/** Mis ofertas y tiempo real del feed (el resto de Home/feed está en LoadsViewModelsTest). */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveAndBidsViewModelsTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `mis ofertas ordena por la oferta mas reciente`() = runTest {
        val loads = Loads(Result.success(listOf(r("1", "2026-11-09T10:00:00Z"), r("2", "2026-11-10T10:00:00Z"), r("3", "2026-11-08T10:00:00Z"))))
        val vm = MyBidsViewModel(loads, NoNegotiation(), FakeRealtime())
        assertEquals(listOf("2", "1", "3"), vm.state.value.items.map { it.id })
        assertTrue(!vm.state.value.loading)
    }

    @Test fun `mis ofertas muestra el error de red y reintenta con refresh`() = runTest {
        val loads = Loads(Result.failure(ApiException(0, "NETWORK", "Sin conexión")))
        val vm = MyBidsViewModel(loads, NoNegotiation(), FakeRealtime())
        assertEquals("Sin conexión", vm.state.value.error)
        loads.bids = Result.success(listOf(r("1", "2026-11-09T10:00:00Z")))
        vm.refresh()
        assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.items.size)
    }

    @Test fun `mis ofertas refresca en silencio con bid accepted`() = runTest(UnconfinedTestDispatcher()) {
        val loads = Loads(Result.success(listOf(r("1", "2026-11-09T10:00:00Z"))))
        val rt = FakeRealtime()
        val vm = MyBidsViewModel(loads, NoNegotiation(), rt)
        val job = launch { vm.liveLoop() }
        loads.bids = Result.success(listOf(r("1", "2026-11-09T10:00:00Z", BidStatus.ACCEPTED)))
        rt.emit(RealtimeTypes.BID_ACCEPTED, "requestId" to "1")
        assertEquals(BidStatus.ACCEPTED, vm.state.value.items.single().myBid?.status)
        assertTrue(!vm.state.value.refreshing)
        job.cancel()
    }

    @Test fun `feed recarga la primera pagina con auction updated y respeta la paginacion`() = runTest(UnconfinedTestDispatcher()) {
        val loads = Loads(market = (1..30).map { r("$it") })
        val rt = FakeRealtime()
        val vm = LoadsFeedViewModel(Auth(), loads, Catalog(), rt)
        val job = launch { vm.liveLoop() }
        val before = loads.marketCalls
        rt.emit(RealtimeTypes.AUCTION_UPDATED, "requestId" to "1")
        assertEquals(before + 1, loads.marketCalls)
        vm.loadMore()
        val afterMore = loads.marketCalls
        rt.emit(RealtimeTypes.AUCTION_UPDATED, "requestId" to "1")
        assertEquals(afterMore, loads.marketCalls, "con page > 0 no se recarga solo")
        job.cancel()
    }
}

/** B5: Mis ofertas con perdidas, canceladas y retiradas (F15), orden y filtro (F51). */
@OptIn(ExperimentalCoroutinesApi::class)
class MyBidsOutcomeTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun item(id: String, created: String, bid: BidStatus, request: RequestStatus, auction: AuctionStatus = AuctionStatus.OPEN) =
        r(id, created, bid).copy(
            status = request,
            auction = AuctionView("a$id", auction, "2099-01-01T00:00:00Z", 5, 3, closeReason = if (auction == AuctionStatus.OPEN) null else CloseReason.MAX_BIDS, closedAt = "2026-11-10T09:00:00-05:00"),
            myBidStanding = if (bid == BidStatus.WITHDRAWN) null else MyBidStanding(2, 3, false, BidStandingBand.WITHIN_5_PCT, finalStanding = auction != AuctionStatus.OPEN),
        )

    private val all = listOf(
        item("lost", "2026-11-10T10:00:00Z", BidStatus.REJECTED, RequestStatus.MATCHED, AuctionStatus.CLOSED),
        item("open", "2026-11-08T10:00:00Z", BidStatus.PENDING, RequestStatus.PUBLISHED),
        item("cancelled", "2026-11-11T10:00:00Z", BidStatus.REJECTED, RequestStatus.CANCELLED, AuctionStatus.CLOSED),
        item("withdrawn", "2026-11-09T10:00:00Z", BidStatus.WITHDRAWN, RequestStatus.PUBLISHED),
        item("won", "2026-11-07T10:00:00Z", BidStatus.ACCEPTED, RequestStatus.MATCHED, AuctionStatus.CLOSED),
    )

    @Test fun `lista las solicitudes perdidas canceladas y retiradas con su resultado y las activas primero`() = runTest {
        val vm = MyBidsViewModel(Loads(Result.success(all)), NoNegotiation(), FakeRealtime())
        val s = vm.state.value
        assertEquals("open", s.items.first().id, "la oferta viva va primero aunque sea la más antigua")
        assertEquals(
            mapOf(
                "open" to BidOutcome.OPEN, "lost" to BidOutcome.LOST, "cancelled" to BidOutcome.REQUEST_CANCELLED,
                "withdrawn" to BidOutcome.WITHDRAWN, "won" to BidOutcome.WON,
            ),
            s.items.associate { it.id to it.myBidOutcome },
        )
        assertEquals(1, s.activeCount)
    }

    @Test fun `el filtro separa activas y cerradas`() = runTest {
        val vm = MyBidsViewModel(Loads(Result.success(all)), NoNegotiation(), FakeRealtime())
        assertEquals(5, vm.state.value.visible.size)
        vm.setFilter(MyBidsFilter.Active)
        assertEquals(listOf("open"), vm.state.value.visible.map { it.id })
        vm.setFilter(MyBidsFilter.Closed)
        assertEquals(listOf("cancelled", "lost", "withdrawn", "won"), vm.state.value.visible.map { it.id })
    }

    @Test fun `solo consulta contraofertas de las ofertas vivas`() = runTest {
        val nego = object : NegotiationRepository by NoNegotiation() {
            val asked = mutableListOf<String>()
            override suspend fun counterOffers(bidId: String): Result<List<CounterOfferView>> { asked += bidId; return Result.success(emptyList()) }
        }
        MyBidsViewModel(Loads(Result.success(all)), nego, FakeRealtime())
        assertEquals(listOf("bopen"), nego.asked)
    }

    @Test fun `una contraoferta mia pendiente aparece como esperando a la PYME`() = runTest {
        val mine = CounterOfferView("c", "bopen", amount = 850.0, status = CounterOfferStatus.PENDING, proposedByCarrier = true)
        val nego = object : NegotiationRepository by NoNegotiation() {
            override suspend fun counterOffers(bidId: String) = Result.success(listOf(mine))
        }
        val vm = MyBidsViewModel(Loads(Result.success(all)), nego, FakeRealtime())
        assertTrue(vm.state.value.counters.getValue("bopen").proposedByCarrier)
    }
}
