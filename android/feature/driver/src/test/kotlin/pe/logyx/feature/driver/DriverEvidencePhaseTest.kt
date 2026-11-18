package pe.logyx.feature.driver

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
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.EvidenceItem
import pe.logyx.core.data.EvidenceRepository
import pe.logyx.core.data.EvidenceState
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.DriverView
import pe.logyx.core.model.PublicOrganization
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.StopStatus
import pe.logyx.core.model.StopType
import pe.logyx.core.model.StopView
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun st(id: String, seq: Int, type: StopType, status: StopStatus) =
    StopView(id, seq, type, if (type == StopType.PICKUP) "LIMA" else "JULIACA", null, status)

private fun tripOf(status: ShipmentStatus, vararg stops: StopView) =
    ShipmentView("s1", originCity = "LIMA", destinationCity = "JULIACA", status = status, stops = stops.toList())

private class PhaseShipments(var current: ShipmentView?, var history: Result<List<ShipmentView>> = Result.success(emptyList())) : ShipmentsRepository {
    var historyCalls = 0
    override suspend fun driverShipments() = Result.success(listOfNotNull(current))
    override suspend fun driverActive() = Result.success(current)
    override suspend fun driverHistory(): Result<List<ShipmentView>> { historyCalls++; return history }
    override suspend fun arrive(shipmentId: String, stopId: String) = Result.success(current!!)
    override suspend fun complete(shipmentId: String, stopId: String) = Result.success(current!!)
    override suspend fun list() = Result.success(listOfNotNull(current))
    override suspend fun detail(id: String) = Result.success(current!!)
    override suspend fun drivers() = Result.success(emptyList<DriverView>())
    override suspend fun assignDriver(shipmentId: String, driverId: String, reason: String?) = Result.success(current!!)
}

private class PhaseCatalog : CatalogRepository {
    override suspend fun cities() = Result.success(listOf(CityItem("LIMA", "Lima", -12.0, -77.0), CityItem("JULIACA", "Juliaca", -15.5, -70.1)))
    override suspend fun organization(id: String) = Result.failure<PublicOrganization>(IllegalStateException())
}

private class PhaseEvidence(initial: List<EvidenceItem> = emptyList()) : EvidenceRepository {
    private val _items = MutableStateFlow(initial)
    override val items: StateFlow<List<EvidenceItem>> = _items
    val discarded = mutableListOf<String>()
    override suspend fun load(shipmentId: String) = Result.success(Unit)
    override suspend fun enqueue(shipmentId: String, stopId: String, type: DocumentType, source: ByteArray, mime: String) =
        EvidenceItem("e", shipmentId, stopId, type, EvidenceState.UPLOADED).also { _items.value = _items.value + it }
    override suspend fun retry(id: String) = Unit
    override suspend fun retryAll(shipmentId: String) = Unit
    override suspend fun discard(id: String) { discarded += id; _items.value = _items.value.filter { it.id != id } }
}

/** RN06 / F14: la evidencia solo entre la llegada y el cierre de la entrega; DOC-008 y el historial del conductor. */
@OptIn(ExperimentalCoroutinesApi::class)
class DriverEvidencePhaseTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(shipments: PhaseShipments, evidence: PhaseEvidence = PhaseEvidence()) =
        DriverTripViewModel(shipments, PhaseCatalog(), evidence, FakeRealtime())

    private fun phase(vararg stops: StopView, status: ShipmentStatus = ShipmentStatus.IN_TRANSIT) =
        vm(PhaseShipments(tripOf(status, *stops))).state.value.evidencePhase

    @Test fun `antes de llegar a la entrega no se ofrece y se explica`() {
        assertEquals(EvidencePhase.NONE, phase(st("p", 1, StopType.PICKUP, StopStatus.PENDING), st("d", 2, StopType.DELIVERY, StopStatus.PENDING)))
        assertEquals(EvidencePhase.NONE, phase(st("p", 1, StopType.PICKUP, StopStatus.ARRIVED), st("d", 2, StopType.DELIVERY, StopStatus.PENDING)))
        // recogida completada: el siguiente paso es llegar a destino -> "se pide al llegar"
        assertEquals(EvidencePhase.NOT_YET, phase(st("p", 1, StopType.PICKUP, StopStatus.COMPLETED), st("d", 2, StopType.DELIVERY, StopStatus.PENDING)))
    }

    @Test fun `entre la llegada y el cierre de la entrega se abre`() {
        assertEquals(EvidencePhase.OPEN, phase(st("p", 1, StopType.PICKUP, StopStatus.COMPLETED), st("d", 2, StopType.DELIVERY, StopStatus.ARRIVED)))
    }

    @Test fun `con la entrega cerrada ya no se ofrece`() {
        val delivered = phase(
            st("p", 1, StopType.PICKUP, StopStatus.COMPLETED), st("d", 2, StopType.DELIVERY, StopStatus.COMPLETED),
            status = ShipmentStatus.DELIVERED,
        )
        assertEquals(EvidencePhase.CLOSED, delivered)
    }

    @Test fun `DOC-008 la evidencia descartada se avisa y el conductor la despeja`() = runTest {
        val closed = EvidenceItem("c1", "s1", "d", DocumentType.DELIVERY_PHOTO, EvidenceState.CLOSED, error = "La entrega ya se cerró")
        val evidence = PhaseEvidence(listOf(closed, EvidenceItem("u1", "s1", "d", DocumentType.DELIVERY_PHOTO, EvidenceState.UPLOADED)))
        val v = vm(PhaseShipments(tripOf(ShipmentStatus.IN_TRANSIT, st("p", 1, StopType.PICKUP, StopStatus.COMPLETED), st("d", 2, StopType.DELIVERY, StopStatus.ARRIVED))), evidence)
        assertEquals(listOf("c1"), v.state.value.closedEvidence.map { it.id })
        assertTrue(v.state.value.hasUploadedPhoto, "una foto ya subida sigue habilitando la entrega")
        v.dismissClosedEvidence()
        assertEquals(listOf("c1"), evidence.discarded)
        assertTrue(v.state.value.closedEvidence.isEmpty())
    }

    @Test fun `una evidencia cerrada no cuenta como pendiente de reintento ni como foto subida`() = runTest {
        val closed = EvidenceItem("c1", "s1", "d", DocumentType.DELIVERY_PHOTO, EvidenceState.CLOSED)
        val v = vm(PhaseShipments(tripOf(ShipmentStatus.IN_TRANSIT, st("p", 1, StopType.PICKUP, StopStatus.COMPLETED), st("d", 2, StopType.DELIVERY, StopStatus.ARRIVED))), PhaseEvidence(listOf(closed)))
        assertFalse(v.state.value.hasUploadedPhoto)
        assertTrue(v.state.value.photoBlocked)
    }

    @Test fun `viajes anteriores se cargan con scope HISTORY y se limitan`() {
        val history = (1..15).map { ShipmentView("h$it", originCity = "LIMA", destinationCity = "PUNO", status = ShipmentStatus.DELIVERED) }
        val repo = PhaseShipments(tripOf(ShipmentStatus.IN_TRANSIT, st("p", 1, StopType.PICKUP, StopStatus.PENDING)), Result.success(history))
        val v = vm(repo)
        assertEquals(1, repo.historyCalls)
        assertEquals(10, v.state.value.history.size)
        assertEquals("h1", v.state.value.history.first().id)
    }

    @Test fun `si el historial falla la pantalla sigue funcionando sin la seccion`() {
        val repo = PhaseShipments(tripOf(ShipmentStatus.IN_TRANSIT, st("p", 1, StopType.PICKUP, StopStatus.PENDING)), Result.failure(ApiException(0, "NETWORK", "sin red")))
        val v = vm(repo)
        assertTrue(v.state.value.history.isEmpty())
        assertFalse(v.state.value.loading)
        assertEquals("s1", v.state.value.trip?.id)
    }

    @Test fun `sin viaje activo igual muestra el historial`() {
        val history = listOf(ShipmentView("h1", originCity = "LIMA", destinationCity = "PUNO", status = ShipmentStatus.CANCELLED))
        val v = vm(PhaseShipments(null, Result.success(history)))
        assertEquals(null, v.state.value.trip)
        assertEquals(1, v.state.value.history.size)
    }
}
