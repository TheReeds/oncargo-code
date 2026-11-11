package pe.logyx.feature.negotiation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.NegotiationRepository
import pe.logyx.core.data.userMessage
import pe.logyx.core.model.ChatThreadView

data class ThreadsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val threads: List<ChatThreadView> = emptyList(),
)

class ThreadsViewModel(private val requestId: String, private val repo: NegotiationRepository) : ViewModel() {
    private val _state = MutableStateFlow(ThreadsUiState())
    val state: StateFlow<ThreadsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.threads.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            repo.threads(requestId).fold(
                onSuccess = { list ->
                    _state.update { it.copy(loading = false, refreshing = false, threads = list.sortedByDescending { t -> t.lastMessageAt.orEmpty() }) }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}
