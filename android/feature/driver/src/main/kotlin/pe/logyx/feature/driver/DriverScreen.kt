package pe.logyx.feature.driver

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import pe.logyx.core.designsystem.components.LiveEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import pe.logyx.core.data.SyncItem
import pe.logyx.core.data.SyncItemKind
import pe.logyx.core.data.SyncItemState
import pe.logyx.core.designsystem.components.LogyxTextField
import pe.logyx.core.designsystem.components.NumberField
import pe.logyx.core.designsystem.components.RadioCardRow
import pe.logyx.core.designsystem.components.SyncChipState
import pe.logyx.core.designsystem.components.SyncStatusChip
import pe.logyx.core.designsystem.components.asString
import pe.logyx.core.data.EvidenceItem
import pe.logyx.core.data.EvidenceState
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.EvidenceTile
import pe.logyx.core.designsystem.components.LogyxBottomSheet
import pe.logyx.core.designsystem.components.SignaturePad
import pe.logyx.core.designsystem.components.TileStatus
import pe.logyx.core.designsystem.components.rememberSignaturePadState
import pe.logyx.core.model.DocumentType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.AlertBanner
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LogyxBadge
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.MessageKind
import pe.logyx.core.designsystem.components.PillTone
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.RouteMap
import pe.logyx.core.designsystem.components.SkeletonBox
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.StopContactBlock
import pe.logyx.core.designsystem.components.StopTimeline
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.label
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.StopStatus
import pe.logyx.core.model.StopType
import pe.logyx.core.model.StopView
import pe.logyx.core.model.nextStop

/**
 * Punto de inyección de la captura de foto para tests instrumentados: si hay un valor, "Tomar foto de entrega"
 * usa sus bytes en vez de abrir la cámara. En producción es null.
 */
fun interface PhotoCapture { fun capture(): ByteArray? }

val LocalPhotoCapture = compositionLocalOf<PhotoCapture?> { null }

@Composable
fun DriverTripRoute(
    onReportIncident: (String) -> Unit = {},
    onOpenIncidents: (String) -> Unit = {},
    viewModel: DriverTripViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val injected = LocalPhotoCapture.current
    var tempPath by rememberSaveable { mutableStateOf<String?>(null) }
    // Qué se está fotografiando (foto de entrega, foto de la carga o guía de remisión): sobrevive a la cámara.
    var captureType by rememberSaveable { mutableStateOf<String?>(null) }
    fun deliver(bytes: ByteArray, type: DocumentType) =
        if (type == DocumentType.REMISSION_GUIDE) viewModel.onGuideCaptured(bytes) else viewModel.onPhotoCaptured(bytes, type)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = tempPath?.let(::File)
        val type = captureType?.let { runCatching { DocumentType.valueOf(it) }.getOrNull() }
        tempPath = null
        captureType = null
        if (file != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                val data = if (ok) runCatching { file.readBytes() }.getOrNull() else null
                file.delete()
                data
            }
            if (bytes != null && bytes.isNotEmpty()) deliver(bytes, type ?: DocumentType.DELIVERY_PHOTO)
        }
    }
    // Emergencia: se pide el permiso de ubicación al confirmar; si se niega, se reporta sin ubicación.
    var pendingEmergency by remember { mutableStateOf<Pair<EmergencyKind, String>?>(null) }
    val locate: suspend () -> DeviceLocation? = { context.currentDeviceLocation() }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pendingEmergency?.let { (kind, text) -> viewModel.reportEmergency(kind, text, locate) }
        pendingEmergency = null
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LiveEffect(viewModel) { viewModel.liveLoop() }
    val shipmentId = state.trip?.id
    DriverTripScreen(
        state, viewModel::refresh, viewModel::advance, viewModel::onNoticeShown, viewModel::dismissActionError,
        onTakePhoto = { type ->
            if (injected != null) injected.capture()?.let { deliver(it, type) }
            else scope.launch {
                val file = withContext(Dispatchers.IO) {
                    File.createTempFile("evidencia_", ".jpg", File(context.cacheDir, "evidence").apply { mkdirs() })
                }
                tempPath = file.absolutePath
                captureType = type.name
                val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                launcher.launch(uri)
            }
        },
        onSignature = viewModel::onSignatureCaptured,
        onRetryEvidence = viewModel::retryEvidence,
        onDiscardEvidence = viewModel::discardEvidence,
        onDismissClosedEvidence = viewModel::dismissClosedEvidence,
        onReportIncident = { shipmentId?.let(onReportIncident) },
        onOpenIncidents = { shipmentId?.let(onOpenIncidents) },
        form = DriverFormActions(viewModel::onWeight, viewModel::onReceiverName, viewModel::onReceiverDoc),
        onRetrySync = viewModel::retrySyncNow,
        adjustment = AdjustmentActions(
            viewModel::openAdjustment, viewModel::closeAdjustment, viewModel::onAdjustmentType, viewModel::onAdjustmentAmount,
            viewModel::onAdjustmentNote, viewModel::onAdjustmentStop, viewModel::requestAdjustment, viewModel::confirmAdjustment,
            viewModel::cancelAdjustmentConfirm, viewModel::dismissAdjustmentError,
        ),
        onEmergency = { kind, text ->
            if (context.hasLocationPermission()) viewModel.reportEmergency(kind, text, locate)
            else {
                pendingEmergency = kind to text
                permissions.launch(LOCATION_PERMISSIONS)
            }
        },
    )
}

/** Intenciones de los campos de la parada (peso del recojo, quién recibe). */
class DriverFormActions(
    val onWeight: (String) -> Unit = {},
    val onReceiverName: (String) -> Unit = {},
    val onReceiverDoc: (String) -> Unit = {},
)

@Composable
private fun DriverStep.actionText() = stringResource(
    when (this) {
        DriverStep.ARRIVE_PICKUP -> R.string.driver_action_arrive_pickup
        DriverStep.PICKED_UP -> R.string.driver_action_picked_up
        DriverStep.ARRIVE_DESTINATION -> R.string.driver_action_arrive_destination
        DriverStep.DELIVERED -> R.string.driver_action_delivered
    },
)

/** Modo conductor (action-first): mapa, siguiente parada y UN botón grande. */
@Composable
fun DriverTripScreen(
    state: DriverUiState,
    onRefresh: () -> Unit,
    onAdvance: () -> Unit,
    onNoticeShown: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onTakePhoto: (DocumentType) -> Unit = {},
    onSignature: (ByteArray) -> Unit = {},
    onRetryEvidence: (String) -> Unit = {},
    onDiscardEvidence: (String) -> Unit = {},
    onDismissClosedEvidence: () -> Unit = {},
    onReportIncident: () -> Unit = {},
    onOpenIncidents: () -> Unit = {},
    form: DriverFormActions = DriverFormActions(),
    onRetrySync: () -> Unit = {},
    onEmergency: (EmergencyKind, String) -> Unit = { _, _ -> },
    adjustment: AdjustmentActions = AdjustmentActions(),
) {
    val trip = state.trip
    val snackbar = rememberLogyxSnackbarState()
    val notice = when (state.notice) {
        DriverNotice.DELIVERED -> UiMessage.success(stringResource(R.string.driver_notice_delivered))
        DriverNotice.STOP_UPDATED -> UiMessage.success(stringResource(R.string.driver_notice_stop_updated))
        DriverNotice.QUEUED -> UiMessage.info(stringResource(R.string.driver_notice_queued))
        DriverNotice.EMERGENCY_SENT -> UiMessage.success(stringResource(R.string.driver_emergency_sent))
        DriverNotice.EMERGENCY_QUEUED -> UiMessage.info(stringResource(R.string.driver_emergency_queued))
        DriverNotice.ADJUSTMENT_SENT -> UiMessage.success(stringResource(R.string.driver_adjustment_sent))
        DriverNotice.ADJUSTMENT_QUEUED -> UiMessage.info(stringResource(R.string.driver_adjustment_queued))
        DriverNotice.SYNCED -> UiMessage.success(stringResource(R.string.driver_notice_synced))
        null -> null
    }
    MessageEffect(notice, snackbar, onNoticeShown)
    var confirming by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var showEmergency by remember { mutableStateOf(false) }

    val title = if (trip != null) "${cityLabel(trip.originCity)} → ${cityLabel(trip.destinationCity)}" else stringResource(R.string.driver_title_default)
    LogyxScreen(
        title = title,
        snackbar = snackbar,
        actions = { trip?.let { StatusPill(it.status.pill(), Modifier.padding(end = 12.dp)) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> DriverSkeleton()
                state.error != null && trip == null -> ErrorState(state.error, onRefresh, Modifier.fillMaxSize())
                trip == null -> Refreshable(state.refreshing, onRefresh) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        ClosedEvidenceNotice(state.closedEvidence, onDismissClosedEvidence)
                        SyncIndicator(state) { showSync = true }
                        EmptyState(stringResource(R.string.driver_empty_title), stringResource(R.string.driver_empty_message))
                        HistorySection(state.history)
                    }
                }
                else -> TripContent(
                    state, trip, onRefresh, onDismissError, onConfirm = { confirming = true },
                    onTakePhoto, onSignature, onRetryEvidence, onDiscardEvidence, onDismissClosedEvidence, onReportIncident, onOpenIncidents,
                    form = form, onOpenSync = { showSync = true }, onEmergency = { showEmergency = true }, adjustment = adjustment,
                )
            }
        }
    }
    if (showSync) {
        if (state.syncCount == 0 && !state.retrying) showSync = false
        else SyncSheet(state, onRetrySync) { showSync = false }
    }
    if (showEmergency) {
        EmergencySheet(
            sending = state.emergencySending,
            onDismiss = { showEmergency = false },
            onSubmit = { kind, text -> showEmergency = false; onEmergency(kind, text) },
        )
    }

    state.adjustment?.let { form ->
        AdjustmentSheet(form, trip?.stops.orEmpty(), state.adjustmentConfirming, adjustment)
    }

    val action = state.action
    if (confirming && action != null && state.canAdvance) {
        val haptic = LocalHapticFeedback.current
        val (t, m, b) = when (action.step) {
            DriverStep.ARRIVE_PICKUP -> Triple(R.string.driver_confirm_arrive_pickup_title, R.string.driver_confirm_arrive_pickup_message, R.string.driver_confirm_arrive_pickup_button)
            DriverStep.PICKED_UP -> Triple(R.string.driver_confirm_picked_up_title, R.string.driver_confirm_picked_up_message, R.string.driver_confirm_picked_up_button)
            DriverStep.ARRIVE_DESTINATION -> Triple(R.string.driver_confirm_arrive_destination_title, R.string.driver_confirm_arrive_destination_message, R.string.driver_confirm_arrive_destination_button)
            DriverStep.DELIVERED -> Triple(R.string.driver_confirm_delivered_title, R.string.driver_confirm_delivered_message, R.string.driver_confirm_delivered_button)
        }
        val message = when {
            action.step == DriverStep.PICKED_UP && state.weightKg != null && state.weightValid ->
                stringResource(R.string.driver_confirm_picked_up_weight, Fmt.kg(state.weightKg))
            action.step == DriverStep.DELIVERED && state.receiverValid ->
                stringResource(R.string.driver_confirm_delivered_receiver, state.form.receiverName.trim(), pe.logyx.core.model.ReceiverDoc.normalize(state.form.receiverDoc))
            else -> stringResource(m)
        }
        LogyxConfirmDialog(
            title = stringResource(t),
            message = message,
            confirmLabel = stringResource(b),
            dismissLabel = stringResource(R.string.driver_confirm_not_yet),
            onConfirm = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                confirming = false
                onAdvance()
            },
            onDismiss = { confirming = false },
        )
    } else if (confirming) confirming = false
}

@Composable
private fun DriverSkeleton() {
    Column(Modifier.fillMaxSize()) {
        SkeletonBox(null, 200.dp, Modifier.fillMaxWidth())
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBox(120.dp, 16.dp)
            SkeletonBox(240.dp, 32.dp)
            SkeletonBox(null, 20.dp, Modifier.fillMaxWidth())
            SkeletonBox(null, 120.dp, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TripContent(
    state: DriverUiState,
    trip: ShipmentView,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onConfirm: () -> Unit,
    onTakePhoto: (DocumentType) -> Unit,
    onSignature: (ByteArray) -> Unit,
    onRetryEvidence: (String) -> Unit,
    onDiscardEvidence: (String) -> Unit,
    onDismissClosedEvidence: () -> Unit,
    onReportIncident: () -> Unit,
    onOpenIncidents: () -> Unit,
    form: DriverFormActions,
    onOpenSync: () -> Unit,
    onEmergency: () -> Unit,
    adjustment: AdjustmentActions,
) {
    val total = trip.stops.size
    val done = trip.stops.count { it.status == StopStatus.COMPLETED }
    val current = (done + 1).coerceAtMost(total.coerceAtLeast(1))
    Column(Modifier.fillMaxSize()) {
        if (state.offline) OfflineBanner(state.cachedAt)
        if (state.incidentBlocked) {
            AlertBanner(
                stringResource(R.string.driver_incident_banner),
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                kind = MessageKind.Warning,
            )
        }
        val evidenceStep = state.action?.step == DriverStep.DELIVERED || state.pickupStep
        // En la entrega el panel de evidencia necesita espacio: el mapa se achica.
        Box(Modifier.fillMaxWidth().weight(if (evidenceStep) 0.3f else 0.8f)) {
            val mapDescription = stringResource(R.string.driver_map_a11y, cityLabel(trip.originCity), cityLabel(trip.destinationCity))
            if (LocalInspectionMode.current) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            } else {
                RouteMap(state.origin, state.destination, Modifier.fillMaxSize(), path = state.route?.points, fillParent = true, contentDescription = mapDescription)
            }
            if (total > 0 && !state.delivered) {
                LogyxBadge(
                    stringResource(R.string.driver_stop_progress, current, total),
                    Modifier.align(Alignment.TopStart).padding(12.dp),
                    tone = PillTone.Petrol,
                )
            }
        }
        Refreshable(state.refreshing, onRefresh, Modifier.weight(if (evidenceStep) 1.7f else 1.2f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ClosedEvidenceNotice(state.closedEvidence, onDismissClosedEvidence)
                SyncIndicator(state, onOpenSync)
                if (state.delivered) DeliveredCard(trip) else trip.nextStop()?.let { NextStopCard(it, current, total) }
                if (state.pickupStep) PickupPanel(state, onTakePhoto, form.onWeight, onRetryEvidence, onDiscardEvidence)
                when (state.evidencePhase) {
                    EvidencePhase.OPEN -> {
                        EvidencePanel(state.evidence, { onTakePhoto(DocumentType.DELIVERY_PHOTO) }, onSignature, onRetryEvidence, onDiscardEvidence)
                        ReceiverCard(state, form)
                    }
                    // RN06: la foto y la firma solo se piden entre la llegada y el cierre de la entrega; se explica por qué no hay botones.
                    EvidencePhase.NOT_YET -> EvidenceHint(R.string.driver_evidence_not_yet)
                    // Entrega guardada en el teléfono: la foto y el cierre salen al recuperar señal.
                    EvidencePhase.CLOSED -> EvidenceHint(if (state.pendingSync) R.string.driver_evidence_pending else R.string.driver_evidence_closed)
                    EvidencePhase.NONE -> Unit
                }
                // Roadmap 2.6: cobro extra (estadía, peajes, maniobras…). Va en el contenido y no en el panel fijo:
                // el panel queda solo para el botón XL y las acciones urgentes, y el mapa conserva su espacio.
                if (state.action != null) AdjustmentPrompt(adjustment.onOpen, state.adjustmentSending)
                Text(
                    "${Fmt.tons(trip.weightKg)} · ${trip.vehiclePlate ?: "—"} · ${trip.cargoDescription ?: stringResource(R.string.driver_cargo_default)}",
                    style = LogyxText.mono, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StopTimeline(trip.stops, large = true)
                HistorySection(state.history)
            }
        }
        val action = state.action
        if (action != null || state.actionError != null || state.adjustmentError != null) {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.actionError?.let {
                    AlertBanner(
                        it, kind = MessageKind.Error,
                        title = stringResource(R.string.driver_action_error_title),
                        actionLabel = if (action != null) stringResource(R.string.driver_action_error_retry) else null,
                        onAction = if (action != null) onConfirm else null,
                        onDismiss = onDismissError,
                    )
                }
                state.adjustmentError?.let {
                    AlertBanner(
                        it, kind = MessageKind.Error,
                        title = stringResource(R.string.driver_adjustment_error_title),
                        onDismiss = adjustment.onDismissError,
                    )
                }
                if (action != null) {
                    LogyxButton(action.step.actionText(), onConfirm, size = ButtonSize.Xl, loading = state.acting, enabled = state.canAdvance)
                    actionHint(state)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.incidentBlocked) {
                            LogyxButton(
                                stringResource(R.string.driver_incident_view), onOpenIncidents, Modifier.weight(1f),
                                variant = ButtonVariant.Secondary, size = ButtonSize.Medium,
                            )
                        } else {
                            LogyxButton(
                                stringResource(R.string.driver_incident_report), onReportIncident, Modifier.weight(1f),
                                variant = ButtonVariant.Secondary, size = ButtonSize.Medium,
                            )
                        }
                        // Roadmap 2.7: accidente, robo o avería con ubicación; funciona sin señal (cola offline).
                        LogyxButton(
                            stringResource(R.string.driver_emergency), onEmergency, Modifier.weight(1f),
                            variant = ButtonVariant.Destructive, size = ButtonSize.Medium, icon = Icons.Rounded.WarningAmber,
                            loading = state.emergencySending,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustmentPrompt(onOpen: () -> Unit, sending: Boolean) {
    LogyxCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.driver_adjustment_prompt_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.driver_adjustment_prompt_body),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LogyxButton(
                stringResource(R.string.driver_adjustment_open), onOpen,
                variant = ButtonVariant.Secondary, size = ButtonSize.Medium, loading = sending,
            )
        }
    }
}

/** Explica por qué no se ofrece la evidencia en este momento (RN06). */
@Composable
private fun EvidenceHint(@androidx.annotation.StringRes text: Int) {
    LogyxCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.driver_evidence_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * La evidencia que el servidor rechazó por entrega cerrada (`DOC-008`) o por ya no ser el conductor asignado (`DOC-007`):
 * se descartó de la cola (no se reintenta) y se le dice al conductor qué pasó con cada foto o firma.
 */
@Composable
private fun ClosedEvidenceNotice(items: List<EvidenceItem>, onDismiss: () -> Unit) {
    if (items.isEmpty()) return
    val photos = items.count { it.type != DocumentType.SIGNATURE }
    val signatures = items.count { it.type == DocumentType.SIGNATURE }
    val what = buildList {
        if (photos > 0) add(androidx.compose.ui.res.pluralStringResource(R.plurals.driver_evidence_closed_photos, photos, photos))
        if (signatures > 0) add(androidx.compose.ui.res.pluralStringResource(R.plurals.driver_evidence_closed_signatures, signatures, signatures))
    }.joinToString(" y ")
    AlertBanner(
        text = stringResource(R.string.driver_evidence_closed_banner, what),
        kind = MessageKind.Warning,
        title = stringResource(R.string.driver_evidence_closed_title),
        actionLabel = stringResource(R.string.driver_evidence_closed_ok),
        onAction = onDismiss,
    )
}

/** "Viajes anteriores" (V15, F22): últimos entregados o cancelados. */
@Composable
private fun HistorySection(history: List<ShipmentView>) {
    if (history.isEmpty()) return
    LogyxCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.driver_history_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        history.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${cityLabel(t.originCity)} → ${cityLabel(t.destinationCity)}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        Fmt.dateTime(t.deliveredAt ?: t.cancelledAt ?: t.createdAt),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(t.status.pill())
            }
        }
    }
}

/** Miniatura decodificada fuera del hilo principal y recordada por id de evidencia. */
@Composable
private fun rememberThumbnail(item: EvidenceItem): State<ImageBitmap?> = produceState<ImageBitmap?>(null, item.id, item.bytes?.size) {
    val bytes = item.bytes ?: return@produceState
    value = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 360) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }
}

@Composable
private fun EvidenceThumb(item: EvidenceItem, caption: String, modifier: Modifier = Modifier) {
    val image by rememberThumbnail(item)
    EvidenceTile(
        caption, modifier, image = image,
        status = when (item.state) {
            EvidenceState.UPLOADING -> TileStatus.Uploading
            EvidenceState.UPLOADED -> TileStatus.Uploaded
            EvidenceState.FAILED, EvidenceState.CLOSED -> TileStatus.Failed
            EvidenceState.QUEUED -> TileStatus.Uploading
        },
        progress = item.progress,
    )
}

/** Evidencia de entrega (RN06): foto obligatoria y firma opcional, con estado de subida por miniatura. */
@Composable
private fun EvidencePanel(
    items: List<EvidenceItem>,
    onTakePhoto: () -> Unit,
    onSignature: (ByteArray) -> Unit,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
) {
    var signing by remember { mutableStateOf(false) }
    // Las cerradas (DOC-007/008) no se listan aquí: el aviso de arriba ya dice que se descartaron.
    val shown = items.filter { (it.type == DocumentType.DELIVERY_PHOTO || it.type == DocumentType.SIGNATURE) && it.state != EvidenceState.CLOSED }
    LogyxCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.driver_evidence_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        LogyxButton(stringResource(R.string.driver_evidence_take_photo), onTakePhoto, size = ButtonSize.Large)
        Spacer(Modifier.height(8.dp))
        LogyxButton(stringResource(R.string.driver_evidence_add_signature), { signing = true }, variant = ButtonVariant.Secondary, size = ButtonSize.Medium)
        if (shown.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            var photoNo = 0
            val captions = shown.map {
                if (it.type == DocumentType.SIGNATURE) stringResource(R.string.driver_evidence_signature_caption)
                else stringResource(R.string.driver_evidence_photo_caption, ++photoNo)
            }
            EvidenceThumbs(shown, captions, onRetry, onDiscard)
        }
    }
    if (signing) {
        LogyxBottomSheet(onDismiss = { signing = false }, title = stringResource(R.string.driver_signature_title)) {
            val pad = rememberSignaturePadState()
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SignaturePad(pad)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogyxButton(stringResource(R.string.driver_signature_clear), { pad.clear() }, Modifier.weight(1f), ButtonVariant.Secondary, enabled = !pad.isEmpty)
                    LogyxButton(
                        stringResource(R.string.driver_signature_save),
                        { pad.toPng()?.let { onSignature(it); signing = false } },
                        Modifier.weight(1f), enabled = !pad.isEmpty,
                    )
                }
            }
        }
    }
}

@Composable
private fun NextStopCard(stop: StopView, current: Int, total: Int) {
    val progress = stringResource(R.string.driver_stop_progress, current, total)
    LogyxCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.driver_next_stop).uppercase(),
                Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            )
            if (total > 0) Text(progress, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        LogyxBadge(
            stop.type.label,
            tone = if (stop.type == StopType.PICKUP) PillTone.Petrol else PillTone.Emerald,
        )
        Spacer(Modifier.height(8.dp))
        Text(cityLabel(stop.city), fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        stop.address?.let {
            Text(it, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        if (stop.status == StopStatus.ARRIVED) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.driver_arrived_since, Fmt.time(stop.arrivedAt)),
                style = MaterialTheme.typography.titleMedium, color = LogyxTheme.colors.moneyText,
            )
        }
        // Roadmap 1.3: ventana horaria, contacto con «Llamar», «Cómo llegar» e indicaciones (solo lo que venga).
        Spacer(Modifier.height(12.dp))
        StopContactBlock(stop, Modifier.fillMaxWidth(), large = true)
    }
}

/** Sin red: se ve lo último guardado en el teléfono y de cuándo es (hora de Lima). */
@Composable
private fun OfflineBanner(cachedAt: String?) {
    val stamp = cachedAt?.let {
        if (Fmt.date(it) == Fmt.date(java.time.Instant.now().toString())) Fmt.time(it) else Fmt.dateTime(it)
    }
    AlertBanner(
        stamp?.let { stringResource(R.string.driver_offline_banner, it) } ?: stringResource(R.string.driver_offline_banner_no_time),
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        kind = MessageKind.Warning,
    )
}


@Composable
private fun DeliveredCard(trip: ShipmentView) {
    Row(
        Modifier.fillMaxWidth().background(LogyxTheme.colors.moneyContainer, MaterialTheme.shapes.large).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(Icons.Rounded.CheckCircle, null, Modifier.size(48.dp), tint = LogyxTheme.colors.moneyStrong)
        Spacer(Modifier.size(12.dp))
        Column {
            Text(stringResource(R.string.driver_delivered_title), style = MaterialTheme.typography.headlineSmall, color = LogyxTheme.colors.onMoneyContainer)
            Text(
                stringResource(R.string.driver_delivered_message, Fmt.dateTime(trip.deliveredAt)),
                style = MaterialTheme.typography.bodyLarge, color = LogyxTheme.colors.onMoneyContainer,
            )
        }
    }
}

/** Texto bajo el botón XL que explica qué falta (o que la foto saldrá al recuperar señal). */
private fun actionHint(state: DriverUiState): Int? = when {
    state.incidentBlocked -> null // ya lo explica el banner de arriba
    state.photoBlocked && state.photoMissingRejected -> R.string.driver_evidence_missing
    state.photoBlocked -> R.string.driver_evidence_required_hint
    state.receiverBlocked -> R.string.driver_receiver_hint
    state.needsEvidence && !state.hasUploadedPhoto && state.hasPhotoAwaitingSignal -> R.string.driver_evidence_awaiting_hint
    state.pickupStep && state.pickupPhotoRequired && !state.hasPickupPhoto ->
        if (state.photoMissingRejected) R.string.driver_pickup_hint_photo_rejected else R.string.driver_pickup_hint_photo
    state.pickupStep && !state.weightValid -> R.string.driver_pickup_hint_weight
    state.pickupStep && state.evidence.any { it.type == DocumentType.PICKUP_PHOTO && it.awaitingSignal && it.state != EvidenceState.UPLOADING } ->
        R.string.driver_pickup_hint_awaiting
    else -> null
}

/** «3 acciones por sincronizar»: abre la hoja con la lista, su estado y «Reintentar ahora». */
@Composable
private fun SyncIndicator(state: DriverUiState, onOpen: () -> Unit) {
    val chip = when (state.syncState) {
        SyncItemState.FAILED -> SyncChipState.Failed
        SyncItemState.SENDING -> SyncChipState.Sending
        SyncItemState.WAITING_SIGNAL -> SyncChipState.WaitingSignal
        else -> SyncChipState.Queued
    }
    SyncStatusChip(state.syncCount, chip, onOpen)
}

@Composable
private fun SyncItem.title(trip: ShipmentView?): String {
    val stopType = trip?.stops?.firstOrNull { it.id == stopId }?.type
    return when (kind) {
        SyncItemKind.ARRIVE -> stringResource(
            when (stopType) { StopType.PICKUP -> R.string.driver_sync_arrive_pickup; StopType.DELIVERY -> R.string.driver_sync_arrive_destination; null -> R.string.driver_sync_arrive_stop },
        )
        SyncItemKind.COMPLETE -> stringResource(
            when (stopType) { StopType.PICKUP -> R.string.driver_sync_picked_up; StopType.DELIVERY -> R.string.driver_sync_delivered; null -> R.string.driver_sync_complete_stop },
        )
        SyncItemKind.EVIDENCE -> evidence?.type?.label ?: stringResource(R.string.driver_evidence_title)
        SyncItemKind.INCIDENT -> stringResource(R.string.driver_sync_incident, operation?.incident?.category?.label ?: "")
        SyncItemKind.ADJUSTMENT -> stringResource(R.string.driver_sync_adjustment, operation?.adjustment?.type?.label ?: "")
    }
}

@Composable
private fun SyncItem.stateText(): String = when (state) {
    SyncItemState.QUEUED -> stringResource(R.string.driver_sync_state_queued)
    SyncItemState.SENDING -> stringResource(R.string.driver_sync_state_sending)
    SyncItemState.WAITING_SIGNAL -> stringResource(R.string.driver_sync_state_waiting_signal)
    SyncItemState.WAITING_PHOTO -> stringResource(R.string.driver_sync_state_waiting_photo)
    SyncItemState.FAILED -> stringResource(R.string.driver_sync_state_failed, error ?: stringResource(R.string.driver_evidence_failed_generic))
}

/** Hoja de pendientes: cada operación en el orden en que saldrá, su estado, el próximo intento y «Reintentar ahora». */
@Composable
private fun SyncSheet(state: DriverUiState, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val items = state.syncList
    LogyxBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.driver_sync_title)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.driver_sync_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title(state.trip), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        item.operation?.createdAt?.let {
                            Text(stringResource(R.string.driver_sync_saved_at, Fmt.time(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val tone = when (item.state) {
                        SyncItemState.FAILED -> PillTone.Red
                        SyncItemState.WAITING_SIGNAL, SyncItemState.WAITING_PHOTO -> PillTone.Amber
                        SyncItemState.SENDING -> PillTone.Petrol
                        SyncItemState.QUEUED -> PillTone.Neutral
                    }
                    if (item.state == SyncItemState.FAILED) StatusPill(stringResource(R.string.driver_sync_state_failed_short), tone)
                    else StatusPill(item.stateText(), tone)
                }
                if (item.state == SyncItemState.FAILED) {
                    Text(item.stateText(), style = MaterialTheme.typography.bodySmall, color = LogyxTheme.colors.dangerText)
                }
            }
            state.sync.nextAttemptAt?.let { at ->
                Text(
                    stringResource(R.string.driver_sync_next_attempt, Fmt.time(java.time.Instant.ofEpochMilli(at).toString())),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.sync.lastError?.let {
                Text(stringResource(R.string.driver_sync_last_error, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LogyxButton(stringResource(R.string.driver_sync_retry), onRetry, size = ButtonSize.Large, loading = state.retrying)
        }
    }
}

/** Lista de miniaturas de evidencia con su estado, «Reintentar» y «Descartar» para las fallidas. */
@Composable
private fun EvidenceThumbs(shown: List<EvidenceItem>, captions: List<String>, onRetry: (String) -> Unit, onDiscard: (String) -> Unit) {
    shown.indices.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { i -> EvidenceThumb(shown[i], captions[i], Modifier.weight(1f)) }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    shown.forEachIndexed { i, item ->
        if (item.awaitingSignal && item.state != EvidenceState.UPLOADING) {
            Text(
                "${captions[i]}: " + stringResource(R.string.driver_evidence_offline),
                style = MaterialTheme.typography.bodyMedium, color = LogyxTheme.colors.onWarningContainer,
            )
            Spacer(Modifier.height(8.dp))
        } else if (item.state == EvidenceState.FAILED) {
            Text(
                "${captions[i]}: " + (item.error?.let { stringResource(R.string.driver_evidence_failed, it) } ?: stringResource(R.string.driver_evidence_failed_generic)),
                style = MaterialTheme.typography.bodyMedium, color = LogyxTheme.colors.dangerText,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogyxButton(stringResource(R.string.driver_evidence_retry), { onRetry(item.id) }, Modifier.weight(1f), ButtonVariant.Secondary, ButtonSize.Medium)
                LogyxButton(stringResource(R.string.driver_evidence_discard), { onDiscard(item.id) }, Modifier.weight(1f), ButtonVariant.Ghost, ButtonSize.Medium)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Recojo (2.5), entre «Llegué» y «Carga recogida»: foto de la carga (obligatoria si la PYME la pidió), peso medido (obligatorio
 * si lo pidió) y foto de la guía de remisión (opcional). Una foto tomada y en cola cuenta, como en la entrega.
 */
@Composable
private fun PickupPanel(
    state: DriverUiState,
    onTakePhoto: (DocumentType) -> Unit,
    onWeight: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
) {
    val shown = state.evidence.filter { (it.type == DocumentType.PICKUP_PHOTO || it.type == DocumentType.REMISSION_GUIDE) && it.state != EvidenceState.CLOSED }
    LogyxCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.driver_pickup_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        LogyxButton(
            stringResource(R.string.driver_pickup_take_photo), { onTakePhoto(DocumentType.PICKUP_PHOTO) }, size = ButtonSize.Large,
            variant = if (state.pickupPhotoRequired) ButtonVariant.Primary else ButtonVariant.Secondary,
        )
        Text(
            stringResource(if (state.pickupPhotoRequired) R.string.driver_pickup_photo_required else R.string.driver_pickup_photo_optional),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        val weightError = state.form.weightError?.asString()
            ?: if (state.form.weight.isNotBlank() && !state.weightValid) stringResource(R.string.driver_pickup_weight_invalid) else null
        NumberField(
            state.form.weight, onWeight, stringResource(R.string.driver_pickup_weight_label), stringResource(R.string.driver_pickup_weight_unit),
            hint = stringResource(if (state.pickupWeightRequired) R.string.driver_pickup_weight_hint_required else R.string.driver_pickup_weight_hint),
            error = weightError, optional = !state.pickupWeightRequired,
        )
        Spacer(Modifier.height(12.dp))
        LogyxButton(stringResource(R.string.driver_pickup_guide), { onTakePhoto(DocumentType.REMISSION_GUIDE) }, variant = ButtonVariant.Secondary, size = ButtonSize.Medium)
        if (shown.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            var n = 0
            val captions = shown.map {
                if (it.type == DocumentType.REMISSION_GUIDE) stringResource(R.string.driver_pickup_guide_caption)
                else stringResource(R.string.driver_pickup_photo_caption, ++n)
            }
            EvidenceThumbs(shown, captions, onRetry, onDiscard)
        }
    }
}

/** Entrega (2.5): nombre y DNI/CE de quien recibe, antes de habilitar «Entregado». */
@Composable
private fun ReceiverCard(state: DriverUiState, form: DriverFormActions) {
    LogyxCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.driver_receiver_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        LogyxTextField(
            state.form.receiverName, form.onReceiverName, stringResource(R.string.driver_receiver_name),
            placeholder = stringResource(R.string.driver_receiver_name_placeholder), error = state.form.receiverNameError?.asString(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(12.dp))
        val doc = state.form.receiverDoc
        val docError = state.form.receiverDocError?.asString()
            ?: if (pe.logyx.core.model.ReceiverDoc.normalize(doc).length >= 8 && !pe.logyx.core.model.ReceiverDoc.isValid(doc)) stringResource(R.string.driver_receiver_doc_invalid) else null
        LogyxTextField(
            doc, form.onReceiverDoc, stringResource(R.string.driver_receiver_doc),
            placeholder = stringResource(R.string.driver_receiver_doc_placeholder), hint = stringResource(R.string.driver_receiver_doc_hint), error = docError,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        )
    }
}

/** Botón «Emergencia» (2.7): elige Accidente / Robo / Avería, detalle opcional y confirma (envío detenido, RN11). */
@Composable
private fun EmergencySheet(sending: Boolean, onDismiss: () -> Unit, onSubmit: (EmergencyKind, String) -> Unit) {
    var kind by rememberSaveable { mutableStateOf<EmergencyKind?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    LogyxBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.driver_emergency_title)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.driver_emergency_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            EmergencyKind.entries.forEach { k ->
                RadioCardRow(selected = kind == k, onClick = { kind = k }) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(k.titleRes), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(k.hintRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            LogyxTextField(
                text, { text = it.take(2000) }, stringResource(R.string.driver_emergency_details),
                placeholder = stringResource(R.string.driver_emergency_details_placeholder), optional = true, singleLine = false, minLines = 2, maxLines = 4,
            )
            LogyxButton(
                stringResource(R.string.driver_emergency_submit), { confirming = true },
                variant = ButtonVariant.Destructive, size = ButtonSize.Large, enabled = kind != null, loading = sending,
            )
        }
    }
    val chosen = kind
    if (confirming && chosen != null) {
        LogyxConfirmDialog(
            title = stringResource(R.string.driver_emergency_confirm_title, stringResource(chosen.wordRes)),
            message = stringResource(R.string.driver_emergency_confirm_message),
            confirmLabel = stringResource(R.string.driver_emergency_confirm_button),
            dismissLabel = stringResource(R.string.driver_emergency_back),
            destructive = true,
            onConfirm = { confirming = false; onSubmit(chosen, text) },
            onDismiss = { confirming = false },
        )
    }
}

private val EmergencyKind.titleRes get() = when (this) {
    EmergencyKind.ACCIDENT -> R.string.driver_emergency_accident
    EmergencyKind.THEFT -> R.string.driver_emergency_theft
    EmergencyKind.BREAKDOWN -> R.string.driver_emergency_breakdown
}
private val EmergencyKind.hintRes get() = when (this) {
    EmergencyKind.ACCIDENT -> R.string.driver_emergency_accident_hint
    EmergencyKind.THEFT -> R.string.driver_emergency_theft_hint
    EmergencyKind.BREAKDOWN -> R.string.driver_emergency_breakdown_hint
}
private val EmergencyKind.wordRes get() = when (this) {
    EmergencyKind.ACCIDENT -> R.string.driver_emergency_word_accident
    EmergencyKind.THEFT -> R.string.driver_emergency_word_theft
    EmergencyKind.BREAKDOWN -> R.string.driver_emergency_word_breakdown
}

private fun previewTrip(vararg status: StopStatus, shipment: ShipmentStatus = ShipmentStatus.IN_TRANSIT) = ShipmentView(
    id = "s1", originCity = "LIMA", destinationCity = "JULIACA", cargoDescription = "Abarrotes", weightKg = 2500.0,
    vehiclePlate = "ABC-123", status = shipment, deliveredAt = "2026-11-10T15:30:00-05:00",
    stops = status.mapIndexed { i, st ->
        StopView("st$i", i + 1, if (i == 0) StopType.PICKUP else StopType.DELIVERY, if (i == 0) "LIMA" else "JULIACA", "Av. Argentina 1234", st, arrivedAt = "2026-11-10T08:15:00-05:00")
    },
)

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun DriverTripPreview() {
    LogyxTheme { DriverTripScreen(DriverUiState(loading = false, trip = previewTrip(StopStatus.ARRIVED, StopStatus.PENDING)), {}, {}) }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun DriverOfflinePreview() {
    val trip = previewTrip(StopStatus.COMPLETED, StopStatus.PENDING).let { t ->
        t.copy(stops = t.stops.map { if (it.type == StopType.DELIVERY) it.copy(contactName = "Ana Torres", contactPhone = "987654321", windowFrom = "08:00", windowTo = "12:00", instructions = "Descarga por la puerta lateral.") else it })
    }
    val pending = listOf(pe.logyx.core.data.PendingStopAction("a", "s1", "st1", pe.logyx.core.data.StopActionOp.ARRIVE, null, "2026-11-10T08:15:00-05:00"))
    LogyxTheme {
        DriverTripScreen(DriverUiState(loading = false, trip = trip, offline = true, cachedAt = "2026-11-10T08:10:00-05:00", pendingActions = pending), {}, {})
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun DriverEvidencePreview() {
    val items = listOf(
        EvidenceItem("a", "s1", "st1", DocumentType.DELIVERY_PHOTO, EvidenceState.UPLOADED, 1f),
        EvidenceItem("b", "s1", "st1", DocumentType.DELIVERY_PHOTO, EvidenceState.FAILED, 0f, error = "Sin conexión"),
    )
    LogyxTheme {
        DriverTripScreen(DriverUiState(loading = false, trip = previewTrip(StopStatus.COMPLETED, StopStatus.ARRIVED).copy(hasActiveIncident = true), allEvidence = items), {}, {})
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun DriverClosedEvidencePreview() {
    val closed = listOf(EvidenceItem("c", "s1", "st1", DocumentType.DELIVERY_PHOTO, EvidenceState.CLOSED, 0f, error = EvidenceErrorsPreview))
    val history = listOf(previewTrip(StopStatus.COMPLETED, StopStatus.COMPLETED, shipment = ShipmentStatus.DELIVERED), previewTrip(StopStatus.PENDING, shipment = ShipmentStatus.CANCELLED).copy(cancelledAt = "2026-11-05T10:00:00-05:00"))
    LogyxTheme {
        DriverTripScreen(DriverUiState(loading = false, trip = previewTrip(StopStatus.COMPLETED, StopStatus.PENDING), allEvidence = closed, history = history), {}, {})
    }
}

private const val EvidenceErrorsPreview = "La entrega ya se cerró: esta evidencia no se subió y se descartó."

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun DriverTripErrorPreview() {
    LogyxTheme {
        DriverTripScreen(DriverUiState(loading = false, trip = previewTrip(StopStatus.COMPLETED, StopStatus.PENDING), actionError = "La parada anterior no está completada."), {}, {})
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun DriverDeliveredPreview() {
    LogyxTheme { DriverTripScreen(DriverUiState(loading = false, trip = previewTrip(StopStatus.COMPLETED, StopStatus.COMPLETED, shipment = ShipmentStatus.DELIVERED)), {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun DriverEmptyPreview() {
    LogyxTheme { DriverTripScreen(DriverUiState(loading = false), {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun DriverLoadingPreview() {
    LogyxTheme { DriverTripScreen(DriverUiState(), {}, {}) }
}
