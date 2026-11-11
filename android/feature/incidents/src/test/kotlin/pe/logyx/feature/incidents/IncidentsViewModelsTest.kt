package pe.logyx.feature.incidents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    override suspend fun report(shipmentId: String, category: IncidentCategory, description: String): Result<IncidentView> {
        reported = Triple(shipmentId, category, description); return reportResult
    }
    override suspend fun forShipment(shipmentId: String) = Result.success(list)
    override suspend fun detail(id: String) = detail
    override suspend fun comment(id: String, body: String) = commentResult?.invoke(body) ?: Result.success(incident())
    override suspend fun mine(active: Boolean?, page: Int) = Result.failure<Page<IncidentView>>(IllegalStateException())
}

private class FakeDocs(var fail: Boolean = false) : DocumentsRepository {
    var uploads = 0
    override suspend fun list(shipmentId: String) = Result.success(emptyList<DocumentView>())
    override suspend fun file(documentId: String) = Result.success(ByteArray(0))
    override suspend fun upload(shipmentId: String, bytes: ByteArray, fileName: String, mime: String, type: DocumentType, stopId: String?): Result<DocumentView> {
        uploads++
        return if (fail) Result.failure(ApiException(500, null, "x")) else Result.success(DocumentView("d1", shipmentId, type = type))
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
        assertEquals("i1", vm.state.value.reportedId); assertFalse(vm.state.value.photoFailed); assertEquals(0, docs.uploads)
        assertEquals(Triple("s1", IncidentCategory.LOST_CARGO, "Falta un bulto"), inc.reported)
    }

    @Test fun `si la foto falla la incidencia ya existe y se marca photoFailed`() {
        val docs = FakeDocs(fail = true)
        val vm = ReportIncidentViewModel("s1", FakeIncidents(), docs)
        vm.onCategory(IncidentCategory.CARGO_DAMAGE); vm.onDescription("Caja rota"); vm.onPhoto(byteArrayOf(1, 2))
        vm.askSubmit(); vm.confirmSubmit()
        assertEquals(1, docs.uploads); assertEquals("i1", vm.state.value.reportedId); assertTrue(vm.state.value.photoFailed)
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
        val vm = IncidentsListViewModel("s1", FakeIncidents(list = listOf(old, incident("b"))))
        val s = vm.state.value
        assertFalse(s.loading); assertTrue(s.hasActive); assertEquals(listOf("b", "a"), s.items.map { it.id })
    }

    @Test fun `la lista sin incidencias activas permite reportar`() {
        val vm = IncidentsListViewModel("s1", FakeIncidents(list = listOf(incident("a", IncidentStatus.CLOSED, false))))
        assertFalse(vm.state.value.hasActive)
    }

    @Test fun `el detalle carga, expone la organizacion y permite comentar solo si esta activa`() {
        val vm = IncidentDetailViewModel("i1", FakeIncidents(), FakeAuth("org-p"))
        assertEquals("org-p", vm.state.value.organizationId); assertTrue(vm.state.value.canComment)
        val closed = IncidentDetailViewModel("i1", FakeIncidents(detail = Result.success(incident(status = IncidentStatus.RESOLVED, active = false))), FakeAuth())
        assertFalse(closed.state.value.canComment)
    }

    @Test fun `comentar actualiza el hilo y limpia el borrador`() {
        val withComment = incident(comments = listOf(IncidentCommentView("c1", "org-p", "Ana", "Hola")))
        val vm = IncidentDetailViewModel("i1", FakeIncidents(commentResult = { Result.success(withComment) }), FakeAuth())
        vm.onDraft("Hola"); vm.send()
        assertEquals(1, vm.state.value.incident?.comments?.size); assertEquals("", vm.state.value.draft); assertFalse(vm.state.value.sending)
    }

    @Test fun `comentar con error conserva el borrador y avisa`() {
        val vm = IncidentDetailViewModel("i1", FakeIncidents(commentResult = { Result.failure(ApiException(0, null, "Sin conexión.")) }), FakeAuth())
        vm.onDraft("Hola"); vm.send()
        assertEquals("Hola", vm.state.value.draft); assertEquals("Sin conexión.", vm.state.value.message?.text)
    }

    @Test fun `el detalle con error muestra el mensaje y reintenta con refresh`() {
        val inc = FakeIncidents(detail = Result.failure(ApiException(404, null, "No encontramos la incidencia.")))
        val vm = IncidentDetailViewModel("i1", inc, FakeAuth())
        assertEquals("No encontramos la incidencia.", vm.state.value.error)
        inc.detail = Result.success(incident()); vm.refresh()
        assertNull(vm.state.value.error); assertNotNull(vm.state.value.incident)
    }
}
