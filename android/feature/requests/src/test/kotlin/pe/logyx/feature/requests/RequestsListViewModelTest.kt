package pe.logyx.feature.requests

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
import pe.logyx.core.data.LoadsRepository
import pe.logyx.core.data.RequestsRepository
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun req(id: String, created: String) = RequestView(
    id = id, originCity = "LIMA", destinationCity = "JULIACA", cargoType = CargoType.GENERAL, weightKg = 3000.0,
    suggestedPrice = 2000.0, priceFloor = 1500.0, status = RequestStatus.PUBLISHED, createdAt = created,
)

private class ListRequests(var result: Result<List<RequestView>>) : RequestsRepository {
    override suspend fun mine() = result
    override suspend fun detail(id: String) = Result.failure<RequestView>(IllegalStateException())
    override suspend fun bids(requestId: String) = Result.success(emptyList<BidView>())
    override suspend fun acceptBid(bidId: String) = Result.failure<AcceptResult>(IllegalStateException())
    override suspend fun cancel(requestId: String) = Result.failure<RequestView>(IllegalStateException())
}

private class DashLoads(var dash: Result<DashboardSummary>) : LoadsRepository {
    override suspend fun marketplace() = Result.success(emptyList<RequestView>())
    override suspend fun dashboard() = dash
    override suspend fun load(id: String) = Result.failure<RequestView>(IllegalStateException())
    override suspend fun myBids() = Result.success(emptyList<RequestView>())
    override suspend fun vehicles() = Result.success(emptyList<VehicleView>())
    override suspend fun placeBid(requestId: String, vehicleId: String, amount: Double, notes: String?) = Result.failure<BidView>(IllegalStateException())
}

/** Home de la PYME (pestaña Solicitudes): lista + reservas de espacio activas del dashboard. */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestsListViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `ordena por recientes y expone activeTripBookings del dashboard`() = runTest {
        val vm = RequestsViewModel(
            ListRequests(Result.success(listOf(req("a", "2026-11-09T10:00:00Z"), req("b", "2026-11-10T10:00:00Z")))),
            FakeRealtime(), DashLoads(Result.success(DashboardSummary(role = "PYME", activeTripBookings = 3))),
        )
        val s = vm.state.value
        assertEquals(listOf("b", "a"), s.items.map { it.id })
        assertEquals(3, s.activeTripBookings)
        assertTrue(!s.loading)
    }

    @Test fun `si el dashboard falla la lista igual carga y no hay contador`() = runTest {
        val vm = RequestsViewModel(
            ListRequests(Result.success(listOf(req("a", "2026-11-09T10:00:00Z")))), FakeRealtime(),
            DashLoads(Result.failure(UnsupportedOperationException())),
        )
        assertEquals(1, vm.state.value.items.size)
        assertNull(vm.state.value.activeTripBookings)
    }

    @Test fun `error de la lista muestra el mensaje del API`() = runTest {
        val vm = RequestsViewModel(
            ListRequests(Result.failure(ApiException(0, "NETWORK", "Sin conexión"))), FakeRealtime(), DashLoads(Result.success(DashboardSummary())),
        )
        assertEquals("Sin conexión", vm.state.value.error)
    }

    @Test fun `tiempo real refresca la lista con trip_booking updated y actualiza el contador`() = runTest(UnconfinedTestDispatcher()) {
        val loads = DashLoads(Result.success(DashboardSummary(role = "PYME", activeTripBookings = 1)))
        val rt = FakeRealtime()
        val vm = RequestsViewModel(ListRequests(Result.success(emptyList())), rt, loads)
        val job = launch { vm.liveLoop() }
        assertEquals(1, vm.state.value.activeTripBookings)
        loads.dash = Result.success(DashboardSummary(role = "PYME", activeTripBookings = 2))
        rt.emit(RealtimeTypes.TRIP_BOOKING_UPDATED, "tripId" to "t1")
        assertEquals(2, vm.state.value.activeTripBookings)
        job.cancel()
    }
}
