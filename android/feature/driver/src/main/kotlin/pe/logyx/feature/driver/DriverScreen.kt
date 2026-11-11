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
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = tempPath?.let(::File)
        tempPath = null
        if (file != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                val data = if (ok) runCatching { file.readBytes() }.getOrNull() else null
                file.delete()
                data
            }
            if (bytes != null && bytes.isNotEmpty()) viewModel.onPhotoCaptured(bytes)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LiveEffect(viewModel) { viewModel.liveLoop() }
    val shipmentId = state.trip?.id
    DriverTripScreen(
        state, viewModel::refresh, viewModel::advance, viewModel::onNoticeShown, viewModel::dismissActionError,
        onTakePhoto = {
            if (injected != null) injected.capture()?.let(viewModel::onPhotoCaptured)
            else scope.launch {
                val file = withContext(Dispatchers.IO) {
                    File.createTempFile("entrega_", ".jpg", File(context.cacheDir, "evidence").apply { mkdirs() })
                }
                tempPath = file.absolutePath
                val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                launcher.launch(uri)
            }
        },
        onSignature = viewModel::onSignatureCaptured,
        onRetryEvidence = viewModel::retryEvidence,
        onDiscardEvidence = viewModel::discardEvidence,
        onReportIncident = { shipmentId?.let(onReportIncident) },
        onOpenIncidents = { shipmentId?.let(onOpenIncidents) },
    )
}

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
    onTakePhoto: () -> Unit = {},
    onSignature: (ByteArray) -> Unit = {},
    onRetryEvidence: (String) -> Unit = {},
    onDiscardEvidence: (String) -> Unit = {},
    onReportIncident: () -> Unit = {},
    onOpenIncidents: () -> Unit = {},
) {
    val trip = state.trip
    val snackbar = rememberLogyxSnackbarState()
    val notice = state.notice?.let {
        UiMessage.success(
            stringResource(if (it == DriverNotice.DELIVERED) R.string.driver_notice_delivered else R.string.driver_notice_stop_updated),
        )
    }
    MessageEffect(notice, snackbar, onNoticeShown)
    var confirming by remember { mutableStateOf(false) }

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
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                        EmptyState(stringResource(R.string.driver_empty_title), stringResource(R.string.driver_empty_message))
                    }
                }
                else -> TripContent(
                    state, trip, onRefresh, onDismissError, onConfirm = { confirming = true },
                    onTakePhoto, onSignature, onRetryEvidence, onDiscardEvidence, onReportIncident, onOpenIncidents,
                )
            }
        }
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
        LogyxConfirmDialog(
            title = stringResource(t),
            message = stringResource(m),
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
    onTakePhoto: () -> Unit,
    onSignature: (ByteArray) -> Unit,
    onRetryEvidence: (String) -> Unit,
    onDiscardEvidence: (String) -> Unit,
    onReportIncident: () -> Unit,
    onOpenIncidents: () -> Unit,
) {
    val total = trip.stops.size
    val done = trip.stops.count { it.status == StopStatus.COMPLETED }
    val current = (done + 1).coerceAtMost(total.coerceAtLeast(1))
    Column(Modifier.fillMaxSize()) {
        if (state.incidentBlocked) {
            AlertBanner(
                stringResource(R.string.driver_incident_banner),
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                kind = MessageKind.Warning,
            )
        }
        val evidenceStep = state.action?.step == DriverStep.DELIVERED
        // En la entrega el panel de evidencia necesita espacio: el mapa se achica.
        Box(Modifier.fillMaxWidth().weight(if (evidenceStep) 0.3f else 0.8f)) {
            val mapDescription = stringResource(R.string.driver_map_a11y, cityLabel(trip.originCity), cityLabel(trip.destinationCity))
            if (LocalInspectionMode.current) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            } else {
                RouteMap(state.origin, state.destination, Modifier.fillMaxSize(), fillParent = true, contentDescription = mapDescription)
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
                if (state.delivered) DeliveredCard(trip) else trip.nextStop()?.let { NextStopCard(it, current, total) }
                if (state.needsEvidence) {
                    EvidencePanel(state.evidence, onTakePhoto, onSignature, onRetryEvidence, onDiscardEvidence)
                }
                Text(
                    "${Fmt.tons(trip.weightKg)} · ${trip.vehiclePlate ?: "—"} · ${trip.cargoDescription ?: stringResource(R.string.driver_cargo_default)}",
                    style = LogyxText.mono, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StopTimeline(trip.stops, large = true)
            }
        }
        val action = state.action
        if (action != null || state.actionError != null) {
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
                if (action != null) {
                    LogyxButton(action.step.actionText(), onConfirm, size = ButtonSize.Xl, loading = state.acting, enabled = state.canAdvance)
                    val hint = when {
                        state.incidentBlocked -> null // ya lo explica el banner de arriba
                        state.photoBlocked && state.photoMissingRejected -> R.string.driver_evidence_missing
                        state.photoBlocked -> R.string.driver_evidence_required_hint
                        else -> null
                    }
                    hint?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LogyxButton(
                            stringResource(R.string.driver_incident_report), onReportIncident, Modifier.weight(1f),
                            variant = ButtonVariant.Secondary, size = ButtonSize.Medium,
                        )
                        if (state.incidentBlocked) {
                            LogyxButton(
                                stringResource(R.string.driver_incident_view), onOpenIncidents, Modifier.weight(1f),
                                variant = ButtonVariant.Secondary, size = ButtonSize.Medium,
                            )
                        }
                    }
                }
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
            EvidenceState.FAILED -> TileStatus.Failed
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
    val shown = items.filter { it.type == DocumentType.DELIVERY_PHOTO || it.type == DocumentType.SIGNATURE }
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
            shown.indices.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { i -> EvidenceThumb(shown[i], captions[i], Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            shown.forEachIndexed { i, item ->
                if (item.state == EvidenceState.FAILED) {
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
    }
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
private fun DriverEvidencePreview() {
    val items = listOf(
        EvidenceItem("a", "s1", "st1", DocumentType.DELIVERY_PHOTO, EvidenceState.UPLOADED, 1f),
        EvidenceItem("b", "s1", "st1", DocumentType.DELIVERY_PHOTO, EvidenceState.FAILED, 0f, error = "Sin conexión"),
    )
    LogyxTheme {
        DriverTripScreen(DriverUiState(loading = false, trip = previewTrip(StopStatus.COMPLETED, StopStatus.ARRIVED).copy(hasActiveIncident = true), allEvidence = items), {}, {})
    }
}

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
