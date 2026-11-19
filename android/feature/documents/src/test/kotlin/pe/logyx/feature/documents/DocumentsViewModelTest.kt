package pe.logyx.feature.documents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.DocumentsRepository
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.DocumentView
import pe.logyx.core.network.ApiException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeDocs(var result: Result<List<DocumentView>>) : DocumentsRepository {
    var listCalls = 0
    var uploadResult: Result<DocumentView> = Result.success(DocumentView("n1", "s1", type = DocumentType.REMISSION_GUIDE, contentType = "application/pdf"))
    var uploaded: Triple<String, DocumentType, Int>? = null
    override suspend fun list(shipmentId: String): Result<List<DocumentView>> { listCalls++; return result }
    override suspend fun file(documentId: String) = Result.success(byteArrayOf(1, 2, 3))
    override suspend fun upload(shipmentId: String, bytes: ByteArray, fileName: String, mime: String, type: DocumentType, stopId: String?): Result<DocumentView> {
        uploaded = Triple(mime, type, bytes.size)
        return uploadResult
    }
}

private fun doc(id: String, at: String, mime: String = "image/jpeg") =
    DocumentView(id, "s1", type = DocumentType.DELIVERY_PHOTO, contentType = mime, createdAt = at)

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `carga documentos ordenados del mas reciente al mas antiguo`() {
        val vm = DocumentsViewModel("s1", FakeDocs(Result.success(listOf(doc("a", "2026-11-01"), doc("b", "2026-11-03")))))
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(listOf("b", "a"), s.documents.map { it.id })
        assertNull(s.error)
    }

    @Test fun `error inicial muestra error y retry recupera`() {
        val repo = FakeDocs(Result.failure(ApiException(500, "INTERNAL", "Algo salió mal")))
        val vm = DocumentsViewModel("s1", repo)
        assertNotNull(vm.state.value.error)
        repo.result = Result.success(listOf(doc("a", "2026-11-01")))
        vm.retry()
        assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.documents.size)
    }

    @Test fun `refresh fallido conserva datos y avisa con mensaje`() {
        val repo = FakeDocs(Result.success(listOf(doc("a", "2026-11-01"))))
        val vm = DocumentsViewModel("s1", repo)
        repo.result = Result.failure(ApiException(500, "INTERNAL", "Algo salió mal"))
        vm.refresh()
        assertNull(vm.state.value.error)
        assertNotNull(vm.state.value.message)
        assertTrue(vm.state.value.documents.isNotEmpty())
        vm.onMessageShown()
        assertNull(vm.state.value.message)
    }

    @Test fun `fetch devuelve los bytes del archivo`() = runTest {
        val vm = DocumentsViewModel("s1", FakeDocs(Result.success(emptyList())))
        assertContentEquals(byteArrayOf(1, 2, 3), vm.fetch("a").getOrThrow())
    }

    @Test fun `subir una guia valida llama al repositorio, avisa y recarga la lista`() {
        val repo = FakeDocs(Result.success(listOf(doc("a", "2026-11-01"))))
        val vm = DocumentsViewModel("s1", repo)
        val before = repo.listCalls
        vm.upload(ByteArray(2_000), "guia.pdf", "application/pdf", DocumentType.REMISSION_GUIDE)
        assertEquals(Triple("application/pdf", DocumentType.REMISSION_GUIDE, 2_000), repo.uploaded)
        assertFalse(vm.state.value.uploading)
        assertNotNull(vm.state.value.message)
        assertEquals(before + 1, repo.listCalls)
    }

    @Test fun `no sube formatos no admitidos, archivos vacios ni PDF de mas de 10 MB`() {
        val repo = FakeDocs(Result.success(emptyList()))
        val vm = DocumentsViewModel("s1", repo)
        vm.upload(ByteArray(10), "a.gif", "image/gif", DocumentType.OTHER)
        assertNull(repo.uploaded)
        vm.upload(null, "a.pdf", "application/pdf", DocumentType.OTHER)
        vm.upload(ByteArray(MAX_UPLOAD_BYTES + 1), "a.pdf", "application/pdf", DocumentType.OTHER)
        assertNull(repo.uploaded)
        assertNotNull(vm.state.value.message)
        assertEquals(listOf(DocumentType.REMISSION_GUIDE, DocumentType.RECEIPT, DocumentType.SERVICE_ORDER, DocumentType.OTHER), UPLOADABLE_TYPES)
    }

    @Test fun `un error del servidor al subir queda como mensaje y el boton se libera`() {
        val repo = FakeDocs(Result.success(emptyList()))
        repo.uploadResult = Result.failure(ApiException(400, "LOGYX-DOC-001", "Tipo de archivo no permitido: solo JPG, PNG o PDF", "file"))
        val vm = DocumentsViewModel("s1", repo)
        vm.upload(ByteArray(10), "a.pdf", "application/pdf", DocumentType.RECEIPT)
        assertFalse(vm.state.value.uploading)
        assertNotNull(vm.state.value.message)
    }
}
