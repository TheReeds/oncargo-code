package pe.logyx.feature.incidents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.components.AlertBanner
import pe.logyx.core.designsystem.components.ChatBubble
import pe.logyx.core.designsystem.components.ChatComposer
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.MessageKind
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.label
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.IncidentCategory
import pe.logyx.core.model.IncidentCommentView
import pe.logyx.core.model.IncidentStatus
import pe.logyx.core.model.IncidentView

private const val POLL_MS = 20_000L

/** Detalle de una incidencia con su hilo de comentarios (polling ~20 s en primer plano). */
@Composable
fun IncidentDetailRoute(
    incidentId: String,
    onBack: () -> Unit,
    viewModel: IncidentDetailViewModel = koinViewModel { parametersOf(incidentId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(POLL_MS)
                viewModel.poll()
            }
        }
    }
    IncidentDetailScreen(state, onBack, viewModel::refresh, viewModel::onDraft, viewModel::send, viewModel::onMessageShown)
}

@Composable
fun IncidentDetailScreen(
    state: IncidentDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    LogyxScreen(
        stringResource(R.string.incidents_detail_title), onBack = onBack, snackbar = snackbar,
        bottomBar = {
            if (state.incident != null && state.canComment) {
                ChatComposer(
                    value = state.draft, onValueChange = onDraft, onSend = onSend,
                    error = state.commentError, sending = state.sending,
                    placeholder = stringResource(R.string.incidents_comment_placeholder),
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val incident = state.incident
            when {
                state.loading -> SkeletonList(Modifier.fillMaxSize())
                incident == null -> ErrorState(state.error ?: "", onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Summary(incident, state.organizationId) }
                        item {
                            Text(
                                stringResource(R.string.incidents_detail_thread), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        if (incident.comments.isEmpty()) {
                            item { Text(stringResource(R.string.incidents_detail_thread_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        items(incident.comments, key = { it.id }) { c ->
                            ChatBubble(
                                text = c.body, mine = state.organizationId != null && c.authorOrgId == state.organizationId,
                                time = Fmt.dateTime(c.createdAt), sender = c.authorName ?: stringResource(R.string.incidents_operator),
                            )
                        }
                        if (!state.canComment) {
                            item { AlertBanner(stringResource(R.string.incidents_detail_closed_notice), kind = MessageKind.Info, modifier = Modifier.padding(top = 8.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(incident: IncidentView, myOrgId: String?) {
    val you = stringResource(R.string.incidents_detail_you)
    val reporter = when (incident.reportedByOrgId) {
        null -> null
        myOrgId -> you
        incident.requesterOrgId -> incident.requesterName
        incident.carrierOrgId -> incident.carrierName
        else -> null
    }
    LogyxCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(incident.category.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(incident.status.pill())
        }
        Text(
            stringResource(R.string.incidents_detail_reported, Fmt.dateTime(incident.createdAt)),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp),
        )
        if (reporter != null) {
            Text(stringResource(R.string.incidents_detail_reported_by, reporter), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.incidents_detail_description), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        Text(incident.description, style = MaterialTheme.typography.bodyMedium)
        incident.resolutionNotes?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.incidents_detail_resolution), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private val sample = IncidentView(
    "1", "s1", requesterOrgId = "org-p", requesterName = "Ferretería Los Andes", carrierOrgId = "org-c", carrierName = "Transportes Inti",
    reportedByOrgId = "org-p", category = IncidentCategory.DELAY, description = "El camión lleva 3 horas detenido en el peaje.",
    status = IncidentStatus.IN_REVIEW, active = true, createdAt = "2026-11-05T14:00:00-05:00",
    comments = listOf(
        IncidentCommentView("c1", "org-p", "Ferretería Los Andes", "Sigue detenido.", "2026-11-05T14:10:00-05:00"),
        IncidentCommentView("c2", "op", "LOGYX", "Estamos contactando al conductor.", "2026-11-05T14:20:00-05:00"),
    ),
)

@Preview(showBackground = true)
@Composable
private fun DetailPreview() = LogyxTheme { IncidentDetailScreen(IncidentDetailUiState(loading = false, incident = sample, organizationId = "org-p"), {}, {}, {}, {}, {}) }

@Preview(showBackground = true)
@Composable
private fun DetailResolvedPreview() = LogyxTheme {
    IncidentDetailScreen(
        IncidentDetailUiState(loading = false, incident = sample.copy(status = IncidentStatus.RESOLVED, active = false, resolutionNotes = "Se reprogramó la entrega."), organizationId = "org-p"),
        {}, {}, {}, {}, {},
    )
}

@Preview(showBackground = true)
@Composable
private fun DetailLoadingPreview() = LogyxTheme { IncidentDetailScreen(IncidentDetailUiState(), {}, {}, {}, {}, {}) }

@Preview(showBackground = true)
@Composable
private fun DetailErrorPreview() = LogyxTheme { IncidentDetailScreen(IncidentDetailUiState(loading = false, error = "No pudimos cargar la incidencia."), {}, {}, {}, {}, {}) }
