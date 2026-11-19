package pe.logyx.feature.driver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.Backoff
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.DriverActionQueue
import pe.logyx.core.data.EvidenceItem
import pe.logyx.core.data.EvidenceRepository
import pe.logyx.core.data.EvidenceState
import pe.logyx.core.data.IncidentsRepository
import pe.logyx.core.data.OperationKind
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.data.SyncItemKind
import pe.logyx.core.data.SyncItemState
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
import kotlin.time.Duration.Companion.seconds

private val clock = Clock.fixed(Instant.parse("2026-11-19T14:00:00Z"), ZoneOffset.ofHours(-5))
private val OFFLINE = ApiException(0, "NETWORK", "No pudimos conectar con OnCargo. Revisa tu conexión e inténtalo de nuevo.")

private fun stop(id: String, seq: Int, type: StopType, status: StopStatus) =
    StopView(id, seq, type, if (type == StopType.PICKUP) "LIMA" else "JULIACA", null, status)

/** Backend con interruptor de red que registra el cuerpo de cada cierre. */
private class Backend(var current: ShipmentView) : ShipmentsRepository {
    var online = true
    var failComplete: ApiException? = null
    val calls = mutableListOf<String>()
    val events = mutableListOf<StopEventRequest>()
    override suspend fun driverShipments() = Result.success(listOf(current))
    override suspend fun driverActive(): Result<ShipmentView?> = if (online) Result.success(current) else Result.failure(OFFLINE)
    override suspend fun driverHistory(): Result<List<ShipmentView>> = Result.success(emptyList())
    private fun move(stopId: String, to: StopStatus): Result<ShipmentView> {
        if (!online) return Result.failure(OFFLINE)
        val stops = current.stops.map { if (it.id == stopId) it.copy(status = to) else it }
        current = current.copy(stops = stops, status = if (stops.all { it.status == StopStatus.COMPLETED }) ShipmentStatus.DELIVERED else ShipmentStatus.IN_TRANSIT)
        return Result.success(current)
    }
    override suspend fun arrive(shipmentId: String, stopId: String) = arrive(shipmentId, stopId, null)
    override suspend fun complete(shipmentId: String, stopId: String) = complete(shipmentId, stopId, null)
    override suspend fun arrive(shipmentId: String, stopId: String, occurredAt: String?): Result<ShipmentView> { calls += "arrive:$stopId"; return move(stopId, StopStatus.ARRIVED) }
    override suspend fun completeStop(shipmentId: String, stopId: String, event: StopEventRequest): Result<ShipmentView> {
        calls += "complete:$stopId"
        events += event
        failComplete?.let { failComplete = null; return Result.failure(it) }
        return move(stopId, StopStatus.COMPLETED)
    }
    override suspend fun list() = Result.success(listOf(current))
    override suspend fun detail(id: String) = Result.success(current)
    override suspend fun drivers() = Result.success(emptyList<DriverView>())
    override suspend fun assignDriver(shipmentId: String, driverId: String, reason: String?) = Result.success(current)
}

private class Cities : CatalogRepository {
    override suspend fun cities() = Result.success(listOf(CityItem("LIMA", "Lima", -12.0, -77.0), CityItem("JULIACA", "Juliaca", -15.5, -70.1)))
    override suspend fun organization(id: String) = Result.failure<PublicOrganization>(IllegalStateException())
}

/** Evidencia con interruptor de red: sin red la captura queda `FAILED` por falta de señal; [retry] sube si hay red. */
private class NetEvidence(var online: Boolean = true) : EvidenceRepository {
    val flow = MutableStateFlow<List<EvidenceItem>>(emptyList())
    override val items: StateFlow<List<EvidenceItem>> = flow
    var retries = 0
    private fun state() = if (online) EvidenceState.UPLOADED else EvidenceState.FAILED
    override suspend fun load(shipmentId: String) = Result.success(Unit)
    override suspend fun enqueue(shipmentId: String, stopId: String, type: DocumentType, source: ByteArray, mime: String): EvidenceItem {
        val item = EvidenceItem("e${flow.value.size}", shipmentId, stopId, type, state(), offline = !online, error = if (online) null else "Sin señal")
        flow.value = flow.value + item
        return item
    }
    override suspend fun retry(id: String) {
        retries++
        flow.value = flow.value.map { if (it.id == id) it.copy(state = state(), offline = !online) else it }
    }
    override suspend fun retryAll(shipmentId: String) {}
    override suspend fun discard(id: String) { flow.value = flow.value.filter { it.id != id } }
}

private class FakeIncidents(var online: Boolean = true) : IncidentsRepository {
    val sent = mutableListOf<Pair<ReportIncidentRequest, String?>>()
    override suspend fun report(shipmentId: String, category: IncidentCategory, description: String) = Result.failure<IncidentView>(IllegalStateException())
    override suspend fun report(shipmentId: String, request: ReportIncidentRequest, idempotencyKey: String?): Result<IncidentView> {
        sent += request to idempotencyKey
        return if (online) Result.success(IncidentView("i1", shipmentId, category = request.category, description = request.description, status = IncidentStatus.OPEN))
        else Result.failure(OFFLINE)
    }
    override suspend fun forShipment(shipmentId: String) = Result.success(emptyList<IncidentView>())
    override suspend fun detail(id: String) = Result.failure<IncidentView>(IllegalStateException())
    override suspend fun comment(id: String, body: String) = Result.failure<IncidentView>(IllegalStateException())
    override suspend fun mine(active: Boolean?, page: Int) = Result.failure<Page<IncidentView>>(IllegalStateException())
}

/**
 * Ola 2, zona X (2.5/2.7): evidencia del recojo, quién recibe, emergencia sin red e indicador de la cola. Los bucles de la
 * cola corren en `backgroundScope` (se cancelan solos); nada de `advanceUntilIdle()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriverWave2ViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val atPickup = ShipmentView(
        "s1", originCity = "LIMA", destinationCity = "JULIACA", status = ShipmentStatus.IN_TRANSIT,
        stops = listOf(stop("p", 1, StopType.PICKUP, StopStatus.ARRIVED), stop("d", 2, StopType.DELIVERY, StopStatus.PENDING)),
    )
    private val atDestination = atPickup.copy(stops = listOf(stop("p", 1, StopType.PICKUP, StopStatus.COMPLETED), stop("d", 2, StopType.DELIVERY, StopStatus.ARRIVED)))

    private fun vm(
        backend: Backend,
        evidence: NetEvidence = NetEvidence(),
        incidents: FakeIncidents = FakeIncidents(),
        scope: CoroutineScope? = null,
    ): DriverTripViewModel {
        var n = 0
        val queue = DriverActionQueue(
            backend, TextStore.InMemory(), { evidence.flow.value }, clock = clock, newId = { "k-${++n}" },
            incidents = incidents, uploader = evidence, backoff = Backoff(random = { 0.0 }), scope = scope,
        )
        return DriverTripViewModel(backend, Cities(), evidence, FakeRealtime(), queue = queue, clock = clock)
    }

    // ── Recojo ──

    @Test(timeout = 60_000) fun `Carga recogida espera la foto y el peso que pidio la PYME`() = runTest {
        val backend = Backend(atPickup.copy(pickupPhotoRequired = true, pickupWeightRequired = true))
        val v = vm(backend)
        assertEquals(DriverStep.PICKED_UP, v.state.value.action?.step)
        assertTrue(v.state.value.pickupStep)
        assertTrue(v.state.value.pickupBlocked)
        assertFalse(v.state.value.canAdvance)

        v.onPhotoCaptured(byteArrayOf(1))
        assertEquals(DocumentType.PICKUP_PHOTO, v.state.value.evidence.single().type, "en el recojo la foto es de la carga")
        assertFalse(v.state.value.canAdvance, "falta el peso")
        v.onWeight("0")
        assertFalse(v.state.value.weightValid)
        v.onWeight("2,450.5")
        assertTrue(v.state.value.canAdvance)

        v.advance()
        assertEquals(listOf("complete:p"), backend.calls)
        assertEquals(2450.5, backend.events.single().weightKg)
        assertEquals(DriverStep.ARRIVE_DESTINATION, v.state.value.action?.step)
        assertEquals(DriverNotice.STOP_UPDATED, v.state.value.notice)
    }

    @Test(timeout = 60_000) fun `sin requisitos el recojo se cierra sin foto ni peso y un peso mal escrito bloquea`() = runTest {
        val v = vm(Backend(atPickup))
        assertTrue(v.state.value.canAdvance)
        v.onWeight("70000")
        assertFalse(v.state.value.canAdvance, "si lo escribe, debe estar entre 1 y 60 000 kg")
        v.onWeight("")
        assertTrue(v.state.value.canAdvance)
    }

    @Test(timeout = 60_000) fun `una foto de la carga en cola por falta de senal cuenta y el cierre sale despues de ella`() = runTest(timeout = 60.seconds) {
        val backend = Backend(atPickup.copy(pickupPhotoRequired = true))
        val evidence = NetEvidence(online = false)
        val v = vm(backend, evidence, scope = backgroundScope)
        backend.online = false
        v.onPhotoCaptured(byteArrayOf(1))
        assertTrue(v.state.value.hasPickupPhoto, "tomada y en cola cuenta, como en la entrega")
        assertTrue(v.state.value.canAdvance)
        v.advance()
        assertEquals(DriverNotice.QUEUED, v.state.value.notice)
        assertTrue(backend.calls.isEmpty(), "el cierre espera a la foto")
        assertEquals(2, v.state.value.syncCount, "1 foto + 1 cierre")
        assertEquals(SyncItemState.WAITING_SIGNAL, v.state.value.syncState)

        // Vuelve la señal: el reintento con espera exponencial sube la foto y luego cierra el recojo.
        backend.online = true
        evidence.online = true
        advanceTimeBy(20_001); runCurrent()
        assertEquals(listOf("complete:p"), backend.calls)
        assertEquals(EvidenceState.UPLOADED, evidence.flow.value.single().state)
        assertEquals(0, v.state.value.syncCount)
    }

    @Test(timeout = 60_000) fun `TRK-010 del servidor se muestra bajo el peso`() = runTest {
        val backend = Backend(atPickup)
        backend.failComplete = ApiException(400, "LOGYX-TRK-010", "Indica el peso medido en el recojo: la PYME lo pidió", "weightKg")
        val v = vm(backend)
        v.advance()
        assertEquals("Indica el peso medido en el recojo: la PYME lo pidió", (v.state.value.form.weightError as? pe.logyx.core.designsystem.components.UiText.Raw)?.value)
        assertEquals(DriverStep.PICKED_UP, v.state.value.action?.step, "vuelve a lo que dice el servidor")
        assertNull(v.state.value.notice)
    }

    // ── Entrega ──

    @Test(timeout = 60_000) fun `Entregado pide nombre y DNI o CE de quien recibe`() = runTest {
        val backend = Backend(atDestination)
        val v = vm(backend)
        v.onPhotoCaptured(byteArrayOf(1))
        assertTrue(v.state.value.receiverBlocked)
        assertFalse(v.state.value.canAdvance)
        v.onReceiverName("Ana Torres")
        v.onReceiverDoc("1234")
        assertFalse(v.state.value.canAdvance, "DNI incompleto")
        v.onReceiverDoc("456.789-12")
        assertTrue(v.state.value.canAdvance)
        v.advance()
        assertTrue(v.state.value.delivered)
        val event = backend.events.single()
        assertEquals("Ana Torres", event.receivedByName)
        assertEquals("45678912", event.receivedByDoc, "normalizado como el backend")
        assertEquals("2026-11-19T09:00:00-05:00", event.occurredAt)
    }

    @Test(timeout = 60_000) fun `el formulario se reinicia al cambiar de parada`() = runTest {
        val v = vm(Backend(atPickup))
        v.onWeight("1200")
        v.advance()
        assertEquals("", v.state.value.form.weight)
        assertEquals("d", v.state.value.form.stopId)
    }

    // ── Emergencia ──

    @Test(timeout = 60_000) fun `emergencia sin red queda en la cola con ubicacion y sale despues con la misma clave`() = runTest {
        val backend = Backend(atDestination)
        val incidents = FakeIncidents(online = false)
        val v = vm(backend, incidents = incidents)
        backend.online = false
        v.reportEmergency(EmergencyKind.ACCIDENT, "", locate = { DeviceLocation(-15.49, -70.13) })
        assertEquals(DriverNotice.EMERGENCY_QUEUED, v.state.value.notice)
        assertFalse(v.state.value.emergencySending)
        val op = v.state.value.pendingActions.single { it.op == OperationKind.INCIDENT }
        val body = assertNotNull(op.incident)
        assertEquals(IncidentCategory.ACCIDENT, body.category)
        assertEquals(IncidentSeverity.CRITICAL, body.severity)
        assertEquals(-15.49, body.latitude)
        assertEquals("2026-11-19T09:00:00-05:00", body.occurredAt)
        assertTrue(body.description.isNotBlank(), "sin texto se manda una descripción por omisión")
        assertEquals(SyncItemKind.INCIDENT, v.state.value.syncList.single().kind)

        // «Reintentar ahora» con señal: sale con la misma Idempotency-Key.
        backend.online = true
        incidents.online = true
        v.retrySyncNow()
        assertEquals(2, incidents.sent.size)
        assertEquals(op.idempotencyKey, incidents.sent[0].second)
        assertEquals(op.idempotencyKey, incidents.sent[1].second)
        assertTrue(v.state.value.pendingActions.isEmpty())
        assertEquals(DriverNotice.SYNCED, v.state.value.notice)
    }

    @Test(timeout = 60_000) fun `sin permiso de ubicacion la emergencia sale sin coordenadas y la averia usa la severidad del servidor`() = runTest {
        val incidents = FakeIncidents()
        val v = vm(Backend(atDestination), incidents = incidents)
        v.reportEmergency(EmergencyKind.BREAKDOWN, "  Se rompió la caja  ")
        assertEquals(DriverNotice.EMERGENCY_SENT, v.state.value.notice)
        val (body, key) = incidents.sent.single()
        assertNull(body.latitude)
        assertNull(body.severity)
        assertEquals(IncidentCategory.BREAKDOWN, body.category)
        assertEquals("Se rompió la caja", body.description)
        assertNotNull(key)
    }

    // ── Cola visible ──

    @Test(timeout = 60_000) fun `el indicador cuenta las acciones pendientes de este usuario`() = runTest {
        val backend = Backend(atPickup)
        val v = vm(backend, NetEvidence(online = false))
        backend.online = false
        assertEquals(0, v.state.value.syncCount)
        v.onPhotoCaptured(byteArrayOf(1), DocumentType.REMISSION_GUIDE)
        v.advance()
        v.advance()
        assertEquals(3, v.state.value.syncCount, "guía + cierre del recojo + llegada a destino")
        assertEquals(listOf(SyncItemKind.EVIDENCE, SyncItemKind.COMPLETE, SyncItemKind.ARRIVE), v.state.value.syncList.map { it.kind })
        assertEquals(SyncItemState.WAITING_SIGNAL, v.state.value.syncState)
        assertNotNull(v.state.value.sync.nextAttemptAt)
    }
}
