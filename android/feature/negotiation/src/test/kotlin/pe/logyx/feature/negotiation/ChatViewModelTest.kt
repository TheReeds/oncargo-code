package pe.logyx.feature.negotiation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.NegotiationRepository
import pe.logyx.core.designsystem.components.BubbleState
import pe.logyx.core.model.ChatThreadView
import pe.logyx.core.model.CounterOfferView
import pe.logyx.core.model.CursorPage
import pe.logyx.core.model.MessageView
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun msg(id: String, at: String, mine: Boolean = false, body: String = "m$id") =
    MessageView(id = id, body = body, mine = mine, createdAt = at)

private class FakeNegotiation : NegotiationRepository {
    var latest: CursorPage<MessageView> = CursorPage(emptyList(), null)
    var older: CursorPage<MessageView> = CursorPage(emptyList(), null)
    var sendResult: CompletableDeferred<Result<MessageView>>? = null
    var sendImmediate: Result<MessageView>? = null
    val sent = mutableListOf<String>()
    var lastBefore: String? = "unset"

    override suspend fun threads(requestId: String) = Result.success(emptyList<ChatThreadView>())
    override suspend fun messages(requestId: String, carrierOrgId: String, before: String?): Result<CursorPage<MessageView>> {
        lastBefore = before
        return Result.success(if (before == null) latest else older)
    }
    override suspend fun send(requestId: String, carrierOrgId: String, body: String): Result<MessageView> {
        sent += body
        return sendImmediate ?: sendResult!!.await()
    }
    override suspend fun counterOffers(bidId: String) = Result.success(emptyList<CounterOfferView>())
    override suspend fun propose(bidId: String, amount: Double, message: String?) = Result.failure<CounterOfferView>(IllegalStateException())
    override suspend fun accept(counterOfferId: String) = Result.failure<CounterOfferView>(IllegalStateException())
    override suspend fun reject(counterOfferId: String) = Result.failure<CounterOfferView>(IllegalStateException())
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: FakeNegotiation) = ChatViewModel("r1", "c1", repo)

    @Test fun `carga mensajes en orden cronologico y expone el cursor`() {
        val repo = FakeNegotiation().apply {
            latest = CursorPage(listOf(msg("2", "2026-11-10T10:00:00-05:00"), msg("1", "2026-11-10T09:00:00-05:00")), "cur")
            older = CursorPage(listOf(msg("0", "2026-11-10T08:00:00-05:00")), null)
        }
        val vm = vm(repo)
        assertEquals(listOf("1", "2"), vm.state.value.entries.map { it.id })
        assertEquals("cur", vm.state.value.nextCursor)
        vm.loadOlder()
        assertEquals("cur", repo.lastBefore)
        assertEquals(listOf("0", "1", "2"), vm.state.value.entries.map { it.id })
        assertNull(vm.state.value.nextCursor)
    }

    @Test fun `envio optimista aparece Sending y se reemplaza por el real`() = runTest {
        val repo = FakeNegotiation().apply { sendResult = CompletableDeferred() }
        val vm = vm(repo)
        vm.onDraft("Hola")
        vm.send()
        val pending = vm.state.value.entries.single()
        assertEquals(BubbleState.Sending, pending.state)
        assertTrue(pending.isLocal)
        assertEquals("", vm.state.value.draft)

        repo.sendResult!!.complete(Result.success(msg("9", "2026-11-10T11:00:00-05:00", mine = true, body = "Hola")))
        val entry = vm.state.value.entries.single()
        assertEquals("9", entry.id)
        assertEquals(BubbleState.Sent, entry.state)
    }

    @Test fun `fallo de red deja la burbuja Failed y reintentar la envia`() = runTest {
        val repo = FakeNegotiation().apply { sendImmediate = Result.failure(ApiException(0, null, "Sin conexión")) }
        val vm = vm(repo)
        vm.onDraft("Hola"); vm.send()
        val failed = vm.state.value.entries.single()
        assertEquals(BubbleState.Failed, failed.state)
        assertEquals("Sin conexión", vm.state.value.message?.text)

        repo.sendImmediate = Result.success(msg("9", "2026-11-10T11:00:00-05:00", mine = true, body = "Hola"))
        vm.retry(failed.id)
        assertEquals(listOf("9"), vm.state.value.entries.map { it.id })
        assertEquals(listOf("Hola", "Hola"), repo.sent)
    }

    @Test fun `descartar quita la burbuja fallida`() = runTest {
        val repo = FakeNegotiation().apply { sendImmediate = Result.failure(ApiException(0, null, "Sin conexión")) }
        val vm = vm(repo)
        vm.onDraft("Hola"); vm.send()
        vm.discard(vm.state.value.entries.single().id)
        assertTrue(vm.state.value.entries.isEmpty())
    }

    @Test fun `RN07 no agrega burbuja, devuelve el texto al campo y muestra el error bajo el campo`() = runTest {
        val text = "No puedes compartir teléfonos, correos ni enlaces. Coordina todo por el chat de LOGYX."
        val repo = FakeNegotiation().apply { sendImmediate = Result.failure(ApiException(422, "LOGYX-NEG-001", text, field = "body")) }
        val vm = vm(repo)
        vm.onDraft("llámame al 999 888 777"); vm.send()
        assertTrue(vm.state.value.entries.isEmpty())
        assertEquals("llámame al 999 888 777", vm.state.value.draft)
        assertEquals(text, vm.state.value.composerError)
        assertNull(vm.state.value.message)
        vm.onDraft("llámame al 999 888 77")
        assertNull(vm.state.value.composerError)
    }

    @Test fun `hilo cerrado 409 deshabilita el campo`() = runTest {
        val repo = FakeNegotiation().apply { sendImmediate = Result.failure(ApiException(409, "LOGYX-NEG-002", "La conversación está cerrada")) }
        val vm = vm(repo)
        vm.onDraft("Hola"); vm.send()
        assertTrue(vm.state.value.readOnly)
        assertEquals("La conversación está cerrada", vm.state.value.readOnlyReason)
        assertTrue(vm.state.value.entries.isEmpty())
        vm.onDraft("otro"); vm.send()
        assertEquals(1, repo.sent.size)
    }

    @Test fun `polling fusiona por id sin duplicar y conserva los optimistas`() = runTest {
        val repo = FakeNegotiation().apply {
            latest = CursorPage(listOf(msg("1", "2026-11-10T09:00:00-05:00")), null)
            sendResult = CompletableDeferred()
        }
        val vm = vm(repo)
        vm.onDraft("Hola"); vm.send()
        repo.latest = CursorPage(listOf(msg("1", "2026-11-10T09:00:00-05:00"), msg("2", "2026-11-10T09:30:00-05:00")), null)
        vm.pollOnce()
        vm.pollOnce()
        assertEquals(listOf("1", "2"), vm.state.value.entries.filter { !it.isLocal }.map { it.id })
        assertEquals(BubbleState.Sending, vm.state.value.entries.last().state)

        // El servidor ya devuelve el mensaje enviado: no se duplica al llegar la respuesta del POST.
        val real = msg("3", "2026-11-10T09:40:00-05:00", mine = true, body = "Hola")
        repo.latest = CursorPage(listOf(msg("1", "2026-11-10T09:00:00-05:00"), msg("2", "2026-11-10T09:30:00-05:00"), real), null)
        vm.pollOnce()
        repo.sendResult!!.complete(Result.success(real))
        assertEquals(listOf("1", "2", "3"), vm.state.value.entries.map { it.id })
    }
}
