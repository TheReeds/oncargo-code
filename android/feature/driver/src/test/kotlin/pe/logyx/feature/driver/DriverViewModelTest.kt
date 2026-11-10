package pe.logyx.feature.driver

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
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun stop(id: String, seq: Int, type: StopType, status: StopStatus) =
    StopView(id, seq, type, if (type == StopType.PICKUP) "LIMA" else "JULIACA", "Dirección $seq", status)

private fun trip(status: ShipmentStatus, vararg stops: StopView) =
    ShipmentView("s1", originCity = "LIMA", destinationCity = "JULIACA", status = status, stops = stops.toList())

/** Simula el backend: arrive/complete avanzan la máquina de estados de las paradas. */
private class FakeShipments(var current: ShipmentView, var failNext: ApiException? = null) : ShipmentsRepository {
    override suspend fun driverShipments() = Result.success(listOf(current))
    override suspend fun driverActive() = Result.success<ShipmentView?>(current)
    private fun update(stopId: String, to: StopStatus): Result<ShipmentView> {
        failNext?.let { failNext = null; return Result.failure(it) }
        val stops = current.stops.map { if (it.id == stopId) it.copy(status = to) else it }
        val allDone = stops.all { it.status == StopStatus.COMPLETED }
        current = current.copy(stops = stops, status = if (allDone) ShipmentStatus.DELIVERED else ShipmentStatus.IN_TRANSIT)
        return Result.success(current)
    }
    override suspend fun arrive(shipmentId: String, stopId: String) = update(stopId, StopStatus.ARRIVED)
    override suspend fun complete(shipmentId: String, stopId: String) = update(stopId, StopStatus.COMPLETED)
    override suspend fun list() = Result.success(listOf(current))
    override suspend fun detail(id: String) = Result.success(current)
    override suspend fun drivers() = Result.success(emptyList<DriverView>())
    override suspend fun assignDriver(shipmentId: String, driverId: String) = Result.success(current)
}

private class FakeCatalog : CatalogRepository {
    override suspend fun cities() = Result.success(listOf(CityItem("LIMA", "Lima", -12.0, -77.0), CityItem("JULIACA", "Juliaca", -15.5, -70.1)))
    override suspend fun organization(id: String) = Result.failure<PublicOrganization>(IllegalStateException())
}

@OptIn(ExperimentalCoroutinesApi::class)
class DriverViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val fresh = trip(
        ShipmentStatus.PENDING,
        stop("p", 1, StopType.PICKUP, StopStatus.PENDING),
        stop("d", 2, StopType.DELIVERY, StopStatus.PENDING),
    )

    @Test fun `la accion sigue la secuencia llegue - recogida - destino - entregado`() {
        val labels = mutableListOf<DriverStep?>()
        var t = fresh
        fun step(op: (StopView) -> StopStatus) {
            labels += t.driverAction()?.step
            val a = t.driverAction() ?: return
            t = t.copy(stops = t.stops.map { if (it.id == a.stop.id) it.copy(status = op(it)) else it })
        }
        repeat(4) { i -> step { if (i % 2 == 0) StopStatus.ARRIVED else StopStatus.COMPLETED } }
        labels += t.driverAction()?.step
        assertEquals(listOf(DriverStep.ARRIVE_PICKUP, DriverStep.PICKED_UP, DriverStep.ARRIVE_DESTINATION, DriverStep.DELIVERED, null), labels)
    }

    @Test fun `avanzar el viaje completo hasta entregado`() = runTest {
        val repo = FakeShipments(fresh)
        val vm = DriverTripViewModel(repo, FakeCatalog())
        assertNotNull(vm.state.value.origin)
        assertEquals(DriverStep.ARRIVE_PICKUP, vm.state.value.action?.step)
        assertEquals(StopOp.ARRIVE, vm.state.value.action?.op)

        vm.advance(); assertEquals(DriverStep.PICKED_UP, vm.state.value.action?.step)
        assertEquals(StopOp.COMPLETE, vm.state.value.action?.op)
        vm.advance(); assertEquals(DriverStep.ARRIVE_DESTINATION, vm.state.value.action?.step)
        vm.advance(); assertEquals(DriverStep.DELIVERED, vm.state.value.action?.step)
        assertEquals(false, vm.state.value.delivered)
        vm.advance()
        assertTrue(vm.state.value.delivered)
        assertNull(vm.state.value.action)
        assertEquals(ShipmentStatus.DELIVERED, vm.state.value.trip?.status)
        assertEquals(DriverNotice.DELIVERED, vm.state.value.notice)
        vm.onNoticeShown(); assertNull(vm.state.value.notice)
    }

    @Test fun `error al avanzar conserva el viaje y muestra el mensaje`() = runTest {
        val repo = FakeShipments(fresh, failNext = ApiException(409, "LOGYX-SHP-002", "La parada anterior no está completada"))
        val vm = DriverTripViewModel(repo, FakeCatalog())
        vm.advance()
        assertEquals("La parada anterior no está completada", vm.state.value.actionError)
        assertEquals(DriverStep.ARRIVE_PICKUP, vm.state.value.action?.step)
        assertEquals(false, vm.state.value.acting)
    }

}
