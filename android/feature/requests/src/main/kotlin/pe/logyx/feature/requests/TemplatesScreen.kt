package pe.logyx.feature.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LogyxBottomSheet
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxDateField
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.PillTone
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.asString
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.label
import pe.logyx.core.model.TemplateView
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TemplatesRoute(onBack: () -> Unit, onPublished: (requestId: String) -> Unit, viewModel: TemplatesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdId) { state.createdId?.let { viewModel.navigationHandled(); onPublished(it) } }
    TemplatesScreen(state, onBack, viewModel::refresh, viewModel::askPublish, viewModel::onDate, viewModel::confirmPublish, viewModel::closePublish, viewModel::onMessageShown)
}

@Composable
fun TemplatesScreen(
    state: TemplatesUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPublish: (TemplateView) -> Unit,
    onDate: (java.time.LocalDate) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    LogyxScreen(stringResource(R.string.requests_templates_title), onBack = onBack, snackbar = snackbar) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList(Modifier.fillMaxSize())
                state.error != null -> ErrorState(state.error, onRefresh)
                state.items.isEmpty() -> EmptyState(stringResource(R.string.requests_templates_empty_title), stringResource(R.string.requests_templates_empty_message))
                else -> Refreshable(state.refreshing, onRefresh) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.items, key = { it.id }) { TemplateCard(it) { onPublish(it) } }
                    }
                }
            }
        }
        state.publish?.let { p ->
            LogyxBottomSheet(onDismiss = onClose, title = stringResource(R.string.requests_templates_publish_title)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(R.string.requests_templates_publish_message, p.template.name),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LogyxDateField(stringResource(R.string.requests_new_date), p.date, onDate, error = p.error?.asString())
                    LogyxButton(stringResource(R.string.requests_templates_publish_confirm), onConfirm, variant = ButtonVariant.Money, loading = p.sending, requiresOperate = true)
                    LogyxButton(stringResource(R.string.requests_dismiss_back), onClose, variant = ButtonVariant.Ghost, size = ButtonSize.Medium, enabled = !p.sending)
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(t: TemplateView, onPublish: () -> Unit) {
    LogyxCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${cityLabel(t.originCity)} → ${cityLabel(t.destinationCity)} · ${t.cargoType.label} · ${Fmt.tons(t.weightKg)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val s = t.schedule
            if (s != null) {
                val day = DayOfWeek.of(s.pickupWeekday.coerceIn(1, 7)).getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-PE"))
                val every = stringResource(if (s.frequency == "BIWEEKLY") R.string.requests_templates_every_two else R.string.requests_templates_every, day)
                val (label, tone) = when {
                    s.finished -> stringResource(R.string.requests_templates_state_finished) to PillTone.Neutral
                    s.paused -> stringResource(R.string.requests_templates_state_paused) to PillTone.Amber
                    s.mode == "DRAFT" -> stringResource(R.string.requests_templates_state_draft) to PillTone.Petrol
                    else -> stringResource(R.string.requests_templates_state_auto) to PillTone.Emerald
                }
                StatusPill(label, tone)
                Text(every, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!s.paused && !s.finished && s.nextPickupDate != null) {
                    Text(stringResource(R.string.requests_templates_next, Fmt.date(s.nextPickupDate)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LogyxButton(stringResource(R.string.requests_templates_publish_now), onPublish, variant = ButtonVariant.Money, size = ButtonSize.Medium, requiresOperate = true)
        }
    }
}
