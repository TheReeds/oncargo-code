package pe.logyx.core.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import pe.logyx.core.model.DocumentType
import pe.logyx.core.network.LogyxApi
import pe.logyx.core.network.createLogyxHttpClient
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Cola persistente de evidencia (RN06, F14): DOC-007/008 la cierran sin reintentos; los demás fallos siguen reintentables. */
class EvidenceRepositoryTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val ts = """"meta":{"timestamp":"2026-11-17T20:00:00Z"}"""
    private fun dir() = File.createTempFile("evidence", "").let { it.delete(); it.mkdirs(); it }

    private fun error(status: HttpStatusCode, code: String, message: String) =
        """{"error":{"code":"$code","field":"stopId","message":"$message"},$ts}""" to status

    private fun repo(dir: File, engine: MockEngine) =
        DefaultEvidenceRepository(LogyxApi(createLogyxHttpClient("http://localhost", { "tok" }, engine)), dir, compress = { it })

    private fun uploadOk() = """{"data":{"id":"doc1","shipmentId":"s1","stopId":"st2","type":"DELIVERY_PHOTO","contentType":"image/jpeg"},$ts}"""

    @Test fun `DOC-008 descarta la evidencia, borra el archivo y no se reintenta`() = runBlocking<Unit> {
        val dir = dir()
        val (body, status) = error(HttpStatusCode.Conflict, "LOGYX-DOC-008", "La entrega ya se cerró")
        val engine = MockEngine { respond(body, status, json) }
        val repo = repo(dir, engine)

        val item = repo.enqueue("s1", "st2", DocumentType.DELIVERY_PHOTO, byteArrayOf(1, 2, 3))

        assertEquals(EvidenceState.CLOSED, item.state)
        assertEquals(EvidenceErrors.CLOSED_MESSAGE, item.error)
        assertTrue(dir.listFiles().orEmpty().isEmpty(), "el archivo de la cola se borra")
        val calls = engine.requestHistory.size
        repo.retryAll("s1")
        repo.retry(item.id)
        assertEquals(calls, engine.requestHistory.size, "no reintenta una evidencia cerrada")

        // load() no la hace desaparecer antes de que el conductor la vea; discard la quita.
        repo.load("s1")
        assertEquals(EvidenceState.CLOSED, repo.items.value.single { it.id == item.id }.state)
        repo.discard(item.id)
        assertFalse(repo.items.value.any { it.id == item.id })
    }

    @Test fun `DOC-007 el conductor ya no esta asignado y tambien se descarta`() = runBlocking<Unit> {
        val (body, status) = error(HttpStatusCode.Forbidden, "LOGYX-DOC-007", "Solo el conductor asignado")
        val engine = MockEngine { respond(body, status, json) }
        val repo = repo(dir(), engine)
        val item = repo.enqueue("s1", "st2", DocumentType.SIGNATURE, byteArrayOf(9), "image/png")
        assertEquals(EvidenceState.CLOSED, item.state)
        assertEquals(EvidenceErrors.NOT_ASSIGNED_MESSAGE, item.error)
    }

    @Test fun `DOC-009 descarta la foto de la carga con el mensaje del servidor`() = runBlocking<Unit> {
        val (body, status) = error(HttpStatusCode.BadRequest, "LOGYX-DOC-009", "La foto de la carga debe indicar la parada de recojo (stopId)")
        val repo = repo(dir(), MockEngine { respond(body, status, json) })
        val item = repo.enqueue("s1", "st1", DocumentType.PICKUP_PHOTO, byteArrayOf(1))
        assertEquals(EvidenceState.CLOSED, item.state)
        assertEquals("La foto de la carga debe indicar la parada de recojo (stopId)", item.error)
    }

    @Test fun `DOC-005 la llegada sigue en la cola y la foto cuenta como pendiente de senal`() = runBlocking<Unit> {
        val (body, status) = error(HttpStatusCode.Conflict, "LOGYX-DOC-005", "Registra la llegada al recojo antes de subir la foto de la carga")
        val dir = dir()
        val repo = repo(dir, MockEngine { respond(body, status, json) })
        val item = repo.enqueue("s1", "st1", DocumentType.PICKUP_PHOTO, byteArrayOf(1))
        assertEquals(EvidenceState.FAILED, item.state)
        assertTrue(item.awaitingSignal)
        assertEquals(1, dir.listFiles().orEmpty().size)
    }

    @Test fun `restoreQueued lee del disco la cola de todos los envios sin consultar al servidor`() = runBlocking<Unit> {
        val dir = dir()
        val engine = MockEngine { throw java.io.IOException("sin red") }
        repo(dir, engine).apply {
            enqueue("s1", "st1", DocumentType.PICKUP_PHOTO, byteArrayOf(1))
            enqueue("s2", "st9", DocumentType.REMISSION_GUIDE, byteArrayOf(2))
        }
        val reborn = repo(dir, engine)
        val calls = engine.requestHistory.size
        reborn.restoreQueued()
        assertEquals(setOf(DocumentType.PICKUP_PHOTO, DocumentType.REMISSION_GUIDE), reborn.items.value.map { it.type }.toSet())
        assertEquals(calls, engine.requestHistory.size)
    }

    @Test fun `un fallo de red deja la evidencia en la cola para reintentar`() = runBlocking<Unit> {
        var fail = true
        val engine = MockEngine { if (fail) throw java.io.IOException("sin red") else respond(uploadOk(), HttpStatusCode.Created, json) }
        val dir = dir()
        val repo = repo(dir, engine)
        val item = repo.enqueue("s1", "st2", DocumentType.DELIVERY_PHOTO, byteArrayOf(1, 2, 3))
        assertEquals(EvidenceState.FAILED, item.state)
        assertEquals(1, dir.listFiles().orEmpty().size)

        fail = false
        repo.retryAll("s1")
        assertEquals(EvidenceState.UPLOADED, repo.items.value.single { it.id == item.id }.state)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }
}
