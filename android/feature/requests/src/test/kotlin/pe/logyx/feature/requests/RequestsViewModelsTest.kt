package pe.logyx.feature.requests

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.RequestsRepository
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun bid(id: String, amount: Double, status: BidStatus = BidStatus.PENDING) =
    BidView(id, "r1", carrierName = "Carrier $id", amount = amount, status = status)

private val request = RequestView(
    id = "r1", originCity = "LIMA", destinationCity = "JULIACA", cargoType = CargoType.GENERAL, weightKg = 3000.0,
    suggestedPrice = 2000.0, priceFloor = 1500.0, status = RequestStatus.PUBLISHED,
)

private class FakeRequests(val bids: List<BidView>, var acceptResult: Result<AcceptResult>) : RequestsRepository {
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
        val vm = RequestDetailViewModel("r1", repo, FakeCatalog())
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
        val vm = RequestDetailViewModel("r1", repo, FakeCatalog())
        vm.askAccept(bid("a", 1900.0)); vm.confirmAccept()
        assertEquals("La solicitud ya fue adjudicada", vm.state.value.message)
        assertNull(vm.state.value.createdShipmentId)
    }
}
