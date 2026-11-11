package pe.logyx.feature.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.DocumentsRepository
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.model.DocumentView

data class DocumentsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val documents: List<DocumentView> = emptyList(),
    val message: UiMessage? = null,
)

class DocumentsViewModel(
    private val shipmentId: String,
    private val repo: DocumentsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DocumentsUiState())
    val state: StateFlow<DocumentsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)
    fun retry() = load(initial = true)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
        viewModelScope.launch {
            repo.list(shipmentId).fold(
                onSuccess = { docs ->
                    _state.update { it.copy(loading = false, refreshing = false, documents = docs.sortedByDescending { d -> d.createdAt.orEmpty() }) }
                },
                onFailure = { e ->
                    val hasData = _state.value.documents.isNotEmpty()
                    _state.update {
                        it.copy(
                            loading = false, refreshing = false,
                            error = if (hasData) null else e.userMessage(),
                            message = if (hasData) UiMessage.error(e.userMessage()) else null,
                        )
                    }
                },
            )
        }
    }

    /** Descarga el contenido de un documento (miniaturas, visor y PDF). */
    suspend fun fetch(documentId: String): Result<ByteArray> = repo.file(documentId)

    fun showError(message: UiMessage) = _state.update { it.copy(message = message) }
    fun onMessageShown() = _state.update { it.copy(message = null) }
}
