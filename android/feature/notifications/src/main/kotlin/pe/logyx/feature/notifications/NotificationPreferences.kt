package pe.logyx.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.logyx.core.data.NotificationsRepository
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.LogyxSwitchRow
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.model.NotificationCategory
import pe.logyx.core.model.NotificationPreferenceView
import pe.logyx.core.model.toChange

/** Canal de un aviso: en la app (centro de avisos) o por correo. */
enum class NotificationChannel { IN_APP, EMAIL }

data class NotificationPreferencesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<NotificationPreferenceView> = emptyList(),
    /** Categorías con un cambio en vuelo (su fila no se puede volver a tocar hasta que responda el servidor). */
    val saving: Set<NotificationCategory> = emptySet(),
    val message: UiMessage? = null,
)

/**
 * Preferencias de aviso (V16): `GET /notifications/preferences` y un `PUT` por cada interruptor (solo la categoría
 * tocada). Cambio optimista: si el servidor lo rechaza (`NTF-001/002`, red) se vuelve al valor anterior y se explica.
 */
class NotificationPreferencesViewModel(private val repo: NotificationsRepository) : ViewModel() {
    private val _state = MutableStateFlow(NotificationPreferencesUiState())
    val state: StateFlow<NotificationPreferencesUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()

    private fun load() {
        _state.update { it.copy(loading = it.items.isEmpty(), refreshing = it.items.isNotEmpty(), error = null) }
        viewModelScope.launch {
            repo.preferences().fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, refreshing = false, items = list.visible()) } },
                onFailure = { e ->
                    _state.update {
                        if (it.items.isEmpty()) it.copy(loading = false, refreshing = false, error = e.userMessage())
                        else it.copy(refreshing = false, message = UiMessage.error(e.userMessage()))
                    }
                },
            )
        }
    }

    fun toggle(category: NotificationCategory, channel: NotificationChannel, enabled: Boolean) {
        val current = _state.value
        if (category in current.saving) return
        val before = current.items.firstOrNull { it.category == category } ?: return
        if (channel == NotificationChannel.IN_APP && before.inAppLocked) return
        val after = when (channel) {
            NotificationChannel.IN_APP -> before.copy(inApp = enabled)
            NotificationChannel.EMAIL -> before.copy(email = enabled)
        }
        val change = after.toChange() ?: return
        _state.update { s -> s.copy(items = s.items.replace(after), saving = s.saving + category) }
        viewModelScope.launch {
            repo.updatePreferences(listOf(change)).fold(
                onSuccess = { list ->
                    val fresh = list.visible()
                    _state.update { s ->
                        s.copy(
                            // El PUT devuelve todas las categorías; si viniera vacío, se conserva lo optimista.
                            items = if (fresh.isEmpty()) s.items else fresh,
                            saving = s.saving - category,
                            message = UiMessage.success(R.string.notifications_prefs_saved),
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { s ->
                        s.copy(items = s.items.replace(before), saving = s.saving - category, message = UiMessage.error(e.userMessage()))
                    }
                },
            )
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun List<NotificationPreferenceView>.visible() = filter { it.category != NotificationCategory.UNKNOWN }
    private fun List<NotificationPreferenceView>.replace(p: NotificationPreferenceView) = map { if (it.category == p.category) p else it }
}

@Composable
fun NotificationPreferencesRoute(onBack: () -> Unit, vm: NotificationPreferencesViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    NotificationPreferencesScreen(state, onBack, vm::refresh, vm::toggle, vm::onMessageShown)
}

@Composable
fun NotificationPreferencesScreen(
    state: NotificationPreferencesUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (NotificationCategory, NotificationChannel, Boolean) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    LogyxScreen(title = stringResource(R.string.notifications_prefs_title), onBack = onBack, snackbar = snackbar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> SkeletonList(count = 4)
                state.error != null -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh, Modifier.weight(1f)) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                stringResource(R.string.notifications_prefs_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(state.items, key = { it.category.name }) { p ->
                            PreferenceCard(p, saving = p.category in state.saving, onToggle = onToggle)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceCard(
    p: NotificationPreferenceView,
    saving: Boolean,
    onToggle: (NotificationCategory, NotificationChannel, Boolean) -> Unit,
) {
    LogyxCard(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.label, style = MaterialTheme.typography.titleMedium)
            p.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
            LogyxSwitchRow(
                label = stringResource(R.string.notifications_prefs_in_app),
                checked = p.inApp || p.inAppLocked,
                onCheckedChange = { onToggle(p.category, NotificationChannel.IN_APP, it) },
                modifier = Modifier.padding(top = 6.dp),
                supporting = if (p.inAppLocked) stringResource(R.string.notifications_prefs_locked) else null,
                enabled = !p.inAppLocked && !saving,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LogyxSwitchRow(
                label = stringResource(R.string.notifications_prefs_email),
                checked = p.email,
                onCheckedChange = { onToggle(p.category, NotificationChannel.EMAIL, it) },
                enabled = !saving,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationPreferencesPreview() {
    LogyxTheme {
        NotificationPreferencesScreen(
            NotificationPreferencesUiState(
                loading = false,
                items = listOf(
                    NotificationPreferenceView(NotificationCategory.ALERTS, "Alertas de carga", "Cargas nuevas que coinciden con tus búsquedas guardadas.", inApp = true, email = true),
                    NotificationPreferenceView(NotificationCategory.SHIPMENTS, "Envíos", "Recojo, entrega, cancelaciones y ajustes del precio.", inApp = true, email = false),
                    NotificationPreferenceView(NotificationCategory.ACCOUNT, "Cuenta y accesos", "Verificación de tu empresa.", inApp = true, email = true, inAppLocked = true),
                ),
            ),
            onBack = {}, onRefresh = {}, onToggle = { _, _, _ -> }, onMessageShown = {},
        )
    }
}
