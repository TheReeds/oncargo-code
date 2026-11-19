package pe.logyx.core.data

import kotlinx.coroutines.runBlocking
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.DriverView
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.ShipmentView
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Conductor sin señal (roadmap 1.11): caché del último viaje, cola persistente de arrive/complete y aplicación optimista. */
class DriverOfflineTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-18T13:15:00Z"), ZoneOffset.ofHours(-5))
    private fun dir() = File.createTempFile("driver", "").let { it.delete(); it.mkdirs(); it }

    private fun stop(id: String, seq: Int, type: StopType, status: StopStatus = StopStatus.PENDING) =
        StopView(id, seq, type, if (type == StopType.PICKUP) "LIMA" else "JULIACA", "Dirección $seq", status)

    private val trip = ShipmentView(
        "s1", originCity = "LIMA", destinationCity = "JULIACA", status = ShipmentStatus.PENDING, code = "OC-7K2QX",
        stops = listOf(stop("p", 1, StopType.PICKUP), stop("d", 2, StopType.DELIVERY).copy(contactName = "Ana", contactPhone = "987654321")),
    )

    /** Backend simulado: [respond] decide el resultado de cada llamada; [calls] registra (op, stopId, occurredAt). */
    private class FakeRepo(var respond: (op: String, stopId: String, occurredAt: String?) -> Result<ShipmentView>) : ShipmentsRepository {
        val calls = mutableListOf<Triple<String, String, String?>>()
        override suspend fun list() = Result.success(emptyList<ShipmentView>())
        override suspend fun detail(id: String) = Result.failure<ShipmentView>(IllegalStateException())
        override suspend fun drivers() = Result.success(emptyList<DriverView>())
        override suspend fun assignDriver(shipmentId: String, driverId: String, reason: String?) = Result.failure<ShipmentView>(IllegalStateException())
        override suspend fun driverShipments() = Result.success(emptyList<ShipmentView>())
        override suspend fun arrive(shipmentId: String, stopId: String) = arrive(shipmentId, stopId, null)
        override suspend fun complete(shipmentId: String, stopId: String) = complete(shipmentId, stopId, null)
        override suspend fun arrive(shipmentId: String, stopId: String, occurredAt: String?): Result<ShipmentView> {
            calls += Triple("arrive", stopId, occurredAt); return respond("arrive", stopId, occurredAt)
        }
        override suspend fun complete(shipmentId: String, stopId: String, occurredAt: String?): Result<ShipmentView> {
            calls += Triple("complete", stopId, occurredAt); return respond("complete", stopId, occurredAt)
        }
    }

    private val offline = ApiException(0, "NETWORK", "Sin red")
    private fun ok(t: ShipmentView = trip) = Result.success(t)

    // --- DriverTripCache ---

    @Test fun `la cache guarda y lee el viaje con la hora de guardado`() = runBlocking<Unit> {
        val file = File(dir(), "driver/active-trip.json")
        val cache = DriverTripCache(TextStore.InFile(file), { "u1" }, clock)
        val savedAt = cache.save(trip)
        assertEquals("2026-11-18T08:15:00-05:00", savedAt)
        assertTrue(file.exists())

        val read = DriverTripCache(TextStore.InFile(file), { "u1" }, clock).load()
        assertNotNull(read)
        assertEquals(trip, read.trip)
        assertEquals("2026-11-18T08:15:00-05:00", read.savedAt)
        assertEquals("Ana", read.trip.stops.last().contactName)

        // Otro conductor en el mismo equipo no ve el viaje guardado.
        assertNull(DriverTripCache(TextStore.InFile(file), { "u2" }, clock).load())
        // Sin viaje activo se borra.
        cache.save(null)
        assertNull(cache.load())
        assertTrue(!file.exists())
    }

    @Test fun `un archivo corrupto se trata como vacio`() = runBlocking<Unit> {
        val file = File(dir(), "trip.json").apply { writeText("{no es json") }
        assertNull(DriverTripCache(TextStore.InFile(file)).load())
    }

    // --- DriverActionQueue ---

    @Test fun `envia en orden FIFO con la hora del dispositivo y se vacia con 200`() = runBlocking<Unit> {
        val repo = FakeRepo { _, _, _ -> Result.failure(offline) }
        val queue = DriverActionQueue(repo, TextStore.InMemory(), clock = clock)
        queue.enqueue("s1", "p", StopActionOp.ARRIVE)
        queue.enqueue("s1", "p", StopActionOp.COMPLETE)
        queue.enqueue("s1", "p", StopActionOp.COMPLETE) // toque repetido: no se duplica
        assertEquals(2, queue.pending.value.size)

        val stalled = queue.flush()
        assertEquals(QueueStall.NETWORK, stalled.stall)
        assertEquals(2, queue.pending.value.size, "sin red todo queda en la cola")
        assertEquals(1, repo.calls.size, "no salta a la siguiente si la primera no salió")

        repo.respond = { _, _, _ -> ok() }
        val done = queue.flush()
        assertNull(done.stall); assertNull(done.rejection)
        assertEquals(trip, done.latest)
        assertTrue(queue.pending.value.isEmpty())
        assertEquals(listOf("arrive", "arrive", "complete"), repo.calls.map { it.first })
        assertEquals("2026-11-18T08:15:00-05:00", repo.calls.last().third)
    }

    @Test fun `la cola sobrevive al cierre de la app`() = runBlocking<Unit> {
        val file = File(dir(), "driver/actions.json")
        val repo = FakeRepo { _, _, _ -> Result.failure(offline) }
        DriverActionQueue(repo, TextStore.InFile(file), clock = clock).apply {
            enqueue("s1", "p", StopActionOp.ARRIVE)
            flush()
        }
        val reborn = DriverActionQueue(FakeRepo { _, _, _ -> ok() }, TextStore.InFile(file), clock = clock)
        reborn.restore()
        assertEquals(listOf("p"), reborn.pending.value.map { it.stopId })
        assertEquals("2026-11-18T08:15:00-05:00", reborn.pending.value.single().occurredAt)
        reborn.flush()
        assertTrue(reborn.pending.value.isEmpty())
        assertTrue(!file.exists(), "cola vacía = sin archivo")
    }

    @Test fun `las acciones de otro usuario se descartan al restaurar`() = runBlocking<Unit> {
        val store = TextStore.InMemory()
        DriverActionQueue(FakeRepo { _, _, _ -> Result.failure(offline) }, store, currentUserId = { "u1" }, clock = clock)
            .enqueue("s1", "p", StopActionOp.ARRIVE)
        val other = DriverActionQueue(FakeRepo { _, _, _ -> ok() }, store, currentUserId = { "u2" }, clock = clock)
        other.restore()
        assertTrue(other.pending.value.isEmpty())
    }

    @Test fun `TRK-008 repite la misma accion sin occurredAt`() = runBlocking<Unit> {
        val repo = FakeRepo { _, _, at ->
            if (at != null) Result.failure(ApiException(400, "LOGYX-TRK-008", "Hora inválida", "occurredAt")) else ok()
        }
        val queue = DriverActionQueue(repo, TextStore.InMemory(), clock = clock)
        queue.enqueue("s1", "p", StopActionOp.ARRIVE)
        val r = queue.flush()
        assertNull(r.rejection)
        assertEquals(listOf("2026-11-18T08:15:00-05:00", null), repo.calls.map { it.third })
        assertEquals(listOf("arrive", "arrive"), repo.calls.map { it.first })
        assertTrue(queue.pending.value.isEmpty())
    }

    @Test fun `TRK-008 y luego sin red guarda la accion ya sin hora`() = runBlocking<Unit> {
        val repo = FakeRepo { _, _, at ->
            if (at != null) Result.failure(ApiException(400, "LOGYX-TRK-008", "Hora inválida", "occurredAt")) else Result.failure(offline)
        }
        val queue = DriverActionQueue(repo, TextStore.InMemory(), clock = clock)
        queue.enqueue("s1", "p", StopActionOp.ARRIVE)
        assertEquals(QueueStall.NETWORK, queue.flush().stall)
        assertNull(queue.pending.value.single().occurredAt)
    }

    @Test fun `repetir una accion ya aplicada es 200 y se quita`() = runBlocking<Unit> {
        val delivered = trip.copy(status = ShipmentStatus.DELIVERED, stops = trip.stops.map { it.copy(status = StopStatus.COMPLETED) })
        val repo = FakeRepo { _, _, _ -> ok(delivered) }
        val queue = DriverActionQueue(repo, TextStore.InMemory(), clock = clock)
        queue.enqueue("s1", "d", StopActionOp.COMPLETE)
        val r = queue.flush()
        assertEquals(ShipmentStatus.DELIVERED, r.latest?.status)
        assertTrue(queue.pending.value.isEmpty())
    }

    @Test fun `un error definitivo descarta la accion y las siguientes del mismo envio`() = runBlocking<Unit> {
        val repo = FakeRepo { op, _, _ ->
            if (op == "arrive") Result.failure(ApiException(409, "LOGYX-TRK-005", "Incidencia activa")) else ok()
        }
        val queue = DriverActionQueue(repo, TextStore.InMemory(), clock = clock)
        queue.enqueue("s1", "p", StopActionOp.ARRIVE)
        queue.enqueue("s1", "p", StopActionOp.COMPLETE)
        val r = queue.flush()
        assertEquals("LOGYX-TRK-005", r.rejection?.error?.apiCode)
        assertEquals("p", r.rejection?.action?.stopId)
        assertTrue(queue.pending.value.isEmpty())
        assertEquals(1, repo.calls.size, "el complete dependía de la llegada: no se manda")

        // 404 (ya no es tu envío) también es definitivo; un 503 no.
        assertTrue(!ApiException(404, "LOGYX-SHP-001", "No existe").isRetryable)
        assertTrue(ApiException(503, null, "Caído").isRetryable)
        assertTrue(ApiException(429, null, "Espera").isRetryable)
    }

    @Test fun `el cierre de entrega espera a que suba la foto`() = runBlocking<Unit> {
        var evidence = listOf(EvidenceItem("e1", "s1", "d", DocumentType.DELIVERY_PHOTO, EvidenceState.FAILED, offline = true))
        val repo = FakeRepo { _, _, _ -> ok() }
        val queue = DriverActionQueue(repo, TextStore.InMemory(), evidence = { evidence }, clock = clock)
        queue.enqueue("s1", "d", StopActionOp.COMPLETE, needsPhoto = true)

        assertEquals(QueueStall.WAITING_PHOTO, queue.flush().stall)
        assertTrue(repo.calls.isEmpty(), "no se manda antes que la foto")

        evidence = evidence.map { it.copy(state = EvidenceState.UPLOADED) }
        val r = queue.flush()
        assertNull(r.stall)
        assertEquals(listOf("complete"), repo.calls.map { it.first })
        assertTrue(queue.pending.value.isEmpty())
    }

    @Test fun `sin evidencia local pendiente el cierre se manda y decide el servidor`() {
        val action = PendingStopAction("a", "s1", "d", StopActionOp.COMPLETE, null, "x", needsPhoto = true)
        assertTrue(!action.waitsForPhoto(emptyList()))
        // Ola 2 (2.5): una firma en cola también sale antes del cierre (después el servidor la rechazaría con DOC-008).
        val signatureOnly = listOf(EvidenceItem("f", "s1", "d", DocumentType.SIGNATURE, EvidenceState.QUEUED))
        assertTrue(action.waitsForPhoto(signatureOnly))
        val signatureUploaded = listOf(EvidenceItem("f", "s1", "d", DocumentType.SIGNATURE, EvidenceState.UPLOADED))
        assertTrue(!action.waitsForPhoto(signatureUploaded))
    }

    // --- Aplicación optimista ---

    @Test fun `withPending avanza las paradas y cierra el envio con la ultima entrega`() {
        fun a(stop: String, op: StopActionOp, at: String) = PendingStopAction(stop + op, "s1", stop, op, at, at)
        val arrived = trip.withPending(listOf(a("p", StopActionOp.ARRIVE, "2026-11-18T08:00:00-05:00")))
        assertEquals(StopStatus.ARRIVED, arrived.stops.first().status)
        assertEquals("2026-11-18T08:00:00-05:00", arrived.stops.first().arrivedAt)
        assertEquals(ShipmentStatus.IN_TRANSIT, arrived.status)

        val all = listOf(
            a("p", StopActionOp.ARRIVE, "t1"), a("p", StopActionOp.COMPLETE, "t2"),
            a("d", StopActionOp.ARRIVE, "t3"), a("d", StopActionOp.COMPLETE, "t4"),
        )
        val delivered = trip.withPending(all)
        assertEquals(ShipmentStatus.DELIVERED, delivered.status)
        assertEquals("t4", delivered.deliveredAt)
        assertTrue(delivered.stops.all { it.status == StopStatus.COMPLETED })

        // Nunca retrocede lo que el servidor ya confirmó ni toca otros envíos.
        val server = trip.copy(stops = trip.stops.map { if (it.id == "p") it.copy(status = StopStatus.COMPLETED) else it })
        assertEquals(StopStatus.COMPLETED, server.withPending(listOf(a("p", StopActionOp.ARRIVE, "t"))).stops.first().status)
        assertEquals(trip, trip.withPending(listOf(PendingStopAction("x", "otro", "p", StopActionOp.ARRIVE, "t", "t"))))
    }
}
