package pe.logyx.feature.incidents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.components.AlertBanner
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.MessageKind
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.label
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.IncidentCategory
import pe.logyx.core.model.IncidentStatus
import pe.logyx.core.model.IncidentView

/** Incidencias de un envío. [onReport] null = el rol no puede reportar (oculta el botón). */
@Composable
fun IncidentsListRoute(
    shipmentId: String,
    onBack: () -> Unit,
    onOpenIncident: (String) -> Unit,
    onReport: (() -> Unit)?,
    viewModel: IncidentsListViewModel = koinViewModel { parametersOf(shipmentId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Al volver de "Reportar" o del detalle (que puede cambiar el estado) se actualiza en silencio.
    val first = remember { booleanArrayOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (first[0]) first[0] = false else viewModel.refresh()
    }
    IncidentsListScreen(state, onBack, viewModel::refresh, onOpenIncident, onReport)
}

@Composable
fun IncidentsListScreen(
    state: IncidentsListUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onReport: (() -> Unit)?,
) {
    LogyxScreen(stringResource(R.string.incidents_list_title), onBack = onBack) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList(Modifier.fillMaxSize())
                state.error != null && state.items.isEmpty() -> ErrorState(state.error, onRefresh)
                state.items.isEmpty() -> Refreshable(state.refreshing, onRefresh) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        EmptyState(
                            stringResource(R.string.incidents_empty_title), stringResource(R.string.incidents_empty_message),
                            actionLabel = onReport?.let { stringResource(R.string.incidents_empty_action) }, onAction = onReport,
                        )
                    }
                }
                else -> Refreshable(state.refreshing, onRefresh) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.hasActive) {
                            item {
                                AlertBanner(
                                    stringResource(R.string.incidents_blocked_text), kind = MessageKind.Warning,
                                    title = stringResource(R.string.incidents_blocked_title),
                                )
                            }
                        }
                        items(state.items, key = { it.id }) { IncidentRow(it) { onOpen(it.id) } }
                        if (!state.hasActive && onReport != null) {
                            item { LogyxButton(stringResource(R.string.incidents_report_button), onReport) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncidentRow(incident: IncidentView, onClick: () -> Unit) {
    LogyxCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(incident.category.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(incident.status.pill())
        }
        Text(Fmt.dateTime(incident.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        Text(
            incident.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private val sample = listOf(
    IncidentView("1", "s1", category = IncidentCategory.DELAY, description = "El camión lleva 3 horas detenido en el peaje de Pucusana por una fila de vehículos.", status = IncidentStatus.OPEN, active = true, createdAt = "2026-11-05T14:00:00-05:00"),
    IncidentView("2", "s1", category = IncidentCategory.CARGO_DAMAGE, description = "Una caja llegó golpeada.", status = IncidentStatus.RESOLVED, createdAt = "2026-10-20T09:30:00-05:00"),
)

@Preview(showBackground = true)
@Composable
private fun ListPreview() = LogyxTheme { IncidentsListScreen(IncidentsListUiState(loading = false, items = sample), {}, {}, {}, {}) }

@Preview(showBackground = true)
@Composable
private fun ListEmptyPreview() = LogyxTheme { IncidentsListScreen(IncidentsListUiState(loading = false), {}, {}, {}, {}) }

@Preview(showBackground = true)
@Composable
private fun ListErrorPreview() = LogyxTheme { IncidentsListScreen(IncidentsListUiState(loading = false, error = "No pudimos cargar las incidencias."), {}, {}, {}, {}) }

@Preview(showBackground = true)
@Composable
private fun ListLoadingPreview() = LogyxTheme { IncidentsListScreen(IncidentsListUiState(), {}, {}, {}, {}) }
