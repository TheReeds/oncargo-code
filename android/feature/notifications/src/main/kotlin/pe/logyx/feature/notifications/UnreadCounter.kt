package pe.logyx.feature.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.compose.koinInject
import pe.logyx.core.data.NotificationsRepository
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.data.refreshSignals
import pe.logyx.core.model.RealtimeEvent
import pe.logyx.core.model.RealtimeTypes

/** Contador de notificaciones sin leer, compartido entre el badge de la barra inferior y el centro de notificaciones. */
class UnreadCounter(private val repo: NotificationsRepository) {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    /** Consulta el backend; si falla (sin red, etc.) conserva el último valor. */
    suspend fun refresh() {
        repo.unreadCount().onSuccess { _count.value = it.coerceAtLeast(0) }
    }

    /**
     * Reacciona a una señal de tiempo real: `notification.created` trae `unreadCount` (se usa directo, sin pedir nada);
     * un resync o un tick de respaldo (`null`) consultan al backend.
     */
    suspend fun onSignal(event: RealtimeEvent?) {
        val n = event?.takeIf { it.type == RealtimeTypes.NOTIFICATION_CREATED }?.long("unreadCount")
        if (n != null) set(n.toInt()) else refresh()
    }

    fun set(n: Int) { _count.value = n.coerceAtLeast(0) }

    fun decrement(by: Int = 1) { _count.update { (it - by).coerceAtLeast(0) } }

    fun increment(by: Int = 1) { _count.update { it + by } }

    /** Al cerrar sesión. */
    fun clear() { _count.value = 0 }
}

/** Respaldo cuando el stream no está en vivo (con el stream en vivo no hay polling). */
const val UNREAD_POLL_MS = 60_000L

/**
 * Contador de no leídas para el badge de la barra inferior. Se actualiza por tiempo real (`notification.created` trae `unreadCount`), solo con la app en primer plano;
 * refresca al volver a ella y hace polling de respaldo (60 s) solo si el stream no está en vivo. Con `enabled = false` (p. ej. conductor o sin sesión) no consulta nada.
 */
@Composable
fun rememberUnreadCount(enabled: Boolean = true): State<Int> {
    val counter = koinInject<UnreadCounter>()
    val realtime = koinInject<RealtimeRepository>()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(enabled, owner, counter, realtime) {
        if (enabled) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                counter.refresh()
                realtime.refreshSignals(setOf(RealtimeTypes.NOTIFICATION_CREATED), UNREAD_POLL_MS).collect { counter.onSignal(it) }
            }
        }
    }
    return counter.count.collectAsStateWithLifecycle()
}
