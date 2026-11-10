package pe.logyx.feature.driver

import androidx.compose.foundation.background
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

@Composable
fun DriverTripRoute(viewModel: DriverTripViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DriverTripScreen(state, viewModel::refresh, viewModel::advance, viewModel::onNoticeShown, viewModel::dismissActionError)
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
                else -> TripContent(state, trip, onRefresh, onDismissError, onConfirm = { confirming = true })
            }
        }
    }

    val action = state.action
    if (confirming && action != null) {
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
) {
    val total = trip.stops.size
    val done = trip.stops.count { it.status == StopStatus.COMPLETED }
    val current = (done + 1).coerceAtMost(total.coerceAtLeast(1))
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(0.8f)) {
            val mapDescription = stringResource(R.string.driver_map_a11y, cityLabel(trip.originCity), cityLabel(trip.destinationCity))
            if (LocalInspectionMode.current) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            } else {
                RouteMap(state.origin, state.destination, Modifier.fillMaxSize().semantics { contentDescription = mapDescription }, fillParent = true)
            }
            if (total > 0 && !state.delivered) {
                LogyxBadge(
                    stringResource(R.string.driver_stop_progress, current, total),
                    Modifier.align(Alignment.TopStart).padding(12.dp),
                    tone = PillTone.Petrol,
                )
            }
        }
        Refreshable(state.refreshing, onRefresh, Modifier.weight(1.2f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.delivered) DeliveredCard(trip) else trip.nextStop()?.let { NextStopCard(it, current, total) }
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
                    LogyxButton(action.step.actionText(), onConfirm, size = ButtonSize.Xl, loading = state.acting)
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
