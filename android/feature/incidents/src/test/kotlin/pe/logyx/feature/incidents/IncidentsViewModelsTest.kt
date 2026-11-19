package pe.logyx.feature.incidents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.DocumentsRepository
import pe.logyx.core.data.IncidentsRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun incident(id: String = "i1", status: IncidentStatus = IncidentStatus.OPEN, active: Boolean = true, comments: List<IncidentCommentView> = emptyList()) =
    IncidentView(id, "s1", category = IncidentCategory.DELAY, description = "Demora", status = status, active = active, createdAt = "2026-11-05T14:00:00-05:00", comments = comments)

private class FakeIncidents(
    var list: List<IncidentView> = emptyList(),
    var reportResult: Result<IncidentView> = Result.success(incident()),
    var detail: Result<IncidentView> = Result.success(incident()),
    var commentResult: ((String) -> Result<IncidentView>)? = null,
) : IncidentsRepository {
    var reported: Triple<String, IncidentCategory, String>? = null
    val requests = mutableListOf<Pair<ReportIncidentRequest, String?>>()
    override suspend fun report(shipmentId: String, request: ReportIncidentRequest, idempotencyKey: String?): Result<IncidentView> {
        requests += request to idempotencyKey
        return report(shipmentId, request.category, request.description)
    }
    override suspend fun report(shipmentId: String, category: IncidentCategory, description: String): Result<IncidentView> {
        reported = Triple(shipmentId, category, description); return reportResult
    }
    override suspend fun forShipment(shipmentId: String) = Result.success(list)
    override suspend fun detail(id: String) = detail
    override suspend fun comment(id: String, body: String) = commentResult?.invoke(body) ?: Result.success(incident())
    override suspend fun mine(active: Boolean?, page: Int) = Result.failure<Page<IncidentView>>(IllegalStateException())
}

private class FakeDocs(var fail: Boolean = false, val failNames: Set<String> = emptySet()) : DocumentsRepository {
    var uploads = 0
    val names = mutableListOf<String>()
    val types = mutableListOf<DocumentType>()
    override suspend fun list(shipmentId: String) = Result.success(emptyList<DocumentView>())
    override suspend fun file(documentId: String) = Result.success(ByteArray(0))
    override suspend fun upload(shipmentId: String, bytes: ByteArray, fileName: String, mime: String, type: DocumentType, stopId: String?): Result<DocumentView> {
        uploads++
        names += fileName
        types += type
        return if (fail || fileName in failNames) Result.failure(ApiException(500, null, "x")) else Result.success(DocumentView("doc-$uploads", shipmentId, type = type))
    }
}

private class FakeAuth(org: String = "org-p") : AuthRepository {
    override val state: StateFlow<SessionState> = MutableStateFlow(
        SessionState.LoggedIn(UserInfo("u", "a@b.pe", "Ana", Role.ADMIN, org, "Org", OrgType.PYME)),
    )
    override suspend fun login(email: String, password: String): Result<UserInfo> = throw NotImplementedError()
    override suspend fun logout() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentsViewModelsTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `reportar valida categoria y descripcion antes de confirmar`() {
        val vm = ReportIncidentViewModel("s1", FakeIncidents(), FakeDocs())
        vm.askSubmit()
        assertNotNull(vm.state.value.categoryError); assertNotNull(vm.state.value.descriptionError); assertFalse(vm.state.value.confirming)
        vm.onCategory(IncidentCategory.DELAY); vm.onDescription("   ")
        vm.askSubmit()
        assertNull(vm.state.value.categoryError); assertNotNull(vm.state.value.descriptionError)
        vm.onDescription("a".repeat(MAX_DESCRIPTION + 1))
        vm.askSubmit()
        assertNotNull(vm.state.value.descriptionError); assertFalse(vm.state.value.confirming)
        vm.onDescription("Demora en el peaje")
        vm.askSubmit()
        assertTrue(vm.state.value.confirming)
    }

    @Test fun `reportar sin foto crea la incidencia y no sube documentos`() {
        val inc = FakeIncidents(); val docs = FakeDocs()
        val vm = ReportIncidentViewModel("s1", inc, docs)
        vm.onCategory(IncidentCategory.LOST_CARGO); vm.onDescription("Falta un bulto")
        vm.askSubmit(); vm.confirmSubmit()
        assertEquals("i1", vm.state.value.reportedId); assertEquals(0, vm.state.value.attachFailed); assertEquals(0, docs.uploads)
        assertEquals(Triple("s1", IncidentCategory.LOST_CARGO, "Falta un bulto"), inc.reported)
    }

    @Test fun `409 de incidencia activa muestra el mensaje de la API y no navega`() {
        val inc = FakeIncidents(reportResult = Result.failure(ApiException(409, "LOGYX-INC-001", "Ya hay una incidencia activa para este envío.")))
        val vm = ReportIncidentViewModel("s1", inc, FakeDocs())
        vm.onCategory(IncidentCategory.OTHER); vm.onDescription("Otra")
        vm.askSubmit(); vm.confirmSubmit()
        assertNull(vm.state.value.reportedId); assertFalse(vm.state.value.submitting)
        assertEquals("Ya hay una incidencia activa para este envío.", vm.state.value.message?.text)
    }

    @Test fun `error de campo de la API se asigna a la descripcion`() {
        val inc = FakeIncidents(reportResult = Result.failure(ApiException(422, "V", "Descripción inválida.", field = "description")))
        val vm = ReportIncidentViewModel("s1", inc, FakeDocs())
        vm.onCategory(IncidentCategory.OTHER); vm.onDescription("x")
        vm.askSubmit(); vm.confirmSubmit()
        assertEquals(UiText.Raw("Descripción inválida."), vm.state.value.descriptionError)
        assertNull(vm.state.value.message)
    }

    @Test fun `la lista detecta incidencia activa y ordena por fecha`() {
        val old = incident("a", IncidentStatus.RESOLVED, false).copy(createdAt = "2026-10-01T10:00:00-05:00")
        val vm = IncidentsListViewModel("s1", FakeIncidents(list = listOf(old, incident("b"))), FakeRealtime())
        val s = vm.state.value
        assertFalse(s.loading); assertTrue(s.hasActive); assertEquals(listOf("b", "a"), s.items.map { it.id })
    }

    @Test fun `la lista sin incidencias activas permite reportar`() {
        val vm = IncidentsListViewModel("s1", FakeIncidents(list = listOf(incident("a", IncidentStatus.CLOSED, false))), FakeRealtime())
        assertFalse(vm.state.value.hasActive)
    }

    @Test fun `el detalle carga, expone la organizacion y permite comentar solo si esta activa`() {
        val vm = IncidentDetailViewModel("i1", FakeIncidents(), FakeAuth("org-p"), FakeRealtime())
        assertEquals("org-p", vm.state.value.organizationId); assertTrue(vm.state.value.canComment)
        val closed = IncidentDetailViewModel("i1", FakeIncidents(detail = Result.success(incident(status = IncidentStatus.RESOLVED, active = false))), FakeAuth(), FakeRealtime())
        assertFalse(closed.state.value.canComment)
    }

    @Test fun `comentar actualiza el hilo y limpia el borrador`() {
        val withComment = incident(comments = listOf(IncidentCommentView("c1", "org-p", "Ana", "Hola")))
        val vm = IncidentDetailViewModel("i1", FakeIncidents(commentResult = { Result.success(withComment) }), FakeAuth(), FakeRealtime())
        vm.onDraft("Hola"); vm.send()
        assertEquals(1, vm.state.value.incident?.comments?.size); assertEquals("", vm.state.value.draft); assertFalse(vm.state.value.sending)
    }

    @Test fun `RN07 - NEG-001 en el comentario queda bajo el campo y conserva el borrador`() {
        val e = ApiException(400, "LOGYX-NEG-001", "No incluyas teléfonos ni correos.", field = "body")
        val vm = IncidentDetailViewModel("i1", FakeIncidents(commentResult = { Result.failure(e) }), FakeAuth(), FakeRealtime())
        vm.onDraft("escríbeme a ana@correo.pe"); vm.send()
        assertEquals("No incluyas teléfonos ni correos.", vm.state.value.commentError)
        assertEquals("escríbeme a ana@correo.pe", vm.state.value.draft)
        assertNull(vm.state.value.message); assertFalse(vm.state.value.sending)
        vm.onDraft("Hola"); assertNull(vm.state.value.commentError)
    }

    @Test fun `el comentario se corta a 2000 como el backend`() {
        val vm = IncidentDetailViewModel("i1", FakeIncidents(), FakeAuth(), FakeRealtime())
        vm.onDraft("a".repeat(2500))
        assertEquals(2000, vm.state.value.draft.length)
    }

    @Test fun `RN07 - NEG-001 en la descripcion de la incidencia queda bajo el campo`() {
        val inc = FakeIncidents(reportResult = Result.failure(ApiException(400, "LOGYX-NEG-001", "Sin datos de contacto.", field = "description")))
        val vm = ReportIncidentViewModel("s1", inc, FakeDocs())
        vm.onCategory(IncidentCategory.OTHER); vm.onDescription("llámame 987654321")
        vm.askSubmit(); vm.confirmSubmit()
        assertEquals(UiText.Raw("Sin datos de contacto."), vm.state.value.descriptionError)
        assertNull(vm.state.value.message)
    }

    @Test fun `comentar con error conserva el borrador y avisa`() {
        val vm = IncidentDetailViewModel("i1", FakeIncidents(commentResult = { Result.failure(ApiException(0, null, "Sin conexión.")) }), FakeAuth(), FakeRealtime())
        vm.onDraft("Hola"); vm.send()
        assertEquals("Hola", vm.state.value.draft); assertEquals("Sin conexión.", vm.state.value.message?.text)
    }

    @Test fun `el detalle con error muestra el mensaje y reintenta con refresh`() {
        val inc = FakeIncidents(detail = Result.failure(ApiException(404, null, "No encontramos la incidencia.")))
        val vm = IncidentDetailViewModel("i1", inc, FakeAuth(), FakeRealtime())
        assertEquals("No encontramos la incidencia.", vm.state.value.error)
        inc.detail = Result.success(incident()); vm.refresh()
        assertNull(vm.state.value.error); assertNotNull(vm.state.value.incident)
    }

    @Test fun `detalle refresca con incident updated de su incidencia y no con otra`() = runTest(UnconfinedTestDispatcher()) {
        val inc = FakeIncidents()
        val rt = FakeRealtime()
        val vm = IncidentDetailViewModel("i1", inc, FakeAuth(), rt)
        val job = launch { vm.liveLoop(commentsPollMs = 3_600_000) }
        inc.detail = Result.success(incident(status = IncidentStatus.IN_REVIEW))
        rt.emit(RealtimeTypes.INCIDENT_UPDATED, "incidentId" to "otra", "shipmentId" to "s1")
        assertEquals(IncidentStatus.OPEN, vm.state.value.incident?.status)
        rt.emit(RealtimeTypes.INCIDENT_UPDATED, "incidentId" to "i1", "shipmentId" to "s1")
        assertEquals(IncidentStatus.IN_REVIEW, vm.state.value.incident?.status)
        job.cancel()
    }

    @Test fun `lista de incidencias refresca con incident reported del envio`() = runTest(UnconfinedTestDispatcher()) {
        val inc = FakeIncidents()
        val rt = FakeRealtime()
        val vm = IncidentsListViewModel("s1", inc, rt)
        val job = launch { vm.liveLoop() }
        assertEquals(0, vm.state.value.items.size)
        inc.list = listOf(incident("a"))
        rt.emit(RealtimeTypes.INCIDENT_REPORTED, "incidentId" to "a", "shipmentId" to "otro")
        assertEquals(0, vm.state.value.items.size)
        rt.emit(RealtimeTypes.INCIDENT_REPORTED, "incidentId" to "a", "shipmentId" to "s1")
        assertEquals(1, vm.state.value.items.size)
        job.cancel()
    }
}
