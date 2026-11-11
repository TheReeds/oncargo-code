package pe.logyx.feature.requests

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.NegotiationRepository
import pe.logyx.core.data.RequestsRepository
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun bid(id: String, amount: Double, status: BidStatus = BidStatus.PENDING) =
    BidView(id, "r1", carrierName = "Carrier $id", amount = amount, status = status)

private val request = RequestView(
    id = "r1", originCity = "LIMA", destinationCity = "JULIACA", cargoType = CargoType.GENERAL, weightKg = 3000.0,
    suggestedPrice = 2000.0, priceFloor = 1500.0, status = RequestStatus.PUBLISHED,
)

private class FakeRequests(var bids: List<BidView>, var acceptResult: Result<AcceptResult>) : RequestsRepository {
    var accepted: String? = null
    override suspend fun mine() = Result.success(listOf(request))
    override suspend fun detail(id: String) = Result.success(request)
    override suspend fun bids(requestId: String) = Result.success(bids)
    override suspend fun acceptBid(bidId: String): Result<AcceptResult> { accepted = bidId; return acceptResult }
    override suspend fun cancel(requestId: String) = Result.success(request.copy(status = RequestStatus.CANCELLED))
}

private class FakeCatalog : CatalogRepository {
    override suspend fun cities() = Result.success(listOf(CityItem("LIMA", "Lima", -12.0, -77.0), CityItem("JULIACA", "Juliaca", -15.5, -70.1)))
    override suspend fun organization(id: String) = Result.failure<PublicOrganization>(IllegalStateException())
}

private class FakeNegotiation(var proposeResult: Result<CounterOfferView> = Result.success(CounterOfferView("co1", "a", amount = 1700.0, status = CounterOfferStatus.PENDING))) : NegotiationRepository {
    var proposed: Triple<String, Double, String?>? = null
    override suspend fun threads(requestId: String) = Result.success(emptyList<ChatThreadView>())
    override suspend fun messages(requestId: String, carrierOrgId: String, before: String?) = Result.success(CursorPage<MessageView>(emptyList(), null))
    override suspend fun send(requestId: String, carrierOrgId: String, body: String) = Result.failure<MessageView>(IllegalStateException())
    override suspend fun counterOffers(bidId: String) = Result.success(listOf(CounterOfferView("old", bidId, amount = 1800.0, status = CounterOfferStatus.REJECTED)))
    override suspend fun propose(bidId: String, amount: Double, message: String?): Result<CounterOfferView> { proposed = Triple(bidId, amount, message); return proposeResult }
    override suspend fun accept(counterOfferId: String) = Result.failure<CounterOfferView>(IllegalStateException())
    override suspend fun reject(counterOfferId: String) = Result.failure<CounterOfferView>(IllegalStateException())
}

@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModelsTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `ofertas ordenadas por monto con mejor precio y diferencia vs sugerido`() {
        val rows = buildBidRows(listOf(bid("a", 1900.0), bid("b", 1600.0), bid("c", 2100.0)), 2000.0)
        assertEquals(listOf("b", "a", "c"), rows.map { it.bid.id })
        assertEquals(listOf(true, false, false), rows.map { it.isBest })
        assertEquals(-20.0, rows[0].diffVsSuggestedPct!!, 0.001)
        assertEquals(5.0, rows[2].diffVsSuggestedPct!!, 0.001)
    }

    @Test fun `aceptar pide confirmacion y entrega el id del envio`() = runTest {
        val repo = FakeRequests(listOf(bid("a", 1900.0), bid("b", 1600.0)), Result.success(AcceptResult("ship-9")))
        val vm = RequestDetailViewModel("r1", repo, FakeCatalog(), FakeNegotiation(), FakeRealtime())
        assertEquals(2, vm.state.value.bids.size)
        assertTrue(vm.state.value.canDecide)

        vm.askAccept(bid("b", 1600.0))
        assertEquals("b", vm.state.value.confirmBid?.id)
        vm.confirmAccept()
        assertEquals("b", repo.accepted)
        assertEquals("ship-9", vm.state.value.createdShipmentId)
        vm.navigationHandled()
        assertNull(vm.state.value.createdShipmentId)
    }

    @Test fun `error al aceptar muestra el mensaje de la API`() = runTest {
        val repo = FakeRequests(listOf(bid("a", 1900.0)), Result.failure(ApiException(409, "LOGYX-MKT-009", "La solicitud ya fue adjudicada")))
        val vm = RequestDetailViewModel("r1", repo, FakeCatalog(), FakeNegotiation(), FakeRealtime())
        vm.askAccept(bid("a", 1900.0)); vm.confirmAccept()
        assertEquals("La solicitud ya fue adjudicada", vm.state.value.message?.text)
        assertNull(vm.state.value.createdShipmentId)
    }

    @Test fun `contraoferta valida piso y monto menor antes de confirmar`() = runTest {
        val nego = FakeNegotiation()
        val vm = RequestDetailViewModel("r1", FakeRequests(listOf(bid("a", 1900.0)), Result.failure(IllegalStateException())), FakeCatalog(), nego, FakeRealtime())
        vm.openCounter(bid("a", 1900.0))
        assertEquals(1, vm.state.value.counter?.previous?.size)

        vm.onCounterAmount("1400"); vm.askConfirmCounter()
        assertTrue(vm.state.value.counter?.amountError != null)
        assertTrue(vm.state.value.counter?.confirming == false)
        vm.onCounterAmount("1900"); vm.askConfirmCounter()
        assertTrue(vm.state.value.counter?.amountError != null)
        vm.onCounterAmount("1700"); vm.askConfirmCounter()
        assertTrue(vm.state.value.counter?.confirming == true)
        assertNull(nego.proposed)
    }

    @Test fun `contraoferta enviada cierra la hoja y avisa`() = runTest {
        val nego = FakeNegotiation()
        val vm = RequestDetailViewModel("r1", FakeRequests(listOf(bid("a", 1900.0)), Result.failure(IllegalStateException())), FakeCatalog(), nego, FakeRealtime())
        vm.openCounter(bid("a", 1900.0))
        vm.onCounterAmount("1700"); vm.onCounterMessage(" ¿Podemos? "); vm.askConfirmCounter(); vm.confirmCounter()
        assertEquals(Triple("a", 1700.0, "¿Podemos?"), nego.proposed)
        assertNull(vm.state.value.counter)
        assertEquals(R.string.requests_counter_sent, vm.state.value.message?.textRes)
    }

    @Test fun `error de campo de la API se muestra en el campo monto`() = runTest {
        val nego = FakeNegotiation(Result.failure(ApiException(422, "LOGYX-NEG-005", "El monto no puede ser menor al piso de S/ 1,500.00.", field = "amount")))
        val vm = RequestDetailViewModel("r1", FakeRequests(listOf(bid("a", 1900.0)), Result.failure(IllegalStateException())), FakeCatalog(), nego, FakeRealtime())
        vm.openCounter(bid("a", 1900.0))
        vm.onCounterAmount("1600"); vm.askConfirmCounter(); vm.confirmCounter()
        val c = vm.state.value.counter!!
        assertEquals(pe.logyx.core.designsystem.components.UiText.Raw("El monto no puede ser menor al piso de S/ 1,500.00."), c.amountError)
        assertTrue(!c.sending && !c.confirming)
        assertNull(vm.state.value.message)
    }

    @Test fun `tiempo real refresca la comparacion con bid placed de la solicitud y no con otra`() = runTest(UnconfinedTestDispatcher()) {
        val repo = FakeRequests(listOf(bid("a", 1900.0)), Result.success(AcceptResult("s")))
        val rt = FakeRealtime()
        val vm = RequestDetailViewModel("r1", repo, FakeCatalog(), FakeNegotiation(), rt)
        val job = launch { vm.liveLoop() }
        assertEquals(1, vm.state.value.bids.size)
        repo.bids = listOf(bid("a", 1900.0), bid("b", 1600.0))
        rt.emit(RealtimeTypes.BID_PLACED, "requestId" to "otra", "bidId" to "b")
        assertEquals(1, vm.state.value.bids.size)
        rt.emit(RealtimeTypes.BID_PLACED, "requestId" to "r1", "bidId" to "b")
        assertEquals(2, vm.state.value.bids.size)
        assertEquals("b", vm.state.value.bids.first().bid.id)
        assertFalse(vm.state.value.refreshing)
        job.cancel()
    }
}
