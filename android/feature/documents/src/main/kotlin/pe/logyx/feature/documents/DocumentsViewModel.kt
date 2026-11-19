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
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.DocumentView

data class DocumentsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val documents: List<DocumentView> = emptyList(),
    val message: UiMessage? = null,
    /** Subiendo un documento (guía, factura…): el botón «Subir documento» queda en curso. */
    val uploading: Boolean = false,
)

/** Documentos que sube la PYME o el transportista (la evidencia de recojo y entrega es solo del conductor). */
val UPLOADABLE_TYPES = listOf(DocumentType.REMISSION_GUIDE, DocumentType.RECEIPT, DocumentType.SERVICE_ORDER, DocumentType.OTHER)

/** Tamaño máximo que acepta el servidor (`logyx.documents.max-size-bytes` = 10 MB); las fotos se comprimen antes de subir. */
const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024
val UPLOAD_MIME_TYPES = arrayOf("image/jpeg", "image/png", "application/pdf")

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

    /**
     * Sube una guía, factura u otro documento del envío (roadmap 2.16). Se valida el formato y el tamaño antes de enviar; el
     * servidor revalida por firma binaria. Las fotos JPEG se comprimen en `DocumentsRepository.upload`.
     */
    fun upload(bytes: ByteArray?, fileName: String, mime: String?, type: DocumentType) {
        if (_state.value.uploading) return
        when {
            bytes == null || bytes.isEmpty() -> return showError(UiMessage.error(R.string.docs_upload_unreadable))
            mime == null || mime !in UPLOAD_MIME_TYPES -> return showError(UiMessage.error(R.string.docs_upload_format))
            bytes.size > MAX_UPLOAD_BYTES && mime != "image/jpeg" -> return showError(UiMessage.error(R.string.docs_upload_too_big))
        }
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            repo.upload(shipmentId, bytes!!, fileName, mime!!, type).fold(
                onSuccess = {
                    _state.update { s -> s.copy(uploading = false, message = UiMessage.success(R.string.docs_upload_done)) }
                    load(initial = false)
                },
                onFailure = { e -> _state.update { it.copy(uploading = false, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    fun showError(message: UiMessage) = _state.update { it.copy(message = message) }
    fun onMessageShown() = _state.update { it.copy(message = null) }
}
