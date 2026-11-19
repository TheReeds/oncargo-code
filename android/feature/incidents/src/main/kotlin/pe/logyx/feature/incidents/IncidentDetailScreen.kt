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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.components.AlertBanner
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.LogyxBadge
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.PillTone
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.components.ChatBubble
import pe.logyx.core.designsystem.components.ChatComposer
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LiveEffect
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


/** Detalle de una incidencia con su hilo de comentarios (tiempo real en primer plano). */
@Composable
fun IncidentDetailRoute(
    incidentId: String,
    onBack: () -> Unit,
    viewModel: IncidentDetailViewModel = koinViewModel { parametersOf(incidentId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiveEffect(viewModel) { viewModel.liveLoop() }
    IncidentDetailScreen(
        state, onBack, viewModel::refresh, viewModel::onDraft, viewModel::send, viewModel::onMessageShown,
        onAskWithdraw = viewModel::askWithdraw, onConfirmWithdraw = viewModel::withdraw, onDismissWithdraw = viewModel::dismissWithdraw,
    )
}

@Composable
fun IncidentDetailScreen(
    state: IncidentDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onMessageShown: () -> Unit,
    onAskWithdraw: () -> Unit = {},
    onConfirmWithdraw: () -> Unit = {},
    onDismissWithdraw: () -> Unit = {},
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    WithdrawDialog(state, onConfirmWithdraw, onDismissWithdraw)
    LogyxScreen(
        stringResource(R.string.incidents_detail_title), onBack = onBack, snackbar = snackbar,
        bottomBar = {
            if (state.incident != null && state.canComment) {
                ChatComposer(
                    value = state.draft, onValueChange = onDraft, onSend = onSend,
                    error = state.commentError, sending = state.sending, maxLength = MAX_INCIDENT_COMMENT,
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
                        if (state.canWithdraw) {
                            item {
                                LogyxButton(
                                    stringResource(R.string.incidents_withdraw), onAskWithdraw, variant = ButtonVariant.Secondary,
                                    size = ButtonSize.Medium, loading = state.withdrawing, requiresOperate = true,
                                )
                            }
                        }
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
private fun WithdrawDialog(state: IncidentDetailUiState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (!state.confirmingWithdraw) return
    LogyxConfirmDialog(
        title = stringResource(R.string.incidents_withdraw_title),
        message = stringResource(R.string.incidents_withdraw_message),
        confirmLabel = stringResource(R.string.incidents_withdraw),
        dismissLabel = stringResource(R.string.incidents_withdraw_back),
        onConfirm = onConfirm, onDismiss = onDismiss, destructive = true, loading = state.withdrawing,
    )
}

/** Severidad, «Informativa» (no congela, RN11) y plazo vencido. */
@Composable
internal fun IncidentTags(incident: IncidentView, modifier: Modifier = Modifier) {
    val severity = incident.severity
    if (severity == null && incident.blocking && !incident.slaBreached) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        severity?.let { StatusPill(it.pill()) }
        if (!incident.blocking) LogyxBadge(stringResource(R.string.incidents_informative_short), tone = PillTone.Petrol)
        if (incident.slaBreached && incident.active) LogyxBadge(stringResource(R.string.incidents_sla_breached), tone = PillTone.Amber)
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
        IncidentTags(incident, Modifier.padding(top = 8.dp))
        when {
            // RN11 (V23): las informativas no detienen el viaje; las que congelan, mientras estén activas.
            !incident.blocking && incident.active ->
                AlertBanner(stringResource(R.string.incidents_informative), kind = MessageKind.Info, modifier = Modifier.padding(top = 12.dp))
            incident.holdsShipment || (incident.active && incident.blocking) ->
                AlertBanner(stringResource(R.string.incidents_holds_shipment), kind = MessageKind.Warning, modifier = Modifier.padding(top = 12.dp))
        }
        incident.withdrawnAt?.let {
            Text(stringResource(R.string.incidents_withdrawn_at, Fmt.dateTime(it)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        incident.claimedAmount?.let { Amount(stringResource(R.string.incidents_claimed_amount), it) }
        incident.declaredValue?.let { Amount(stringResource(R.string.incidents_declared_value), it) }
        if (incident.attachments.isNotEmpty()) {
            Text(stringResource(R.string.incidents_attachments, incident.attachments.size), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        if (incident.latitude != null && incident.longitude != null) {
            Text(stringResource(R.string.incidents_location), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Text(stringResource(R.string.incidents_detail_description), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        Text(incident.description, style = MaterialTheme.typography.bodyMedium)
        incident.resolutionNotes?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.incidents_detail_resolution), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Amount(label: String, value: Double) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Fmt.money(value), style = LogyxText.moneySmall, color = LogyxTheme.colors.moneyText)
    }
}

private val sample = IncidentView(
    "1", "s1", requesterOrgId = "org-p", requesterName = "Ferretería Los Andes", carrierOrgId = "org-c", carrierName = "Transportes Inti",
    reportedByOrgId = "org-p", category = IncidentCategory.DELAY, description = "El camión lleva 3 horas detenido en el peaje.",
    status = IncidentStatus.IN_REVIEW, active = true, createdAt = "2026-11-05T14:00:00-05:00",
    severity = pe.logyx.core.model.IncidentSeverity.LOW, blocking = false, canWithdraw = true,
    comments = listOf(
        IncidentCommentView("c1", "org-p", "Ferretería Los Andes", "Sigue detenido.", "2026-11-05T14:10:00-05:00"),
        IncidentCommentView("c2", "op", "OnCargo", "Estamos contactando al conductor.", "2026-11-05T14:20:00-05:00"),
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
