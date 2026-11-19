package pe.logyx.feature.notifications

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
import pe.logyx.core.data.NotificationsRepository
import pe.logyx.core.designsystem.components.MessageKind
import pe.logyx.core.model.NotificationCategory
import pe.logyx.core.model.NotificationPreferenceChange
import pe.logyx.core.model.NotificationPreferenceView
import pe.logyx.core.model.NotificationView
import pe.logyx.core.model.Page
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakePrefs(var prefs: List<NotificationPreferenceView>) : NotificationsRepository {
    var failLoad = false
    var failUpdate = false
    var gate: CompletableDeferred<Unit>? = null
    val updates = mutableListOf<List<NotificationPreferenceChange>>()

    override suspend fun list(unreadOnly: Boolean, page: Int, mineOnly: Boolean): Result<Page<NotificationView>> =
        Result.success(Page(emptyList(), 0, 20, 0))
    override suspend fun unreadCount(): Result<Int> = Result.success(0)
    override suspend fun markRead(id: String): Result<NotificationView> = Result.failure(UnsupportedOperationException())
    override suspend fun markAllRead(): Result<Int> = Result.success(0)
    override suspend fun preferences() = if (failLoad) Result.failure(IOException("sin red")) else Result.success(prefs)
    override suspend fun updatePreferences(changes: List<NotificationPreferenceChange>): Result<List<NotificationPreferenceView>> {
        updates += changes
        gate?.await()
        if (failUpdate) return Result.failure(IOException("sin red"))
        prefs = prefs.map { p -> changes.firstOrNull { it.category == p.category }?.let { p.copy(inApp = it.inApp, email = it.email) } ?: p }
        return Result.success(prefs)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPreferencesViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val base = listOf(
        NotificationPreferenceView(NotificationCategory.ALERTS, "Alertas de carga", inApp = true, email = false),
        NotificationPreferenceView(NotificationCategory.UNKNOWN, "Nueva", inApp = true, email = true),
        NotificationPreferenceView(NotificationCategory.ACCOUNT, "Cuenta y accesos", inApp = true, email = true, inAppLocked = true),
    )

    @Test fun `carga y oculta categorias desconocidas`() = runTest {
        val vm = NotificationPreferencesViewModel(FakePrefs(base))
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(listOf(NotificationCategory.ALERTS, NotificationCategory.ACCOUNT), s.items.map { it.category })
    }

    @Test fun `error al cargar sin datos muestra ErrorState`() = runTest {
        val vm = NotificationPreferencesViewModel(FakePrefs(base).apply { failLoad = true })
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test fun `cambiar el correo envia solo esa categoria y confirma`() = runTest {
        val repo = FakePrefs(base)
        val vm = NotificationPreferencesViewModel(repo)
        vm.toggle(NotificationCategory.ALERTS, NotificationChannel.EMAIL, true)
        assertEquals(listOf(listOf(NotificationPreferenceChange(NotificationCategory.ALERTS, inApp = true, email = true))), repo.updates)
        val s = vm.state.value
        assertTrue(s.items.first { it.category == NotificationCategory.ALERTS }.email)
        assertTrue(s.saving.isEmpty())
        assertEquals(MessageKind.Success, s.message?.kind)
    }

    @Test fun `cambio optimista y vuelta atras si falla`() = runTest {
        val repo = FakePrefs(base).apply { failUpdate = true; gate = CompletableDeferred() }
        val vm = NotificationPreferencesViewModel(repo)
        vm.toggle(NotificationCategory.ALERTS, NotificationChannel.IN_APP, false)
        // Mientras responde: ya se ve apagado y la fila queda bloqueada (un segundo toque no envía nada).
        assertFalse(vm.state.value.items.first { it.category == NotificationCategory.ALERTS }.inApp)
        vm.toggle(NotificationCategory.ALERTS, NotificationChannel.EMAIL, true)
        assertEquals(1, repo.updates.size)
        repo.gate!!.complete(Unit)
        val s = vm.state.value
        assertTrue(s.items.first { it.category == NotificationCategory.ALERTS }.inApp)
        assertEquals(MessageKind.Error, s.message?.kind)
        vm.onMessageShown()
        assertNull(vm.state.value.message)
    }

    @Test fun `in-app bloqueado no se puede apagar`() = runTest {
        val repo = FakePrefs(base)
        val vm = NotificationPreferencesViewModel(repo)
        vm.toggle(NotificationCategory.ACCOUNT, NotificationChannel.IN_APP, false)
        assertTrue(repo.updates.isEmpty())
        // El correo de esa categoría sí se puede cambiar (y el in-app viaja encendido).
        vm.toggle(NotificationCategory.ACCOUNT, NotificationChannel.EMAIL, false)
        assertEquals(NotificationPreferenceChange(NotificationCategory.ACCOUNT, inApp = true, email = false), repo.updates.single().single())
    }
}
