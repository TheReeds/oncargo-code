package pe.logyx.feature.loads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.LoadsRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.model.MAX_SAVED_SEARCH_NAME
import pe.logyx.core.model.SavedSearch
import pe.logyx.core.model.SavedSearchRequest
import pe.logyx.core.model.canOperate

// ───────────────────────── Búsquedas guardadas (V22, roadmap 2.3) ─────────────────────────

data class SavedSearchesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<SavedSearch> = emptyList(),
    /** Solo con `OPERATE` (el Lector ve la lista pero no crea, pausa ni borra). */
    val canWrite: Boolean = true,
    /** Ids con un PUT en curso (el interruptor se deshabilita mientras tanto). */
    val busyIds: Set<String> = emptySet(),
    val saving: Boolean = false,
    /** Error al guardar, tal cual lo manda la API (`ALT-001/002/003`), bajo el campo de nombre. */
    val saveError: UiText? = null,
    /** Sube a `true` cuando se guardó una búsqueda: la hoja de nombre se cierra. */
    val saved: Boolean = false,
    /** Búsqueda pendiente de confirmar el borrado. */
    val deleting: SavedSearch? = null,
    val deleteBusy: Boolean = false,
    val message: UiMessage? = null,
)

class SavedSearchesViewModel(
    private val auth: AuthRepository,
    private val loads: LoadsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SavedSearchesUiState(canWrite = (auth.state.value as? SessionState.LoggedIn)?.user?.canOperate ?: false),
    )
    val state: StateFlow<SavedSearchesUiState> = _state.asStateFlow()

    init { load(refreshing = false) }

    fun refresh() = load(refreshing = true)

    private fun load(refreshing: Boolean) {
        _state.update { it.copy(loading = !refreshing && it.items.isEmpty(), refreshing = refreshing, error = null) }
        viewModelScope.launch {
            loads.savedSearches().fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, refreshing = false, items = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    /** Guarda una búsqueda nueva (`POST`). Con nombre en blanco o más largo que el tope no llama a la API. */
    fun save(name: String, filters: SavedSearchRequest) {
        val s = _state.value
        if (!s.canWrite || s.saving) return
        val clean = name.trim()
        if (clean.isEmpty()) {
            _state.update { it.copy(saveError = UiText.of(R.string.loads_saved_name_required)) }
            return
        }
        _state.update { it.copy(saving = true, saveError = null, saved = false) }
        viewModelScope.launch {
            loads.createSavedSearch(filters.copy(name = clean.take(MAX_SAVED_SEARCH_NAME), active = true)).fold(
                onSuccess = { created ->
                    _state.update {
                        it.copy(
                            saving = false, saved = true, items = listOf(created) + it.items.filter { x -> x.id != created.id },
                            message = UiMessage.success(R.string.loads_saved_created),
                        )
                    }
                },
                // ALT-001/002/003 vienen en español: se muestran tal cual.
                onFailure = { e -> _state.update { it.copy(saving = false, saveError = UiText.Raw(e.userMessage())) } },
            )
        }
    }

    /** Pausa o reanuda los avisos (`PUT` con el resto de la búsqueda sin cambios). */
    fun setActive(item: SavedSearch, active: Boolean) {
        val s = _state.value
        if (!s.canWrite || item.id in s.busyIds) return
        _state.update { it.copy(busyIds = it.busyIds + item.id) }
        viewModelScope.launch {
            loads.updateSavedSearch(item.id, item.toRequest(active)).fold(
                onSuccess = { updated ->
                    _state.update {
                        it.copy(
                            busyIds = it.busyIds - item.id, items = it.items.map { x -> if (x.id == updated.id) updated else x },
                            message = UiMessage.success(if (updated.active) R.string.loads_saved_resumed else R.string.loads_saved_paused),
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(busyIds = it.busyIds - item.id, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    fun askDelete(item: SavedSearch) { if (_state.value.canWrite) _state.update { it.copy(deleting = item) } }

    fun dismissDelete() { if (!_state.value.deleteBusy) _state.update { it.copy(deleting = null) } }

    fun confirmDelete() {
        val s = _state.value
        val item = s.deleting ?: return
        if (!s.canWrite || s.deleteBusy) return
        _state.update { it.copy(deleteBusy = true) }
        viewModelScope.launch {
            loads.deleteSavedSearch(item.id).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            deleteBusy = false, deleting = null, items = it.items.filter { x -> x.id != item.id },
                            message = UiMessage.success(R.string.loads_saved_deleted),
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(deleteBusy = false, deleting = null, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    /** Limpia el error y la marca de "guardada" al abrir o cerrar la hoja de nombre. */
    fun resetSave() = _state.update { it.copy(saveError = null, saved = false) }

    fun messageShown() = _state.update { it.copy(message = null) }
}

/**
 * Filtros de la pantalla de cargas listos para guardar: lo que el servidor ya filtra (origen, destino, fechas, tipo y el
 * peso máximo de «Mi vehículo»); el orden «Mejor pago» no es un filtro. Nulo si no hay ningún filtro (`ALT-001`).
 */
fun LoadsFeedUiState.toSavedSearchRequest(name: String = ""): SavedSearchRequest? {
    val f = toServerFilter()
    val request = SavedSearchRequest(
        name = name, originCity = f.originCity, destinationCity = f.destinationCity, cargoType = f.cargoType,
        pickupFrom = f.pickupFrom, pickupTo = f.pickupTo, maxWeightKg = f.maxWeightKg,
    )
    val any = request.originCity != null || request.destinationCity != null || request.cargoType != null ||
        request.pickupFrom != null || request.pickupTo != null || request.maxWeightKg != null
    return request.takeIf { any }
}

/** Nombre sugerido para la búsqueda: «Lima → Juliaca» (o solo un extremo); vacío si no hay ruta. */
fun LoadsFeedUiState.suggestedSearchName(): String {
    val f = toServerFilter()
    val origin = originCity?.name ?: f.originCity?.let { pe.logyx.core.designsystem.cityLabel(it) }
    val destination = destinationCity?.name
    return when {
        origin != null && destination != null -> "$origin → $destination"
        origin != null -> "Desde $origin"
        destination != null -> "Hacia $destination"
        else -> ""
    }.take(MAX_SAVED_SEARCH_NAME)
}

/** Une los avisos de una oferta enviada (`CAP-006`, `FLT-007/008`) en un solo texto; nulo si no hay. */
fun bidWarningsText(warnings: List<pe.logyx.core.model.BidWarning>): String? =
    warnings.mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }.distinct().joinToString(" ").ifEmpty { null }
