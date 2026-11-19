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
import pe.logyx.core.data.AdjustmentsRepository
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.DriverActionQueue
import pe.logyx.core.data.EvidenceItem
import pe.logyx.core.data.EvidenceRepository
import pe.logyx.core.data.OperationKind
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.data.SyncItemKind
import pe.logyx.core.data.TextStore
import pe.logyx.core.designsystem.components.UiText
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

private val adjClock = Clock.fixed(Instant.parse("2026-11-19T14:00:00Z"), ZoneOffset.ofHours(-5))
private val adjOffline = ApiException(0, "NETWORK", "No pudimos conectar con OnCargo. Revisa tu conexión e inténtalo de nuevo.")

private class AdjBackend(var current: ShipmentView) : ShipmentsRepository {
    var online = true
    override suspend fun driverShipments() = Result.success(listOf(current))
    override suspend fun driverActive(): Result<ShipmentView?> = if (online) Result.success(current) else Result.failure(adjOffline)
    override suspend fun driverHistory(): Result<List<ShipmentView>> = Result.success(emptyList())
    override suspend fun arrive(shipmentId: String, stopId: String) = Result.success(current)
    override suspend fun complete(shipmentId: String, stopId: String) = Result.success(current)
    override suspend fun list() = Result.success(listOf(current))
    override suspend fun detail(id: String) = Result.success(current)
    override suspend fun drivers() = Result.success(emptyList<DriverView>())
    override suspend fun assignDriver(shipmentId: String, driverId: String, reason: String?) = Result.success(current)
}

private class AdjCities : CatalogRepository {
    override suspend fun cities() = Result.success(listOf(CityItem("LIMA", "Lima", -12.0, -77.0), CityItem("JULIACA", "Juliaca", -15.5, -70.1)))
    override suspend fun organization(id: String) = Result.failure<PublicOrganization>(IllegalStateException())
}

private class AdjEvidence : EvidenceRepository {
    private val flow = MutableStateFlow<List<EvidenceItem>>(emptyList())
    override val items: StateFlow<List<EvidenceItem>> = flow
    override suspend fun load(shipmentId: String) = Result.success(Unit)
    override suspend fun enqueue(shipmentId: String, stopId: String, type: DocumentType, source: ByteArray, mime: String) =
        EvidenceItem("e", shipmentId, stopId, type, pe.logyx.core.data.EvidenceState.UPLOADED)
    override suspend fun retry(id: String) {}
    override suspend fun retryAll(shipmentId: String) {}
    override suspend fun discard(id: String) {}
}

private class FakeAdjustments : AdjustmentsRepository by AdjustmentsRepository.NONE {
    var online = true
    var failWith: ApiException? = null
    val sent = mutableListOf<Pair<ProposeAdjustmentRequest, String?>>()
    override suspend fun propose(shipmentId: String, request: ProposeAdjustmentRequest, idempotencyKey: String?): Result<AdjustmentView> {
        sent += request to idempotencyKey
        failWith?.let { return Result.failure(it) }
        return if (online) Result.success(AdjustmentView("a1", shipmentId, type = request.type, amount = request.amount, status = AdjustmentStatus.PENDING))
        else Result.failure(adjOffline)
    }
}

/** Ola 2 (2.6): el conductor propone un ajuste de precio; validación, cola y avisos. */
@OptIn(ExperimentalCoroutinesApi::class)
class DriverAdjustmentViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun stop(id: String, seq: Int, type: StopType, status: StopStatus) =
        StopView(id, seq, type, if (type == StopType.PICKUP) "LIMA" else "JULIACA", null, status)

    private val atDestination = ShipmentView(
        "s1", originCity = "LIMA", destinationCity = "JULIACA", status = ShipmentStatus.IN_TRANSIT, agreedPrice = 1000.0,
        stops = listOf(stop("p", 1, StopType.PICKUP, StopStatus.COMPLETED), stop("d", 2, StopType.DELIVERY, StopStatus.ARRIVED)),
    )

    private fun vm(backend: AdjBackend, adjustments: FakeAdjustments): DriverTripViewModel {
        var n = 0
        val evidence = AdjEvidence()
        val queue = DriverActionQueue(
            backend, TextStore.InMemory(), { evidence.items.value }, clock = adjClock, newId = { "k-${++n}" },
            adjustments = adjustments, uploader = evidence,
        )
        return DriverTripViewModel(backend, AdjCities(), evidence, FakeRealtime(), queue = queue, clock = adjClock)
    }

    private fun DriverTripViewModel.fill(type: AdjustmentType?, amount: String, note: String = "") {
        openAdjustment()
        type?.let(::onAdjustmentType)
        onAdjustmentAmount(amount)
        onAdjustmentNote(note)
    }

    @Test(timeout = 60_000) fun `la hoja solo se abre con un viaje en curso`() = runTest {
        val delivered = atDestination.copy(status = ShipmentStatus.DELIVERED, stops = atDestination.stops.map { it.copy(status = StopStatus.COMPLETED) })
        val done = vm(AdjBackend(delivered), FakeAdjustments())
        done.openAdjustment()
        assertNull(done.state.value.adjustment)

        val v = vm(AdjBackend(atDestination), FakeAdjustments())
        v.openAdjustment()
        assertNotNull(v.state.value.adjustment)
        v.closeAdjustment()
        assertNull(v.state.value.adjustment)
    }

    @Test(timeout = 60_000) fun `monto vacio, cero o mayor al precio acordado no se envian`() = runTest {
        val adj = FakeAdjustments()
        val v = vm(AdjBackend(atDestination), adj)

        v.fill(AdjustmentType.TOLL, "")
        v.requestAdjustment()
        assertNotNull(v.state.value.adjustment?.amountError)
        assertFalse(v.state.value.adjustmentConfirming)

        v.onAdjustmentAmount("0")
        v.requestAdjustment()
        assertNotNull(v.state.value.adjustment?.amountError)
        assertFalse(v.state.value.adjustmentConfirming)

        v.onAdjustmentAmount("1000.01")
        v.requestAdjustment()
        val tooHigh = v.state.value.adjustment?.amountError as UiText.Res
        assertEquals(R.string.driver_adjustment_amount_too_high, tooHigh.id)
        assertFalse(v.state.value.adjustmentConfirming)
        v.confirmAdjustment()
        assertTrue(adj.sent.isEmpty())

        v.onAdjustmentAmount("1000")
        assertNull(v.state.value.adjustment?.amountError, "editar limpia el error")
        v.requestAdjustment()
        assertTrue(v.state.value.adjustmentConfirming, "el tope es el precio acordado, inclusive")
    }

    @Test(timeout = 60_000) fun `sin precio acordado en el envio solo se exige que sea mayor a cero`() = runTest {
        val v = vm(AdjBackend(atDestination.copy(agreedPrice = null)), FakeAdjustments())
        v.fill(AdjustmentType.TOLL, "99999")
        v.requestAdjustment()
        assertTrue(v.state.value.adjustmentConfirming)
    }

    @Test(timeout = 60_000) fun `sin tipo no se envia y Otro exige nota`() = runTest {
        val v = vm(AdjBackend(atDestination), FakeAdjustments())
        v.fill(null, "50")
        v.requestAdjustment()
        assertNotNull(v.state.value.adjustment?.typeError)
        assertFalse(v.state.value.adjustmentConfirming)

        v.onAdjustmentType(AdjustmentType.OTHER)
        v.onAdjustmentNote("   ")
        v.requestAdjustment()
        assertNotNull(v.state.value.adjustment?.noteError)
        assertNull(v.state.value.adjustment?.typeError)
        assertFalse(v.state.value.adjustmentConfirming)

        v.onAdjustmentNote("Cargué a mano 20 sacos")
        assertNull(v.state.value.adjustment?.noteError)
        v.requestAdjustment()
        assertTrue(v.state.value.adjustmentConfirming)
        v.cancelAdjustmentConfirm()
        assertFalse(v.state.value.adjustmentConfirming)
        assertNotNull(v.state.value.adjustment, "Volver deja la hoja abierta")
    }

    @Test(timeout = 60_000) fun `estadia y diferencia de peso proponen la parada actual por defecto`() = runTest {
        val v = vm(AdjBackend(atDestination), FakeAdjustments())
        v.openAdjustment()
        v.onAdjustmentType(AdjustmentType.DETENTION)
        assertEquals("d", v.state.value.adjustment?.stopId)
        v.onAdjustmentStop(null)
        v.onAdjustmentType(AdjustmentType.WEIGHT_DIFF)
        assertEquals("d", v.state.value.adjustment?.stopId)
        v.onAdjustmentStop("p")
        v.onAdjustmentType(AdjustmentType.TOLL)
        assertEquals("p", v.state.value.adjustment?.stopId, "no pisa la parada elegida")

        val other = vm(AdjBackend(atDestination), FakeAdjustments())
        other.openAdjustment()
        other.onAdjustmentType(AdjustmentType.TOLL)
        assertNull(other.state.value.adjustment?.stopId, "peajes no llevan parada por defecto")
    }

    @Test(timeout = 60_000) fun `la nota se limita a 300 caracteres`() = runTest {
        val v = vm(AdjBackend(atDestination), FakeAdjustments())
        v.fill(AdjustmentType.HANDLING, "10", "x".repeat(400))
        assertEquals(MAX_ADJUSTMENT_NOTE, v.state.value.adjustment?.note?.length)
    }

    @Test(timeout = 60_000) fun `confirmar encola y envia con tipo, monto, nota y parada y avisa Ajuste enviado`() = runTest {
        val adj = FakeAdjustments()
        val v = vm(AdjBackend(atDestination), adj)
        v.fill(AdjustmentType.DETENTION, "120.5", "  Esperé 3 horas en el almacén  ")
        v.requestAdjustment()
        assertTrue(v.state.value.adjustmentConfirming)
        v.confirmAdjustment()

        val (request, key) = adj.sent.single()
        assertEquals(AdjustmentType.DETENTION, request.type)
        assertEquals(120.5, request.amount)
        assertEquals("Esperé 3 horas en el almacén", request.note)
        assertEquals("d", request.stopId)
        assertNotNull(key)
        assertEquals(DriverNotice.ADJUSTMENT_SENT, v.state.value.notice)
        assertNull(v.state.value.adjustment, "la hoja se cierra")
        assertFalse(v.state.value.adjustmentSending)
        assertTrue(v.state.value.pendingActions.none { it.op == OperationKind.ADJUSTMENT })
    }

    @Test(timeout = 60_000) fun `nota en blanco se manda como null`() = runTest {
        val adj = FakeAdjustments()
        val v = vm(AdjBackend(atDestination), adj)
        v.fill(AdjustmentType.TOLL, "15", "   ")
        v.confirmAdjustment()
        assertNull(adj.sent.single().first.note)
        assertNull(adj.sent.single().first.stopId)
    }

    @Test(timeout = 60_000) fun `sin senal queda en la cola con su clave y sale despues sin duplicarse`() = runTest {
        val backend = AdjBackend(atDestination)
        val adj = FakeAdjustments().also { it.online = false }
        val v = vm(backend, adj)
        backend.online = false
        v.fill(AdjustmentType.TOLL, "35")
        v.confirmAdjustment()

        assertEquals(DriverNotice.ADJUSTMENT_QUEUED, v.state.value.notice)
        val op = v.state.value.pendingActions.single { it.op == OperationKind.ADJUSTMENT }
        assertEquals(35.0, op.adjustment?.amount)
        assertEquals(SyncItemKind.ADJUSTMENT, v.state.value.syncList.single().kind)

        backend.online = true
        adj.online = true
        v.retrySyncNow()
        assertEquals(2, adj.sent.size)
        assertEquals(op.idempotencyKey, adj.sent[0].second)
        assertEquals(op.idempotencyKey, adj.sent[1].second, "mismo Idempotency-Key en el reintento")
        assertTrue(v.state.value.pendingActions.isEmpty())
        assertEquals(DriverNotice.SYNCED, v.state.value.notice)
    }

    @Test(timeout = 60_000) fun `un rechazo definitivo ADJ se muestra como mensaje y no como aviso de exito`() = runTest {
        val adj = FakeAdjustments()
        adj.failWith = ApiException(400, "LOGYX-ADJ-005", "Ya hay 10 ajustes en este envío.")
        val v = vm(AdjBackend(atDestination), adj)
        v.fill(AdjustmentType.TOLL, "35")
        v.confirmAdjustment()

        assertEquals("Ya hay 10 ajustes en este envío.", v.state.value.adjustmentError)
        assertNull(v.state.value.notice)
        assertNull(v.state.value.actionError, "no se mezcla con el error de avanzar el viaje")
        assertTrue(v.state.value.pendingActions.isEmpty(), "se descartó de la cola")
        v.dismissAdjustmentError()
        assertNull(v.state.value.adjustmentError)
    }
}
