package pe.logyx.feature.driver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.DriverActionQueue
import pe.logyx.core.data.DriverTripCache
import pe.logyx.core.data.EvidenceItem
import pe.logyx.core.data.EvidenceRepository
import pe.logyx.core.data.EvidenceState
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.data.TextStore
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val clock = Clock.fixed(Instant.parse("2026-11-18T13:15:00Z"), ZoneOffset.ofHours(-5))
private val NO_NETWORK = ApiException(0, "NETWORK", "No pudimos conectar con OnCargo. Revisa tu conexión e inténtalo de nuevo.")

private fun stop(id: String, seq: Int, type: StopType, status: StopStatus) =
    StopView(id, seq, type, if (type == StopType.PICKUP) "LIMA" else "JULIACA", "Dirección $seq", status)

private fun trip(status: ShipmentStatus, vararg stops: StopView) =
    ShipmentView("s1", originCity = "LIMA", destinationCity = "JULIACA", status = status, stops = stops.toList())

/** Backend con interruptor de red: sin red todo falla con `status = 0`; con red avanza la máquina de estados. */
private class NetShipments(var current: ShipmentView) : ShipmentsRepository {
    var online = true
    val calls = mutableListOf<Pair<String, String?>>()
    override suspend fun driverShipments() = Result.success(listOf(current))
    override suspend fun driverActive(): Result<ShipmentView?> = if (online) Result.success(current) else Result.failure(NO_NETWORK)
    override suspend fun driverHistory(): Result<List<ShipmentView>> = if (online) Result.success(emptyList()) else Result.failure(NO_NETWORK)
    private fun update(op: String, stopId: String, at: String?, to: StopStatus): Result<ShipmentView> {
        calls += op to at
        if (!online) return Result.failure(NO_NETWORK)
        val stops = current.stops.map { if (it.id == stopId) it.copy(status = to) else it }
        val done = stops.all { it.status == StopStatus.COMPLETED }
        current = current.copy(stops = stops, status = if (done) ShipmentStatus.DELIVERED else ShipmentStatus.IN_TRANSIT)
        return Result.success(current)
    }
    override suspend fun arrive(shipmentId: String, stopId: String) = arrive(shipmentId, stopId, null)
    override suspend fun complete(shipmentId: String, stopId: String) = complete(shipmentId, stopId, null)
    override suspend fun arrive(shipmentId: String, stopId: String, occurredAt: String?) = update("arrive", stopId, occurredAt, StopStatus.ARRIVED)
    override suspend fun complete(shipmentId: String, stopId: String, occurredAt: String?) = update("complete", stopId, occurredAt, StopStatus.COMPLETED)
    override suspend fun list() = Result.success(listOf(current))
    override suspend fun detail(id: String) = Result.success(current)
    override suspend fun drivers() = Result.success(emptyList<DriverView>())
    override suspend fun assignDriver(shipmentId: String, driverId: String, reason: String?) = Result.success(current)
}

private class Catalog : CatalogRepository {
    override suspend fun cities() = Result.success(listOf(CityItem("LIMA", "Lima", -12.0, -77.0), CityItem("JULIACA", "Juliaca", -15.5, -70.1)))
    override suspend fun organization(id: String) = Result.failure<PublicOrganization>(IllegalStateException())
}

/** Evidencia controlable desde el test (estado de cada ítem). */
private class Evidence : EvidenceRepository {
    val flow = MutableStateFlow<List<EvidenceItem>>(emptyList())
    override val items: StateFlow<List<EvidenceItem>> = flow
    override suspend fun load(shipmentId: String) = Result.success(Unit)
    override suspend fun enqueue(shipmentId: String, stopId: String, type: DocumentType, source: ByteArray, mime: String): EvidenceItem {
        val item = EvidenceItem("e${flow.value.size}", shipmentId, stopId, type, EvidenceState.FAILED, offline = true)
        flow.value = flow.value + item
        return item
    }
    override suspend fun retry(id: String) {}
    override suspend fun retryAll(shipmentId: String) {}
    override suspend fun discard(id: String) { flow.value = flow.value.filter { it.id != id } }
    fun uploadAll() { flow.value = flow.value.map { it.copy(state = EvidenceState.UPLOADED, offline = false) } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DriverOfflineViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val fresh = trip(
        ShipmentStatus.PENDING,
        stop("p", 1, StopType.PICKUP, StopStatus.PENDING),
        stop("d", 2, StopType.DELIVERY, StopStatus.PENDING),
    )
    private val atDestination = trip(
        ShipmentStatus.IN_TRANSIT,
        stop("p", 1, StopType.PICKUP, StopStatus.COMPLETED),
        stop("d", 2, StopType.DELIVERY, StopStatus.ARRIVED),
    )

    private fun vm(repo: NetShipments, evidence: Evidence = Evidence(), cache: DriverTripCache = DriverTripCache.inMemory(clock)) =
        DriverTripViewModel(
            repo, Catalog(), evidence, FakeRealtime(), cache = cache,
            queue = DriverActionQueue(repo, TextStore.InMemory(), { evidence.flow.value }, clock = clock),
        )

    @Test fun `sin red muestra el viaje guardado con la hora de los datos`() = runTest {
        val cache = DriverTripCache.inMemory(clock)
        cache.save(fresh)
        val repo = NetShipments(fresh).apply { online = false }
        val vm = vm(repo, cache = cache)
        val s = vm.state.value
        assertEquals(fresh, s.trip)
        assertTrue(s.offline)
        assertEquals("2026-11-18T08:15:00-05:00", s.cachedAt)
        assertNull(s.error)
        assertFalse(s.loading)
        assertEquals(DriverStep.ARRIVE_PICKUP, s.action?.step)
    }

    @Test fun `sin red y sin nada guardado muestra el error de conexion`() = runTest {
        val vm = vm(NetShipments(fresh).apply { online = false })
        assertNull(vm.state.value.trip)
        assertEquals(NO_NETWORK.message, vm.state.value.error)
    }

    @Test fun `con red guarda el viaje y al recuperarla quita el aviso`() = runTest {
        val cache = DriverTripCache.inMemory(clock)
        val repo = NetShipments(fresh)
        val vm = vm(repo, cache = cache)
        assertEquals(fresh, cache.load()?.trip)
        assertFalse(vm.state.value.offline)

        repo.online = false
        vm.refresh()
        assertTrue(vm.state.value.offline)
        assertEquals("2026-11-18T08:15:00-05:00", vm.state.value.cachedAt)
        assertEquals(fresh, vm.state.value.trip, "conserva lo que hay en pantalla")

        repo.online = true
        vm.refresh()
        assertFalse(vm.state.value.offline)
        assertNull(vm.state.value.cachedAt)
    }

    @Test fun `sin red el toque se aplica al instante, queda en cola y sale al recuperar senal`() = runTest {
        val repo = NetShipments(fresh)
        val vm = vm(repo)
        repo.online = false

        vm.advance()
        var s = vm.state.value
        assertEquals(DriverStep.PICKED_UP, s.action?.step, "optimista: ya se ve en el punto de recojo")
        assertEquals(StopStatus.ARRIVED, s.trip?.stops?.first()?.status)
        assertEquals(ShipmentStatus.IN_TRANSIT, s.trip?.status)
        assertEquals(1, s.pendingActions.size)
        assertTrue(s.pendingSync)
        assertEquals(DriverNotice.QUEUED, s.notice)
        assertFalse(s.acting)
        assertTrue(s.offline)
        assertEquals("2026-11-18T08:15:00-05:00", repo.calls.single().second, "manda la hora del dispositivo")

        // Segundo toque, también sin red: FIFO.
        vm.advance()
        assertEquals(DriverStep.ARRIVE_DESTINATION, vm.state.value.action?.step)
        assertEquals(2, vm.state.value.pendingActions.size)

        // Vuelve la señal: el reintento de ~20 s vacía la cola en orden y el servidor manda.
        repo.online = true
        advanceTimeBy(20_001)
        s = vm.state.value
        assertTrue(s.pendingActions.isEmpty())
        assertFalse(s.offline)
        assertEquals(StopStatus.COMPLETED, repo.current.stops.first().status)
        assertEquals(DriverStep.ARRIVE_DESTINATION, s.action?.step)
        assertEquals(listOf("arrive", "arrive", "arrive", "complete"), repo.calls.map { it.first })
    }

    @Test fun `con la foto en cola por falta de senal se puede marcar Entregado y el cierre sale despues de la foto`() = runTest {
        val repo = NetShipments(atDestination)
        val evidence = Evidence()
        val vm = vm(repo, evidence)
        assertFalse(vm.state.value.canAdvance, "sin foto no se entrega (RN06)")

        repo.online = false
        vm.onPhotoCaptured(byteArrayOf(1))
        assertTrue(vm.state.value.hasPhotoAwaitingSignal)
        assertFalse(vm.state.value.photoBlocked)
        assertTrue(vm.state.value.canAdvance)

        vm.advance()
        assertTrue(vm.state.value.delivered, "optimista: entregado en pantalla")
        assertTrue(vm.state.value.pendingSync)
        assertEquals(EvidencePhase.CLOSED, vm.state.value.evidencePhase)
        assertTrue(repo.calls.isEmpty(), "el cierre espera a la foto")

        // Vuelve la red y la foto sube: el cierre sale solo.
        repo.online = true
        evidence.uploadAll()
        assertEquals(listOf("complete"), repo.calls.map { it.first })
        assertTrue(vm.state.value.pendingActions.isEmpty())
        assertEquals(ShipmentStatus.DELIVERED, repo.current.status)
        assertEquals(ShipmentStatus.DELIVERED, vm.state.value.trip?.status)
    }

    @Test fun `la foto fallida por el servidor no habilita la entrega`() = runTest {
        val evidence = Evidence()
        val vm = vm(NetShipments(atDestination), evidence)
        evidence.flow.value = listOf(EvidenceItem("x", "s1", "d", DocumentType.DELIVERY_PHOTO, EvidenceState.FAILED, error = "Archivo muy grande"))
        assertTrue(vm.state.value.photoBlocked)
        assertFalse(vm.state.value.canAdvance)
        // Sin trabajo pendiente el bucle de reintento de ~20 s termina (si no, runTest avanzaría el tiempo virtual sin fin).
        evidence.flow.value = emptyList()
    }

    @Test fun `una accion rechazada al reintentar se descarta, recarga y explica`() = runTest {
        val repo = NetShipments(fresh)
        // Mientras el conductor estaba sin señal la oficina abrió una incidencia: al volver, el servidor rechaza con TRK-005.
        val withIncident = object : ShipmentsRepository by repo {
            override suspend fun arrive(shipmentId: String, stopId: String, occurredAt: String?): Result<ShipmentView> =
                if (!repo.online) Result.failure(NO_NETWORK) else Result.failure(ApiException(409, "LOGYX-TRK-005", "Incidencia activa"))
        }
        val vm = DriverTripViewModel(
            withIncident, Catalog(), Evidence(), FakeRealtime(),
            queue = DriverActionQueue(withIncident, TextStore.InMemory(), clock = clock),
        )
        repo.online = false
        vm.advance()
        assertEquals(1, vm.state.value.pendingActions.size)
        assertEquals(DriverStep.PICKED_UP, vm.state.value.action?.step)

        repo.online = true
        vm.refresh()
        assertTrue(vm.state.value.pendingActions.isEmpty())
        assertTrue(vm.state.value.incidentRejected)
        assertFalse(vm.state.value.canAdvance)
        assertEquals(DriverStep.ARRIVE_PICKUP, vm.state.value.action?.step, "vuelve a lo que dice el servidor")
    }

    @Test fun `un error definitivo al tocar muestra el mensaje del servidor y no queda en cola`() = runTest {
        val repo = NetShipments(fresh)
        val rejecting = object : ShipmentsRepository by repo {
            override suspend fun arrive(shipmentId: String, stopId: String, occurredAt: String?): Result<ShipmentView> =
                Result.failure(ApiException(409, "LOGYX-TRK-001", "Debes completar la parada anterior"))
        }
        val vm = DriverTripViewModel(rejecting, Catalog(), Evidence(), FakeRealtime(), queue = DriverActionQueue(rejecting, TextStore.InMemory(), clock = clock))
        vm.advance()
        assertEquals("Debes completar la parada anterior", vm.state.value.actionError)
        assertTrue(vm.state.value.pendingActions.isEmpty())
        assertNull(vm.state.value.notice)
        assertEquals(DriverStep.ARRIVE_PICKUP, vm.state.value.action?.step)
    }
}
