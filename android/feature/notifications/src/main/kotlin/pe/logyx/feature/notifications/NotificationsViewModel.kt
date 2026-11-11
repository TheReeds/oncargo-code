package pe.logyx.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.NotificationsRepository
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.model.NotificationView

enum class NotificationFilter { ALL, UNREAD }

data class NotificationsUiState(
    val filter: NotificationFilter = NotificationFilter.ALL,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val markingAll: Boolean = false,
    val error: String? = null,
    val items: List<NotificationView> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val message: UiMessage? = null,
) {
    val hasUnread: Boolean get() = items.any { !it.read }
}

const val NOTIFICATIONS_PAGE_SIZE = 20

class NotificationsViewModel(
    private val repo: NotificationsRepository,
    private val counter: UnreadCounter,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    private var loadVersion = 0

    init { load(silent = false) }

    /** Pull-to-refresh o reintento. */
    fun refresh() = load(silent = false)

    /** Refresco periódico: no muestra spinners ni errores y conserva lo ya cargado. */
    fun poll() = load(silent = true)

    fun setFilter(filter: NotificationFilter) {
        if (filter == _state.value.filter) return
        loadVersion++
        _state.update { it.copy(filter = filter, items = emptyList(), page = 0, hasMore = false, loading = true, refreshing = false, loadingMore = false, error = null) }
        load(silent = false)
    }

    private fun load(silent: Boolean) {
        val version = if (silent) loadVersion else ++loadVersion
        val s = _state.value
        if (!silent) {
            _state.update { it.copy(loading = it.items.isEmpty(), refreshing = it.items.isNotEmpty(), loadingMore = false, error = null) }
        }
        viewModelScope.launch {
            repo.list(unreadOnly = s.filter == NotificationFilter.UNREAD, page = 0).fold(
                onSuccess = { p ->
                    if (version != loadVersion) return@fold
                    _state.update { cur ->
                        val fresh = p.items
                        val merged = if (cur.page == 0 || cur.items.isEmpty()) fresh else {
                            val ids = fresh.mapTo(HashSet()) { it.id }
                            fresh + cur.items.filter { it.id !in ids }
                        }
                        cur.copy(
                            loading = false, refreshing = false, error = null, items = merged,
                            page = if (cur.page == 0 || cur.items.isEmpty()) p.page else cur.page,
                            hasMore = if (cur.page == 0 || cur.items.isEmpty()) p.hasMore else cur.hasMore,
                        )
                    }
                    if (s.filter == NotificationFilter.UNREAD) counter.set(p.total) else counter.refresh()
                },
                onFailure = { e ->
                    if (version != loadVersion) return@fold
                    if (silent) return@fold
                    _state.update {
                        // Si ya hay lista, el fallo de un refresh se avisa con mensaje y no tapa el contenido.
                        if (it.items.isNotEmpty()) it.copy(loading = false, refreshing = false, message = UiMessage.error(e.userMessage()))
                        else it.copy(loading = false, refreshing = false, error = e.userMessage())
                    }
                },
            )
        }
    }

    /** Siguiente página (scroll infinito), sin duplicar elementos. */
    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.loadingMore || s.loading || s.refreshing || s.error != null) return
        val version = loadVersion
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            repo.list(unreadOnly = s.filter == NotificationFilter.UNREAD, page = s.page + 1).fold(
                onSuccess = { p ->
                    if (version != loadVersion) return@fold
                    _state.update {
                        val known = it.items.mapTo(HashSet()) { n -> n.id }
                        it.copy(loadingMore = false, items = it.items + p.items.filter { n -> known.add(n.id) }, page = p.page, hasMore = p.hasMore)
                    }
                },
                onFailure = { e ->
                    if (version != loadVersion) return@fold
                    _state.update { it.copy(loadingMore = false, hasMore = false, message = UiMessage.error(e.userMessage())) }
                },
            )
        }
    }

    /** Marca como leída de forma optimista; si falla, revierte y avisa. */
    fun markRead(notification: NotificationView) {
        val id = notification.id
        val target = _state.value.items.firstOrNull { it.id == id } ?: return
        if (target.read) return
        setRead(id, true)
        counter.decrement()
        viewModelScope.launch {
            repo.markRead(id).onFailure { e ->
                setRead(id, false)
                counter.increment()
                _state.update { it.copy(message = UiMessage.error(e.userMessage())) }
            }
        }
    }

    private fun setRead(id: String, read: Boolean) {
        _state.update { s -> s.copy(items = s.items.map { if (it.id == id) it.copy(read = read) else it }) }
    }

    fun markAllRead() {
        if (_state.value.markingAll) return
        _state.update { it.copy(markingAll = true) }
        viewModelScope.launch {
            repo.markAllRead().fold(
                onSuccess = {
                    counter.set(0)
                    _state.update { s ->
                        s.copy(
                            markingAll = false, items = s.items.map { it.copy(read = true) },
                            message = UiMessage.success(R.string.notifications_all_read_done),
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(markingAll = false, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}
