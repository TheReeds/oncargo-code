package pe.logyx.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pe.logyx.core.model.AdjustmentType
import pe.logyx.core.model.AdjustmentView
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.DriverView
import pe.logyx.core.model.IncidentCategory
import pe.logyx.core.model.IncidentSeverity
import pe.logyx.core.model.IncidentStatus
import pe.logyx.core.model.IncidentView
import pe.logyx.core.model.Page
import pe.logyx.core.model.ProposeAdjustmentRequest
import pe.logyx.core.model.ReportIncidentRequest
import pe.logyx.core.model.ShipmentAdjustmentsView
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.StopEventRequest
import pe.logyx.core.model.StopStatus
import pe.logyx.core.model.StopType
import pe.logyx.core.model.StopView
import pe.logyx.core.network.ApiException
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Cola offline completa (Ola 2, roadmap 2.5): orden FIFO por envío, evidencia antes del cierre (entrega y recojo), espera
 * exponencial con tope, idempotencia del reporte de emergencia, descarte en errores definitivos y conteo visible.
 * Ojo: los bucles de reintento viven en `backgroundScope` (se cancelan al terminar) y nunca se usa `advanceUntilIdle()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineQueueTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-18T13:15:00Z"), ZoneOffset.ofHours(-5))
    private val offline = ApiException(0, "NETWORK", "Sin red")
    private fun dir() = File.createTempFile("queue", "").let { it.delete(); it.mkdirs(); it }

    private fun stop(id: String, seq: Int, type: StopType) = StopView(id, seq, type, if (type == StopType.PICKUP) "LIMA" else "JULIACA", null, StopStatus.PENDING)
    private val trip = ShipmentView("s1", originCity = "LIMA", destinationCity = "JULIACA", status = ShipmentStatus.IN_TRANSIT,
        stops = listOf(stop("p", 1, StopType.PICKUP), stop("d", 2, StopType.DELIVERY)))

    /** Registro común de llamadas en orden: `arrive:s1:p`, `complete:s1:d`, `upload:e1`, `incident:s1`. */
    private val log = mutableListOf<String>()

    private inner class Repo(var online: Boolean = true, var failComplete: ApiException? = null) : ShipmentsRepository {
        val events = mutableListOf<StopEventRequest>()
        override suspend fun list() = Result.success(emptyList<ShipmentView>())
        override suspend fun detail(id: String) = Result.success(trip)
        override suspend fun drivers() = Result.success(emptyList<DriverView>())
        override suspend fun assignDriver(shipmentId: String, driverId: String, reason: String?) = Result.success(trip)
        override suspend fun driverShipments() = Result.success(emptyList<ShipmentView>())
        override suspend fun arrive(shipmentId: String, stopId: String) = arrive(shipmentId, stopId, null)
        override suspend fun complete(shipmentId: String, stopId: String) = complete(shipmentId, stopId, null)
        override suspend fun arrive(shipmentId: String, stopId: String, occurredAt: String?): Result<ShipmentView> {
            log += "arrive:$shipmentId:$stopId"
            return if (online) Result.success(trip.copy(id = shipmentId)) else Result.failure(offline)
        }
        override suspend fun completeStop(shipmentId: String, stopId: String, event: StopEventRequest): Result<ShipmentView> {
            log += "complete:$shipmentId:$stopId"
            events += event
            failComplete?.let { return Result.failure(it) }
            return if (online) Result.success(trip.copy(id = shipmentId)) else Result.failure(offline)
        }
    }

    private inner class Incidents(var online: Boolean = true, var reject: ApiException? = null) : IncidentsRepository {
        val keys = mutableListOf<String?>()
        val bodies = mutableListOf<ReportIncidentRequest>()
        override suspend fun report(shipmentId: String, category: IncidentCategory, description: String) = Result.failure<IncidentView>(IllegalStateException())
        override suspend fun report(shipmentId: String, request: ReportIncidentRequest, idempotencyKey: String?): Result<IncidentView> {
            log += "incident:$shipmentId"
            keys += idempotencyKey
            bodies += request
            reject?.let { return Result.failure(it) }
            return if (online) Result.success(IncidentView("i1", shipmentId, category = request.category, description = request.description, status = IncidentStatus.OPEN))
            else Result.failure(offline)
        }
        override suspend fun forShipment(shipmentId: String) = Result.success(emptyList<IncidentView>())
        override suspend fun detail(id: String) = Result.failure<IncidentView>(IllegalStateException())
        override suspend fun comment(id: String, body: String) = Result.failure<IncidentView>(IllegalStateException())
        override suspend fun mine(active: Boolean?, page: Int) = Result.failure<Page<IncidentView>>(IllegalStateException())
    }

    /** Evidencia local: [retry] sube si hay red. */
    private inner class Evidence(initial: List<EvidenceItem> = emptyList(), var online: Boolean = true, var rejectByServer: Boolean = false) : EvidenceRepository {
        val flow = MutableStateFlow(initial)
        override val items: StateFlow<List<EvidenceItem>> = flow
        override suspend fun load(shipmentId: String) = Result.success(Unit)
        override suspend fun enqueue(shipmentId: String, stopId: String, type: DocumentType, source: ByteArray, mime: String) = error("no se usa")
        override suspend fun retry(id: String) {
            log += "upload:$id"
            flow.value = flow.value.map {
                when {
                    it.id != id -> it
                    rejectByServer -> it.copy(state = EvidenceState.FAILED, offline = false, error = "Archivo inválido")
                    online -> it.copy(state = EvidenceState.UPLOADED, offline = false)
                    else -> it.copy(state = EvidenceState.FAILED, offline = true, error = "Sin red")
                }
            }
        }
        override suspend fun retryAll(shipmentId: String) {}
        override suspend fun discard(id: String) { flow.value = flow.value.filter { it.id != id } }
    }

    private var ids = 0
    private fun queue(
        repo: ShipmentsRepository,
        evidence: Evidence? = null,
        incidents: IncidentsRepository? = null,
        store: TextStore = TextStore.InMemory(),
        backoff: Backoff = Backoff(random = { 0.0 }),
        scope: kotlinx.coroutines.CoroutineScope? = null,
        adjustments: AdjustmentsRepository? = null,
    ) = DriverActionQueue(
        repo, store, evidence = { evidence?.flow?.value.orEmpty() }, clock = clock, newId = { "id-${++ids}" },
        incidents = incidents, adjustments = adjustments, uploader = evidence, backoff = backoff, scope = scope,
    )

    private fun photo(id: String, stop: String, type: DocumentType, state: EvidenceState = EvidenceState.FAILED, offline: Boolean = true, shipment: String = "s1") =
        EvidenceItem(id, shipment, stop, type, state, offline = offline)

    // ── Orden ──

    @Test(timeout = 60_000) fun `FIFO por envio, un envio detenido no frena a otro`() = runBlocking<Unit> {
        val repo = Repo()
        // s1: la entrega espera una foto que falló por el servidor (no hay otra subida) → s1 se detiene; s2 sigue.
        val ev = Evidence(listOf(photo("e1", "d", DocumentType.DELIVERY_PHOTO, offline = false)), rejectByServer = true)
        val q = queue(repo, ev)
        q.enqueue("s1", "p", StopActionOp.ARRIVE)
        q.enqueue("s1", "d", StopActionOp.COMPLETE, needsPhoto = true)
        q.enqueue("s2", "x", StopActionOp.ARRIVE)
        q.enqueue("s1", "d2", StopActionOp.ARRIVE) // detrás del cierre detenido (RN08): no sale
        val r = q.flush()
        assertEquals(QueueStall.WAITING_PHOTO, r.stall)
        assertEquals(listOf("upload:e1", "arrive:s1:p", "arrive:s2:x"), log, "la foto se intenta una sola vez por vuelta")
        assertEquals(listOf("d", "d2"), q.pending.value.map { it.stopId })
    }

    @Test(timeout = 60_000) fun `entrega - la llegada, luego la foto y despues el cierre con quien recibe`() = runBlocking<Unit> {
        val repo = Repo()
        val ev = Evidence(listOf(photo("e1", "d", DocumentType.DELIVERY_PHOTO), photo("f1", "d", DocumentType.SIGNATURE)))
        val q = queue(repo, ev)
        q.enqueue("s1", "d", StopActionOp.ARRIVE)
        q.enqueue("s1", "d", StopActionOp.COMPLETE, needsPhoto = true, receivedByName = "Ana Torres", receivedByDoc = "45678912")
        val r = q.flush()
        assertNull(r.stall)
        assertEquals(listOf("arrive:s1:d", "upload:e1", "upload:f1", "complete:s1:d"), log, "la foto y la firma suben después de la llegada y antes del cierre")
        assertEquals("Ana Torres", repo.events.single().receivedByName)
        assertEquals("45678912", repo.events.single().receivedByDoc)
        assertTrue(q.pending.value.isEmpty())
    }

    @Test(timeout = 60_000) fun `recojo - la foto de la carga sale antes del cierre y el peso viaja en el cuerpo`() = runBlocking<Unit> {
        val repo = Repo()
        val ev = Evidence(listOf(photo("c1", "p", DocumentType.PICKUP_PHOTO), photo("g1", "p", DocumentType.REMISSION_GUIDE)), online = false)
        val q = queue(repo, ev)
        q.enqueue("s1", "p", StopActionOp.COMPLETE, needsPhoto = true, photoType = DocumentType.PICKUP_PHOTO, weightKg = 2450.5)
        assertEquals(QueueStall.NETWORK, q.flush().stall)
        assertEquals(listOf("upload:c1"), log, "sin red la foto no sube y el cierre no sale")

        ev.online = true
        val r = q.flush()
        assertNull(r.stall)
        assertEquals(listOf("upload:c1", "upload:c1", "upload:g1", "complete:s1:p"), log)
        assertEquals(2450.5, repo.events.single().weightKg)
    }

    @Test fun `el cierre del recojo espera la foto obligatoria fallida pero no una opcional`() {
        val required = PendingOperation("a", "s1", "p", OperationKind.COMPLETE, null, "x", needsPhoto = true, photoType = DocumentType.PICKUP_PHOTO)
        val failedByServer = listOf(photo("c1", "p", DocumentType.PICKUP_PHOTO, offline = false))
        assertTrue(required.waitsForEvidence(failedByServer))
        val optional = required.copy(needsPhoto = false, photoType = null)
        assertFalse(optional.waitsForEvidence(failedByServer), "la foto opcional fallida por el servidor no detiene el recojo")
        assertTrue(optional.waitsForEvidence(listOf(photo("c1", "p", DocumentType.PICKUP_PHOTO, offline = true))), "pero una que espera señal sí (si no, se perdería con DOC-008)")
        assertFalse(required.waitsForEvidence(listOf(photo("c1", "p", DocumentType.PICKUP_PHOTO, EvidenceState.UPLOADED, offline = false))))
        // Las colas de la Ola 1 no guardaban el tipo: era la foto de entrega.
        assertEquals(DocumentType.DELIVERY_PHOTO, required.copy(photoType = null).requiredPhoto)
    }

    // ── Espera exponencial ──

    @Test fun `backoff exponencial con tope y jitter`() {
        val b = Backoff(baseMs = 5_000, maxMs = 300_000, random = { 0.0 })
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L, 80_000L, 160_000L, 300_000L, 300_000L), (1..8).map { b.delayFor(it) })
        assertEquals(300_000L, b.delayFor(200), "nunca se desborda ni pasa del tope")
        assertEquals(0L, b.delayFor(0))
        val jittered = Backoff(baseMs = 5_000, maxMs = 300_000, random = { 1.0 })
        assertEquals(4_000L, jittered.delayFor(1), "hasta 20 % menos")
        assertEquals(240_000L, jittered.delayFor(9))
        val random = Backoff()
        repeat(50) { n -> assertTrue(random.delayFor(n + 1) in 1..300_000) }
    }

    @Test fun `el bucle reintenta con espera creciente y Reintentar ahora la reinicia`() = runTest(timeout = 60.seconds) {
        val repo = Repo(online = false)
        val q = queue(repo, scope = backgroundScope)
        q.enqueue("s1", "p", StopActionOp.ARRIVE)
        assertEquals(QueueStall.NETWORK, q.flush().stall)
        assertEquals(1, q.status.value.failures)
        assertEquals(clock.millis() + 5_000, q.status.value.nextAttemptAt)
        runCurrent()
        advanceTimeBy(4_999); runCurrent()
        assertEquals(1, log.size, "todavía no")
        advanceTimeBy(2); runCurrent()
        assertEquals(2, log.size, "a los 5 s")
        advanceTimeBy(9_998); runCurrent() // t = 14.999 s
        assertEquals(2, log.size)
        advanceTimeBy(2); runCurrent()
        assertEquals(3, log.size, "luego 10 s después")
        // Muchos fallos: la espera llega al tope de 5 min y no lo pasa.
        repeat(10) { advanceTimeBy(300_001); runCurrent() }
        assertTrue(q.status.value.failures >= 7)
        q.flush() // un envío externo (el toque del conductor) reinicia la espera desde ahora
        runCurrent()
        val before = log.size
        advanceTimeBy(299_999); runCurrent()
        assertEquals(before, log.size, "nunca antes del tope")
        advanceTimeBy(2); runCurrent()
        assertEquals(before + 1, log.size, "y nunca después")

        // «Reintentar ahora» olvida la espera; con red se vacía.
        repo.online = true
        val r = q.retryNow()
        assertNull(r.stall)
        assertTrue(q.pending.value.isEmpty())
        assertEquals(0, q.status.value.failures)
        assertNull(q.status.value.nextAttemptAt)
    }

    @Test fun `volver la red despierta el bucle al instante`() = runTest(timeout = 60.seconds) {
        val repo = Repo(online = false)
        val q = queue(repo, scope = backgroundScope)
        q.enqueue("s1", "p", StopActionOp.ARRIVE)
        q.flush()
        repeat(4) { advanceTimeBy(200_000); runCurrent() } // varios fallos: la espera ya es larga
        val before = log.size
        repo.online = true
        q.onNetworkAvailable()
        runCurrent()
        assertEquals(before + 1, log.size)
        assertTrue(q.pending.value.isEmpty())
    }

    // ── Emergencia: idempotencia ──

    @Test(timeout = 60_000) fun `el reporte de emergencia repite la misma Idempotency-Key en cada intento y tras reiniciar`() = runBlocking<Unit> {
        val file = File(dir(), "driver/actions.json")
        val incidents = Incidents(online = false)
        val q = queue(Repo(), incidents = incidents, store = TextStore.InFile(file))
        val op = q.enqueueIncident(
            "s1", ReportIncidentRequest(IncidentCategory.ACCIDENT, "Choque", IncidentSeverity.CRITICAL, latitude = -15.5, longitude = -70.1),
        )
        assertNotNull(op.idempotencyKey)
        assertEquals("2026-11-18T08:15:00-05:00", op.incident?.occurredAt, "con la hora del dispositivo al encolar")
        assertEquals(QueueStall.NETWORK, q.flush().stall)
        assertEquals(QueueStall.NETWORK, q.flush().stall)

        // La app se cierra; al volver con señal sale con la misma clave.
        incidents.online = true
        val reborn = queue(Repo(), incidents = incidents, store = TextStore.InFile(file))
        reborn.restore()
        assertNull(reborn.flush().stall)
        assertEquals(3, incidents.keys.size)
        assertEquals(1, incidents.keys.toSet().size, "misma clave en los 3 intentos")
        assertEquals(op.idempotencyKey, incidents.keys.first())
        assertEquals(IncidentSeverity.CRITICAL, incidents.bodies.last().severity)
        assertEquals(-15.5, incidents.bodies.last().latitude)
        assertTrue(reborn.pending.value.isEmpty())
    }

    @Test(timeout = 60_000) fun `la emergencia no espera al cierre detenido por la foto`() = runBlocking<Unit> {
        val ev = Evidence(listOf(photo("e1", "d", DocumentType.DELIVERY_PHOTO, offline = false)), rejectByServer = true)
        val incidents = Incidents()
        val q = queue(Repo(), ev, incidents)
        q.enqueue("s1", "d", StopActionOp.COMPLETE, needsPhoto = true)
        q.enqueueIncident("s1", ReportIncidentRequest(IncidentCategory.THEFT, "Robo"))
        val r = q.flush()
        assertEquals(QueueStall.WAITING_PHOTO, r.stall)
        assertEquals(listOf("upload:e1", "incident:s1"), log)
        assertEquals(listOf(OperationKind.COMPLETE), q.pending.value.map { it.op })
    }

    // ── Errores definitivos ──

    @Test(timeout = 60_000) fun `TRK-010 descarta el cierre y las paradas siguientes pero no la emergencia`() = runBlocking<Unit> {
        val repo = Repo(failComplete = ApiException(400, "LOGYX-TRK-010", "Indica el peso medido en el recojo: la PYME lo pidió", "weightKg"))
        val incidents = Incidents()
        val q = queue(repo, incidents = incidents)
        q.enqueue("s1", "p", StopActionOp.COMPLETE)
        q.enqueue("s1", "d", StopActionOp.ARRIVE)
        q.enqueueIncident("s1", ReportIncidentRequest(IncidentCategory.BREAKDOWN, "Avería"))
        val r = q.flush()
        assertEquals("LOGYX-TRK-010", r.rejection?.error?.apiCode)
        assertEquals("p", r.rejection?.action?.stopId)
        assertEquals(listOf("complete:s1:p", "incident:s1"), log, "la llegada siguiente dependía del cierre (RN08)")
        assertTrue(q.pending.value.isEmpty())
    }

    @Test(timeout = 60_000) fun `TRK-009 y TRK-011 tambien son definitivos y un INC-001 descarta solo el reporte`() = runBlocking<Unit> {
        listOf("LOGYX-TRK-009" to 409, "LOGYX-TRK-011" to 400).forEach { (code, status) ->
            val q = queue(Repo(failComplete = ApiException(status, code, "No")))
            q.enqueue("s1", "p", StopActionOp.COMPLETE)
            assertEquals(code, q.flush().rejection?.error?.apiCode)
            assertTrue(q.pending.value.isEmpty())
        }
        val incidents = Incidents(reject = ApiException(409, "LOGYX-INC-001", "El envío ya tiene una incidencia activa de este tipo"))
        val q = queue(Repo(), incidents = incidents)
        q.enqueueIncident("s1", ReportIncidentRequest(IncidentCategory.ACCIDENT, "Choque"))
        q.enqueue("s1", "p", StopActionOp.ARRIVE)
        val r = q.flush()
        assertEquals("LOGYX-INC-001", r.rejection?.error?.apiCode)
        assertTrue(q.pending.value.isEmpty(), "la llegada no dependía del reporte: también salió")
    }

    @Test(timeout = 60_000) fun `la propuesta de ajuste del conductor lleva su clave estable`() = runBlocking<Unit> {
        val keys = mutableListOf<String?>()
        var online = false
        val adjustments = object : AdjustmentsRepository {
            override suspend fun forShipment(shipmentId: String) = Result.success(ShipmentAdjustmentsView())
            override suspend fun propose(shipmentId: String, request: ProposeAdjustmentRequest, idempotencyKey: String?): Result<AdjustmentView> {
                keys += idempotencyKey
                return if (online) Result.success(AdjustmentView("a1", type = request.type, amount = request.amount)) else Result.failure(offline)
            }
            override suspend fun accept(id: String, note: String?) = Result.failure<AdjustmentView>(IllegalStateException())
            override suspend fun reject(id: String, note: String) = Result.failure<AdjustmentView>(IllegalStateException())
            override suspend fun withdraw(id: String) = Result.failure<AdjustmentView>(IllegalStateException())
        }
        val q = queue(Repo(), adjustments = adjustments)
        q.enqueueAdjustment("s1", ProposeAdjustmentRequest(AdjustmentType.TOLL, 45.5, "Peaje Ilave"))
        q.flush()
        online = true
        q.flush()
        assertEquals(2, keys.size)
        assertEquals(1, keys.toSet().size)
        assertTrue(q.pending.value.isEmpty())
    }

    // ── Estado visible ──

    @Test(timeout = 60_000) fun `conteo y estado de lo que falta sincronizar`() = runBlocking<Unit> {
        val ev = Evidence(listOf(photo("e1", "d", DocumentType.DELIVERY_PHOTO, offline = true)), online = false)
        val q = queue(Repo(online = false), ev, Incidents(online = false))
        q.enqueue("s1", "d", StopActionOp.COMPLETE, needsPhoto = true)
        q.enqueueIncident("s1", ReportIncidentRequest(IncidentCategory.ACCIDENT, "Choque"))
        assertEquals(3, syncItems(q.status.value, ev.flow.value).size, "1 foto + 1 cierre + 1 emergencia")
        assertEquals(QueueStall.NETWORK, q.flush().stall)
        val items = syncItems(q.status.value, ev.flow.value)
        assertEquals(listOf(SyncItemKind.EVIDENCE, SyncItemKind.COMPLETE, SyncItemKind.INCIDENT), items.map { it.kind })
        assertEquals(SyncItemState.WAITING_SIGNAL, items[0].state)
        assertEquals(SyncItemState.WAITING_PHOTO, items[1].state, "el cierre espera su foto")
        assertEquals(SyncItemState.WAITING_SIGNAL, items[2].state)
        assertEquals("Sin red", q.status.value.lastError)

        // Una foto rechazada por el servidor se ve como fallo con su motivo.
        val failed = syncItems(SyncStatus(), listOf(photo("x", "d", DocumentType.DELIVERY_PHOTO, offline = false).copy(error = "Archivo muy grande")))
        assertEquals(SyncItemState.FAILED, failed.single().state)
        assertEquals("Archivo muy grande", failed.single().error)
        // Lo ya subido o cerrado no cuenta.
        assertTrue(syncItems(SyncStatus(), listOf(photo("u", "d", DocumentType.DELIVERY_PHOTO, EvidenceState.UPLOADED), photo("c", "d", DocumentType.DELIVERY_PHOTO, EvidenceState.CLOSED))).isEmpty())
    }

    @Test fun `lee la cola guardada por la Ola 1`() = runBlocking<Unit> {
        val old = """[{"id":"a","shipmentId":"s1","stopId":"d","op":"COMPLETE","occurredAt":"2026-11-18T08:00:00-05:00","createdAt":"2026-11-18T08:00:00-05:00","needsPhoto":true,"ownerId":"u1"}]"""
        val q = queue(Repo(), store = TextStore.InMemory(old))
        q.restore()
        val op = q.pending.value.single()
        assertEquals(OperationKind.COMPLETE, op.op)
        assertEquals(DocumentType.DELIVERY_PHOTO, op.requiredPhoto)
        assertNull(op.idempotencyKey)
    }
}
