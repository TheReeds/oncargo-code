package pe.logyx.feature.shipments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.AdjustmentsRepository
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.data.fieldErrors
import pe.logyx.core.data.refreshSignals
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxReasonSheet
import pe.logyx.core.designsystem.components.LogyxSnackbarState
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.designsystem.components.asString
import pe.logyx.core.designsystem.label
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.AdjustmentStatus
import pe.logyx.core.model.AdjustmentSuggestionView
import pe.logyx.core.model.AdjustmentType
import pe.logyx.core.model.AdjustmentView
import pe.logyx.core.model.OrgType
import pe.logyx.core.model.PriceBreakdownView
import pe.logyx.core.model.ProposeAdjustmentRequest
import pe.logyx.core.model.RealtimeTypes
import pe.logyx.core.model.Role
import pe.logyx.core.model.ShipmentAdjustmentsView
import java.util.UUID

// Ola 2, zona X (roadmap 2.6): precio final y ajustes del envío en el seguimiento (PYME y transportista).

/** Hoja «Rechazar ajuste»: el motivo es obligatorio (`ADJ-004`, field `note`). */
data class RejectAdjustmentForm(val adjustment: AdjustmentView, val note: String = "", val error: UiText? = null)

data class AdjustmentsUiState(
    val loading: Boolean = true,
    /** null = no cargó (sin permiso, sin red o backend anterior): la tarjeta no se muestra. */
    val data: ShipmentAdjustmentsView? = null,
    val isPyme: Boolean = false,
    val isCarrier: Boolean = false,
    /** Ajuste que la PYME va a aceptar (diálogo de confirmación con el precio final resultante). */
    val accepting: AdjustmentView? = null,
    val rejecting: RejectAdjustmentForm? = null,
    /** Sugerencia que el transportista va a proponer (diálogo). */
    val proposing: AdjustmentSuggestionView? = null,
    val submitting: Boolean = false,
    val message: UiMessage? = null,
) {
    /** La PYME decide los pendientes (los escalados los decide el operador en la web). */
    fun canDecide(a: AdjustmentView) = isPyme && a.isPending && !a.escalated

    val hasContent: Boolean
        get() = data != null && (data.adjustments.isNotEmpty() || (isCarrier && data.suggestions.isNotEmpty()) ||
            (data.finalPrice != null && data.agreedPrice != null && data.finalPrice != data.agreedPrice))
}

/**
 * Ajustes del envío: lista de solo lectura con su estado y el precio final; la PYME acepta o rechaza (con motivo) y el
 * transportista ve las sugerencias (estadía, diferencia de peso) y puede proponerlas con un toque (`Idempotency-Key` por toque).
 */
class ShipmentAdjustmentsViewModel(
    private val shipmentId: String,
    auth: AuthRepository,
    private val repo: AdjustmentsRepository,
    private val realtime: RealtimeRepository,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val user = (auth.state.value as? SessionState.LoggedIn)?.user
    private val _state = MutableStateFlow(
        AdjustmentsUiState(
            isPyme = user?.organizationType == OrgType.PYME,
            isCarrier = user?.organizationType == OrgType.CARRIER && user.role != Role.DRIVER,
        ),
    )
    val state: StateFlow<AdjustmentsUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()

    /** `shipment.adjustment_updated` de este envío (y `stream.resync`) recarga en silencio. */
    suspend fun liveLoop() {
        realtime.refreshSignals(setOf(RealtimeTypes.SHIPMENT_ADJUSTMENT_UPDATED, RealtimeTypes.SHIPMENT_STATUS_CHANGED)) { it.string("shipmentId") == shipmentId }
            .collect { load() }
    }

    private fun load() {
        viewModelScope.launch {
            repo.forShipment(shipmentId).fold(
                onSuccess = { d -> _state.update { it.copy(loading = false, data = d) } },
                onFailure = { _state.update { it.copy(loading = false) } }, // la tarjeta simplemente no aparece
            )
        }
    }

    fun askAccept(a: AdjustmentView) = _state.update { if (it.canDecide(a)) it.copy(accepting = a) else it }
    fun askReject(a: AdjustmentView) = _state.update { if (it.canDecide(a)) it.copy(rejecting = RejectAdjustmentForm(a)) else it }
    fun onRejectNote(text: String) = _state.update { s -> s.rejecting?.let { s.copy(rejecting = it.copy(note = text.take(MAX_REASON), error = null)) } ?: s }
    fun askPropose(s: AdjustmentSuggestionView) = _state.update { if (it.isCarrier) it.copy(proposing = s) else it }
    fun dismiss() = _state.update { if (it.submitting) it else it.copy(accepting = null, rejecting = null, proposing = null) }

    fun confirmAccept() {
        val a = _state.value.accepting ?: return
        submit({ repo.accept(a.id) }, R.string.shipments_adjustment_accepted)
    }

    fun confirmReject() {
        val form = _state.value.rejecting ?: return
        if (form.note.isBlank()) {
            _state.update { it.copy(rejecting = form.copy(error = UiText.of(R.string.shipments_adjustment_reason_required))) }
            return
        }
        submit({ repo.reject(form.adjustment.id, form.note) }, R.string.shipments_adjustment_rejected)
    }

    fun confirmPropose() {
        val s = _state.value.proposing ?: return
        val body = ProposeAdjustmentRequest(s.type, s.amount, note = s.detail?.take(MAX_REASON), stopId = s.stopId)
        val key = newKey()
        submit({ repo.propose(shipmentId, body, key) }, R.string.shipments_adjustment_proposed)
    }

    private fun submit(call: suspend () -> Result<AdjustmentView>, success: Int) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            call().fold(
                onSuccess = {
                    _state.update { it.copy(submitting = false, accepting = null, rejecting = null, proposing = null, message = UiMessage.success(success)) }
                    load()
                },
                onFailure = { e ->
                    val note = e.fieldErrors("note")["note"]
                    _state.update {
                        if (note != null && it.rejecting != null) it.copy(submitting = false, rejecting = it.rejecting.copy(error = UiText.Raw(note)))
                        else it.copy(submitting = false, accepting = null, rejecting = null, proposing = null, message = UiMessage.error(e.userMessage()))
                    }
                    load()
                },
            )
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}

/** Intenciones de la tarjeta de ajustes. */
class AdjustmentActions(
    val onAccept: (AdjustmentView) -> Unit = {},
    val onReject: (AdjustmentView) -> Unit = {},
    val onRejectNote: (String) -> Unit = {},
    val onPropose: (AdjustmentSuggestionView) -> Unit = {},
    val onConfirmAccept: () -> Unit = {},
    val onConfirmReject: () -> Unit = {},
    val onConfirmPropose: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/** Monto con signo: `+ S/ 120.00` / `− S/ 50.00` (descuento). */
fun signedMoney(amount: Double): String = if (amount < 0) "− " + Fmt.money(-amount) else "+ " + Fmt.money(amount)

/**
 * Tarjeta «Precio y ajustes» del seguimiento: acordado, ajustes aceptados y precio final (con IGV; el transportista ve además
 * «Cobras S/ X neto»), la lista de ajustes con su estado y, según el rol, aceptar/rechazar o proponer una sugerencia.
 */
@Composable
fun AdjustmentsCard(state: AdjustmentsUiState, actions: AdjustmentActions, snackbar: LogyxSnackbarState) {
    MessageEffect(state.message, snackbar, actions.onMessageShown)
    val data = state.data
    if (data != null && state.hasContent) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        LogyxCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.shipments_adjustments_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(12.dp))
            PriceRow(stringResource(R.string.shipments_adjustments_agreed), Fmt.money(data.agreedPrice))
            if ((data.acceptedTotal ?: 0.0) != 0.0) PriceRow(stringResource(R.string.shipments_adjustments_accepted_total), signedMoney(data.acceptedTotal ?: 0.0))
            PriceRow(stringResource(R.string.shipments_adjustments_final), Fmt.money(data.finalPrice ?: data.agreedPrice), strong = true)
            BreakdownLines(data.finalPriceBreakdown, state.isCarrier)
            data.adjustments.forEach { a ->
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                AdjustmentRow(a, state, actions)
            }
            if (state.isCarrier && data.suggestions.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.shipments_adjustments_suggestions), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.shipments_adjustments_suggestions_hint), style = MaterialTheme.typography.bodySmall, color = muted)
                data.suggestions.forEach { s ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.type.label, style = MaterialTheme.typography.bodyMedium)
                            s.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                        }
                        Spacer(Modifier.width(8.dp))
                        LogyxButton(
                            stringResource(R.string.shipments_adjustment_propose, Fmt.money(s.amount)), { actions.onPropose(s) },
                            variant = ButtonVariant.Secondary, size = ButtonSize.Medium, fullWidth = false, requiresOperate = true,
                        )
                    }
                }
            }
        }
    }
    state.accepting?.let { a ->
        val newFinal = (data?.finalPrice ?: data?.agreedPrice ?: 0.0) + a.amount
        LogyxConfirmDialog(
            title = stringResource(R.string.shipments_adjustment_accept_title, signedMoney(a.amount)),
            message = stringResource(R.string.shipments_adjustment_accept_message, a.type.label, Fmt.money(newFinal)),
            confirmLabel = stringResource(R.string.shipments_adjustment_accept),
            dismissLabel = stringResource(R.string.shipments_confirm_dismiss),
            onConfirm = actions.onConfirmAccept, onDismiss = actions.onDismiss, loading = state.submitting,
        )
    }
    state.rejecting?.let { f ->
        LogyxReasonSheet(
            title = stringResource(R.string.shipments_adjustment_reject_title, signedMoney(f.adjustment.amount)),
            message = stringResource(R.string.shipments_adjustment_reject_message),
            reasonLabel = stringResource(R.string.shipments_reason_label),
            value = f.note, onValueChange = actions.onRejectNote, error = f.error?.asString(),
            confirmLabel = stringResource(R.string.shipments_adjustment_reject), onConfirm = actions.onConfirmReject,
            onDismiss = actions.onDismiss, loading = state.submitting,
        )
    }
    state.proposing?.let { s ->
        LogyxConfirmDialog(
            title = stringResource(R.string.shipments_adjustment_propose_title, s.type.label, Fmt.money(s.amount)),
            message = stringResource(R.string.shipments_adjustment_propose_message),
            confirmLabel = stringResource(R.string.shipments_adjustment_propose_confirm),
            dismissLabel = stringResource(R.string.shipments_confirm_dismiss),
            onConfirm = actions.onConfirmPropose, onDismiss = actions.onDismiss, loading = state.submitting,
        )
    }
}

@Composable
private fun PriceRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label, style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value, style = if (strong) LogyxText.moneyMedium else LogyxText.moneySmall,
            color = if (strong) LogyxTheme.colors.moneyText else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (strong) FontWeight.SemiBold else null,
        )
    }
}

/** IGV del precio final; el transportista ve además «Cobras S/ X neto» (la PYME y el conductor no reciben la comisión). */
@Composable
private fun BreakdownLines(b: PriceBreakdownView?, carrier: Boolean) {
    b ?: return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    b.vat?.let { Text(stringResource(R.string.shipments_adjustments_vat, Fmt.money(it)), style = MaterialTheme.typography.bodySmall, color = muted) }
    if (carrier) b.carrierNet?.let {
        Text(stringResource(R.string.shipments_adjustments_net, Fmt.money(it)), style = MaterialTheme.typography.bodyMedium, color = LogyxTheme.colors.moneyText)
    }
}

@Composable
private fun AdjustmentRow(a: AdjustmentView, state: AdjustmentsUiState, actions: AdjustmentActions) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(a.type.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(signedMoney(a.amount), style = LogyxText.moneySmall, color = MaterialTheme.colorScheme.onSurface)
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusPill(a.status.pill())
        Text(Fmt.dateTime(a.createdAt), style = MaterialTheme.typography.labelMedium, color = muted)
    }
    a.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
    when {
        a.isPending && a.escalated -> Text(stringResource(R.string.shipments_adjustment_escalated), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 4.dp))
        a.isPending && a.respondBy != null -> Text(stringResource(R.string.shipments_adjustment_respond_by, Fmt.dateTime(a.respondBy)), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 4.dp))
        a.status == AdjustmentStatus.REJECTED && !a.decisionNote.isNullOrBlank() ->
            Text(stringResource(R.string.shipments_adjustment_reject_reason, a.decisionNote!!), style = MaterialTheme.typography.bodySmall, color = LogyxTheme.colors.dangerText, modifier = Modifier.padding(top = 4.dp))
    }
    if (state.canDecide(a)) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogyxButton(stringResource(R.string.shipments_adjustment_accept), { actions.onAccept(a) }, Modifier.weight(1f), ButtonVariant.Money, ButtonSize.Medium, requiresOperate = true)
            LogyxButton(stringResource(R.string.shipments_adjustment_reject), { actions.onReject(a) }, Modifier.weight(1f), ButtonVariant.Destructive, ButtonSize.Medium, requiresOperate = true)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AdjustmentsCardPreview() {
    val data = ShipmentAdjustmentsView(
        agreedPrice = 1850.0, acceptedTotal = 120.0, finalPrice = 1970.0,
        finalPriceBreakdown = PriceBreakdownView(amount = 1970.0, vat = 300.51, carrierNet = 1812.48),
        adjustments = listOf(
            AdjustmentView("a1", type = AdjustmentType.DETENTION, amount = 120.0, status = AdjustmentStatus.ACCEPTED, note = "4 h de espera en el almacén", createdAt = "2026-11-10T15:00:00-05:00"),
            AdjustmentView("a2", type = AdjustmentType.TOLL, amount = 45.5, status = AdjustmentStatus.PENDING, respondBy = "2026-11-12T15:00:00-05:00", createdAt = "2026-11-10T16:00:00-05:00"),
        ),
        suggestions = listOf(AdjustmentSuggestionView(AdjustmentType.DETENTION, "d", 90.0, "Estadía de 4 h en la entrega (franquicia 3 h)")),
    )
    LogyxTheme {
        Column(Modifier.padding(16.dp)) {
            AdjustmentsCard(AdjustmentsUiState(loading = false, data = data, isPyme = true), AdjustmentActions(), pe.logyx.core.designsystem.components.rememberLogyxSnackbarState())
        }
    }
}
