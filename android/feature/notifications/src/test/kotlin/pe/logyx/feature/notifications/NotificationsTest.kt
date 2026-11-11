package pe.logyx.feature.notifications

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.logyx.core.model.RealtimeEvent
import pe.logyx.core.model.RealtimeTypes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.NotificationsRepository
import pe.logyx.core.model.NotificationType
import pe.logyx.core.model.NotificationView
import pe.logyx.core.model.Page
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun notif(i: Int, read: Boolean = false) =
    NotificationView("n$i", NotificationType.NEW_BID, "T$i", "B$i", read = read, createdAt = "2026-11-10T10:00:00Z")

private class FakeNotifications(var all: List<NotificationView>) : NotificationsRepository {
    var failMarkRead = false
    var failUnreadCount = false
    var failMarkAll = false
    val listCalls = mutableListOf<Pair<Boolean, Int>>()

    override suspend fun list(unreadOnly: Boolean, page: Int): Result<Page<NotificationView>> {
        listCalls += unreadOnly to page
        val src = if (unreadOnly) all.filter { !it.read } else all
        return Result.success(Page(src.drop(page * 20).take(20), page, 20, src.size))
    }
    override suspend fun unreadCount(): Result<Int> =
        if (failUnreadCount) Result.failure(IOException("sin red")) else Result.success(all.count { !it.read })
    override suspend fun markRead(id: String): Result<NotificationView> {
        if (failMarkRead) return Result.failure(IOException("sin red"))
        all = all.map { if (it.id == id) it.copy(read = true) else it }
        return Result.success(all.first { it.id == id })
    }
    override suspend fun markAllRead(): Result<Int> {
        if (failMarkAll) return Result.failure(IOException("sin red"))
        val n = all.count { !it.read }
        all = all.map { it.copy(read = true) }
        return Result.success(n)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: FakeNotifications, counter: UnreadCounter = UnreadCounter(repo)) = NotificationsViewModel(repo, counter)

    @Test fun counterRefreshKeepsLastValueOnFailure() = runTest {
        val repo = FakeNotifications(listOf(notif(1), notif(2), notif(3, read = true)))
        val c = UnreadCounter(repo)
        c.refresh()
        assertEquals(2, c.count.value)
        repo.failUnreadCount = true
        c.refresh()
        assertEquals(2, c.count.value)
        c.decrement(); c.decrement(); c.decrement()
        assertEquals(0, c.count.value)
        c.set(5)
        assertEquals(5, c.count.value)
    }

    @Test fun badgeUsaUnreadCountDelEventoSinConsultarYResyncConsulta() = runTest {
        val repo = FakeNotifications(listOf(notif(1), notif(2)))
        val c = UnreadCounter(repo)
        c.onSignal(RealtimeEvent(RealtimeTypes.NOTIFICATION_CREATED, data = JsonObject(mapOf("unreadCount" to JsonPrimitive(7)))))
        assertEquals(7, c.count.value)
        // Un resync (o el tick de respaldo, null) no trae contador: consulta al backend.
        c.onSignal(RealtimeEvent(RealtimeTypes.STREAM_RESYNC))
        assertEquals(2, c.count.value)
        c.onSignal(RealtimeEvent(RealtimeTypes.NOTIFICATION_CREATED, data = JsonObject(mapOf("unreadCount" to JsonPrimitive(3)))))
        c.onSignal(null)
        assertEquals(2, c.count.value)
    }

    @Test fun loadsFirstPageAndUpdatesCounter() {
        val repo = FakeNotifications((1..5).map { notif(it) })
        val counter = UnreadCounter(repo)
        val m = vm(repo, counter)
        assertEquals(5, m.state.value.items.size)
        assertEquals(false, m.state.value.loading)
        assertEquals(5, counter.count.value)
    }

    @Test fun markReadIsOptimisticAndDecrementsCounter() {
        val repo = FakeNotifications(listOf(notif(1), notif(2)))
        val counter = UnreadCounter(repo)
        val m = vm(repo, counter)
        m.markRead(m.state.value.items[0])
        assertTrue(m.state.value.items[0].read)
        assertEquals(1, counter.count.value)
        assertTrue(repo.all[0].read)
    }

    @Test fun markReadRollsBackOnFailure() {
        val repo = FakeNotifications(listOf(notif(1), notif(2))).apply { failMarkRead = true }
        val counter = UnreadCounter(repo)
        val m = vm(repo, counter)
        m.markRead(m.state.value.items[0])
        assertEquals(false, m.state.value.items[0].read)
        assertEquals(2, counter.count.value)
        assertNotNull(m.state.value.message)
        m.onMessageShown()
        assertNull(m.state.value.message)
    }

    @Test fun markAllReadClearsEverything() {
        val repo = FakeNotifications(listOf(notif(1), notif(2)))
        val counter = UnreadCounter(repo)
        val m = vm(repo, counter)
        m.markAllRead()
        assertTrue(m.state.value.items.all { it.read })
        assertEquals(0, counter.count.value)
        assertNotNull(m.state.value.message)
        assertEquals(false, m.state.value.hasUnread)
    }

    @Test fun markAllFailureKeepsState() {
        val repo = FakeNotifications(listOf(notif(1))).apply { failMarkAll = true }
        val counter = UnreadCounter(repo)
        val m = vm(repo, counter)
        m.markAllRead()
        assertEquals(false, m.state.value.items[0].read)
        assertEquals(1, counter.count.value)
        assertEquals(false, m.state.value.markingAll)
        assertNotNull(m.state.value.message)
    }

    @Test fun paginatesWithoutDuplicates() {
        val repo = FakeNotifications((1..45).map { notif(it) })
        val m = vm(repo)
        assertEquals(20, m.state.value.items.size)
        assertTrue(m.state.value.hasMore)
        m.loadMore()
        assertEquals(40, m.state.value.items.size)
        m.loadMore()
        assertEquals(45, m.state.value.items.size)
        assertEquals(false, m.state.value.hasMore)
        assertEquals(45, m.state.value.items.map { it.id }.toSet().size)
        m.loadMore()
        assertEquals(3, repo.listCalls.count { it.second > 0 } + 1)
    }

    @Test fun unreadFilterReloadsAndSetsCounter() {
        val repo = FakeNotifications(listOf(notif(1), notif(2, read = true), notif(3)))
        val counter = UnreadCounter(repo)
        val m = vm(repo, counter)
        m.setFilter(NotificationFilter.UNREAD)
        assertEquals(NotificationFilter.UNREAD, m.state.value.filter)
        assertEquals(listOf("n1", "n3"), m.state.value.items.map { it.id })
        assertEquals(true to 0, repo.listCalls.last())
        assertEquals(2, counter.count.value)
    }

    @Test fun silentPollKeepsListOnFailure() {
        val repo = FakeNotifications(listOf(notif(1)))
        val m = vm(repo)
        repo.all = repo.all + notif(2)
        m.poll()
        assertEquals(listOf("n1", "n2"), m.state.value.items.map { it.id })
    }
}
