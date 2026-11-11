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
    override suspend fun list(shipmentId: String): Result<List<DocumentView>> { listCalls++; return result }
    override suspend fun file(documentId: String) = Result.success(byteArrayOf(1, 2, 3))
    override suspend fun upload(shipmentId: String, bytes: ByteArray, fileName: String, mime: String, type: DocumentType, stopId: String?) =
        Result.failure<DocumentView>(UnsupportedOperationException())
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
}
