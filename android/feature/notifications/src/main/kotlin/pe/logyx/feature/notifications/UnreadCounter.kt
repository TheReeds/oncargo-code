package pe.logyx.feature.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.compose.koinInject
import pe.logyx.core.data.NotificationsRepository

/** Contador de notificaciones sin leer, compartido entre el badge de la barra inferior y el centro de notificaciones. */
class UnreadCounter(private val repo: NotificationsRepository) {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    /** Consulta el backend; si falla (sin red, etc.) conserva el último valor. */
    suspend fun refresh() {
        repo.unreadCount().onSuccess { _count.value = it.coerceAtLeast(0) }
    }

    fun set(n: Int) { _count.value = n.coerceAtLeast(0) }

    fun decrement(by: Int = 1) { _count.update { (it - by).coerceAtLeast(0) } }

    fun increment(by: Int = 1) { _count.update { it + by } }

    /** Al cerrar sesión. */
    fun clear() { _count.value = 0 }
}

const val UNREAD_POLL_MS = 30_000L

/**
 * Contador de no leídas para el badge de la barra inferior. Hace polling cada ~30 s solo con la app en primer plano
 * (y refresca al volver a ella). Con `enabled = false` (p. ej. conductor o sin sesión) no consulta nada.
 */
@Composable
fun rememberUnreadCount(enabled: Boolean = true): State<Int> {
    val counter = koinInject<UnreadCounter>()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(enabled, owner, counter) {
        if (enabled) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    counter.refresh()
                    // Tiempo real (no el reloj virtual de los tests de Compose, que convertiría esto en un bucle caliente).
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { delay(UNREAD_POLL_MS) }
                }
            }
        }
    }
    return counter.count.collectAsStateWithLifecycle()
}
