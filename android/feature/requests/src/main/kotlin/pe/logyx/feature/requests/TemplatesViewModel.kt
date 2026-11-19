package pe.logyx.feature.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.RequestsRepository
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.model.TemplateView
import pe.logyx.core.network.ApiException
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** «Publicar ahora» de una plantilla: la plantilla elegida y la fecha de recojo. */
data class PublishDraft(
    val template: TemplateView,
    val date: LocalDate? = null,
    val error: UiText? = null,
    val sending: Boolean = false,
)

data class TemplatesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<TemplateView> = emptyList(),
    val publish: PublishDraft? = null,
    val createdId: String? = null,
    val message: UiMessage? = null,
)

/** Plantillas de solicitud de la PYME (roadmap 2.1): la lista y «Publicar ahora» con la fecha de recojo. La programación se edita en la web. */
class TemplatesViewModel(
    private val repo: RequestsRepository,
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.of("America/Lima")) },
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val _state = MutableStateFlow(TemplatesUiState())
    val state: StateFlow<TemplatesUiState> = _state.asStateFlow()
    /** Una clave por apertura de la hoja: reintentar tras fallar la red no publica dos veces. */
    private var key = newKey()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.items.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            repo.templates().fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, refreshing = false, items = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = if (it.items.isEmpty()) e.userMessage() else null,
                    message = if (it.items.isEmpty()) null else UiMessage.error(e.userMessage())) } },
            )
        }
    }

    fun askPublish(t: TemplateView) { key = newKey(); _state.update { it.copy(publish = PublishDraft(t)) } }
    fun onDate(d: LocalDate) = _state.update { s -> s.copy(publish = s.publish?.copy(date = d, error = null)) }
    fun closePublish() = _state.update { if (it.publish?.sending == true) it else it.copy(publish = null) }
    fun onMessageShown() = _state.update { it.copy(message = null) }
    fun navigationHandled() = _state.update { it.copy(createdId = null) }

    fun confirmPublish() {
        val p = _state.value.publish ?: return
        if (p.sending) return
        val date = p.date
        when {
            date == null -> { _state.update { it.copy(publish = p.copy(error = UiText.of(R.string.requests_templates_err_date))) }; return }
            date.isBefore(today()) -> { _state.update { it.copy(publish = p.copy(error = UiText.of(R.string.requests_templates_err_date_past))) }; return }
        }
        _state.update { it.copy(publish = p.copy(sending = true, error = null)) }
        viewModelScope.launch {
            repo.publishTemplate(p.template.id, date.toString(), key).fold(
                onSuccess = { r -> _state.update { it.copy(publish = null, createdId = r.requestId) } },
                onFailure = { e ->
                    _state.update {
                        if ((e as? ApiException)?.field == "pickupDate") it.copy(publish = p.copy(sending = false, error = UiText.Raw(e.userMessage())))
                        else it.copy(publish = null, message = UiMessage.error(e.userMessage()))
                    }
                },
            )
        }
    }
}
