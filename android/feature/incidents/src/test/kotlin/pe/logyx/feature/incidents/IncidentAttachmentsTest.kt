package pe.logyx.feature.incidents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.DocumentsRepository
import pe.logyx.core.data.IncidentsRepository
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class AttDocs(val failNames: Set<String> = emptySet()) : DocumentsRepository {
    val uploads = mutableListOf<Triple<String, String, DocumentType>>()
    override suspend fun list(shipmentId: String) = Result.success(emptyList<DocumentView>())
    override suspend fun file(documentId: String) = Result.success(ByteArray(0))
    override suspend fun upload(shipmentId: String, bytes: ByteArray, fileName: String, mime: String, type: DocumentType, stopId: String?): Result<DocumentView> {
        uploads += Triple(shipmentId, fileName, type)
        return if (fileName in failNames) Result.failure(ApiException(500, null, "x")) else Result.success(DocumentView("doc-$fileName", shipmentId, type = type))
    }
}

private class AttIncidents(var result: Result<IncidentView> = Result.success(IncidentView("i1", "s1", category = IncidentCategory.CARGO_DAMAGE, description = "d", status = IncidentStatus.OPEN))) : IncidentsRepository {
    val requests = mutableListOf<Pair<ReportIncidentRequest, String?>>()
    override suspend fun report(shipmentId: String, category: IncidentCategory, description: String) = result
    override suspend fun report(shipmentId: String, request: ReportIncidentRequest, idempotencyKey: String?): Result<IncidentView> {
        requests += request to idempotencyKey
        return result
    }
    override suspend fun forShipment(shipmentId: String) = Result.success(emptyList<IncidentView>())
    override suspend fun detail(id: String) = Result.failure<IncidentView>(IllegalStateException())
    override suspend fun comment(id: String, body: String) = Result.failure<IncidentView>(IllegalStateException())
    override suspend fun mine(active: Boolean?, page: Int) = Result.failure<Page<IncidentView>>(IllegalStateException())
}

/** Ola 2 (2.7): hasta 5 adjuntos al reportar una incidencia; se suben como documentos `OTHER` y sus ids viajan en `attachmentIds`. */
@OptIn(ExperimentalCoroutinesApi::class)
class IncidentAttachmentsTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private var keys = 0
    private fun vm(inc: AttIncidents = AttIncidents(), docs: AttDocs = AttDocs()) =
        ReportIncidentViewModel("s1", inc, docs, newKey = { "key-${++keys}" }).also {
            it.onCategory(IncidentCategory.CARGO_DAMAGE); it.onDescription("Caja rota")
        }

    private fun ReportIncidentViewModel.photo(name: String = "f.jpg") = addAttachment(byteArrayOf(1, 2, 3), name, "image/jpeg")
    private fun ReportIncidentViewModel.submit() { askSubmit(); confirmSubmit() }

    @Test fun `acepta hasta 5 adjuntos y el sexto se rechaza con aviso`() {
        val v = vm()
        repeat(5) { assertTrue(v.photo("f$it.jpg")) }
        assertEquals(5, v.state.value.attachments.size)
        assertFalse(v.state.value.canAttachMore)
        assertFalse(v.photo("f6.jpg"))
        assertEquals(5, v.state.value.attachments.size)
        assertEquals(R.string.incidents_attach_limit, v.state.value.message?.textRes)
    }

    @Test fun `quitar un adjunto libera el lugar y conserva el orden de los demas`() {
        val v = vm()
        repeat(5) { v.photo("f$it.jpg") }
        v.removeAttachment(v.state.value.attachments[1].id)
        assertEquals(listOf("f0.jpg", "f2.jpg", "f3.jpg", "f4.jpg"), v.state.value.attachments.map { it.fileName })
        assertTrue(v.state.value.canAttachMore)
        assertTrue(v.photo("nuevo.jpg"))
        assertEquals("nuevo.jpg", v.state.value.attachments.last().fileName)
    }

    @Test fun `valida formato, tamano y archivo vacio`() {
        val v = vm()
        assertFalse(v.addAttachment(null, "a.pdf", "application/pdf"))
        assertFalse(v.addAttachment(ByteArray(0), "a.pdf", "application/pdf"))
        assertFalse(v.addAttachment(byteArrayOf(1), "a.zip", "application/zip"))
        assertFalse(v.addAttachment(byteArrayOf(1), "a", null))
        assertFalse(v.addAttachment(ByteArray(MAX_ATTACHMENT_BYTES + 1), "grande.pdf", "application/pdf"))
        assertTrue(v.state.value.attachments.isEmpty())
        assertTrue(v.addAttachment(ByteArray(MAX_ATTACHMENT_BYTES), "justo.pdf", "application/pdf"))
        assertTrue(v.addAttachment(byteArrayOf(1), "a.png", "image/png"))
        assertTrue(v.state.value.attachments[0].isPdf)
        assertTrue(v.state.value.attachments[1].isImage)
    }

    @Test fun `el reporte lleva en attachmentIds los ids de los documentos subidos como OTHER`() {
        val inc = AttIncidents(); val docs = AttDocs()
        val v = vm(inc, docs)
        v.photo("a.jpg"); v.addAttachment(byteArrayOf(9), "guia.pdf", "application/pdf")
        v.submit()
        assertEquals(listOf("a.jpg", "guia.pdf"), docs.uploads.map { it.second })
        assertTrue(docs.uploads.all { it.first == "s1" && it.third == DocumentType.OTHER })
        val (request, key) = inc.requests.single()
        assertEquals(listOf("doc-a.jpg", "doc-guia.pdf"), request.attachmentIds)
        assertEquals(IncidentCategory.CARGO_DAMAGE, request.category)
        assertEquals("Caja rota", request.description)
        assertEquals("key-1", key)
        assertEquals("i1", v.state.value.reportedId)
        assertEquals(0, v.state.value.attachFailed)
    }

    @Test fun `sin adjuntos no manda attachmentIds`() {
        val inc = AttIncidents()
        vm(inc).submit()
        assertNull(inc.requests.single().first.attachmentIds)
    }

    @Test fun `si un adjunto falla se reporta sin el y se avisa cuantos faltaron`() {
        val inc = AttIncidents(); val docs = AttDocs(failNames = setOf("b.jpg"))
        val v = vm(inc, docs)
        v.photo("a.jpg"); v.photo("b.jpg"); v.photo("c.jpg")
        v.submit()
        assertEquals(listOf("doc-a.jpg", "doc-c.jpg"), inc.requests.single().first.attachmentIds)
        assertEquals("i1", v.state.value.reportedId)
        assertEquals(1, v.state.value.attachFailed)
        assertNull(v.state.value.message)
    }

    @Test fun `si todos los adjuntos fallan igual se reporta`() {
        val inc = AttIncidents()
        val v = vm(inc, AttDocs(failNames = setOf("a.jpg", "b.jpg")))
        v.photo("a.jpg"); v.photo("b.jpg")
        v.submit()
        assertNull(inc.requests.single().first.attachmentIds)
        assertEquals(2, v.state.value.attachFailed)
        assertEquals("i1", v.state.value.reportedId)
    }

    @Test fun `INC-011 sale por userMessage, un reintento no vuelve a subir y repite la clave`() {
        val inc = AttIncidents(Result.failure(ApiException(400, "LOGYX-INC-011", "Los adjuntos deben ser documentos de este envío.")))
        val docs = AttDocs()
        val v = vm(inc, docs)
        v.photo("a.jpg")
        v.submit()
        assertNull(v.state.value.reportedId)
        assertFalse(v.state.value.submitting)
        assertEquals("Los adjuntos deben ser documentos de este envío.", v.state.value.message?.text)

        inc.result = Result.success(IncidentView("i2", "s1", category = IncidentCategory.CARGO_DAMAGE, description = "d", status = IncidentStatus.OPEN))
        v.submit()
        assertEquals(1, docs.uploads.size, "el documento ya subido no se sube otra vez")
        assertEquals(2, inc.requests.size)
        assertEquals(inc.requests[0].second, inc.requests[1].second, "mismo Idempotency-Key si el reporte no cambió")
        assertEquals(listOf("doc-a.jpg"), inc.requests[1].first.attachmentIds)
        assertEquals("i2", v.state.value.reportedId)
    }

    @Test fun `si el reporte cambia entre intentos usa otra clave`() {
        val inc = AttIncidents(Result.failure(ApiException(0, null, "Sin conexión.")))
        val v = vm(inc)
        v.submit()
        v.onDescription("Caja rota y mojada")
        v.submit()
        assertNotEquals(inc.requests[0].second, inc.requests[1].second)
    }
}
