package pe.logyx.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
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
import org.koin.compose.viewmodel.koinViewModel
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.ChipRow
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.InfoChip
import pe.logyx.core.designsystem.components.LogyxBottomSheet
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxFilterChip
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.MoneyField
import pe.logyx.core.designsystem.components.PillTone
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.RouteLine
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.asString
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.label
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.ReturnLoadView
import pe.logyx.core.model.SmartLoadItemView
import pe.logyx.core.model.SmartLoadRouteStop
import pe.logyx.core.model.SmartLoadStatus
import pe.logyx.core.model.SmartLoadView
import pe.logyx.core.model.StopType
import pe.logyx.core.model.VehicleType

// ───────────────────────── Cargas de retorno ─────────────────────────

@Composable
fun ReturnLoadsRoute(onBack: () -> Unit, viewModel: ReturnLoadsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReturnLoadsScreen(
        state, onBack, viewModel::refresh, viewModel::openBid, viewModel::dismissBid, viewModel::onAmount,
        viewModel::requestConfirm, viewModel::submit, viewModel::dismissConfirm, viewModel::onMessageShown,
    )
}

@Composable
fun ReturnLoadsScreen(
    state: ReturnLoadsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onBid: (ReturnLoadView) -> Unit,
    onDismissBid: () -> Unit,
    onAmount: (String) -> Unit,
    onRequestConfirm: () -> Unit,
    onSubmit: () -> Unit,
    onDismissConfirm: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    LogyxScreen(stringResource(R.string.trips_return_title), onBack = onBack, snackbar = snackbar) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (state.items.isEmpty()) {
                            item {
                                EmptyState(
                                    stringResource(R.string.trips_return_empty_title), stringResource(R.string.trips_return_empty_message),
                                    vehicle = VehicleType.TRUCK_LARGE,
                                )
                            }
                        } else {
                            items(state.items, key = { it.requestId }) { r -> ReturnLoadCard(r) { onBid(r) } }
                        }
                    }
                }
            }
        }
    }
    state.bid?.let { bid ->
        LogyxBottomSheet(onDismiss = onDismissBid, title = stringResource(R.string.trips_bid_title)) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RouteLine(
                    cityLabel(bid.load.originCity), cityLabel(bid.load.destinationCity),
                    originSub = bid.load.vehiclePlate?.let { stringResource(R.string.trips_return_vehicle, it) },
                )
                MoneyField(
                    bid.amountText, onAmount, stringResource(R.string.trips_bid_amount),
                    hint = stringResource(R.string.trips_bid_hint, Fmt.money(bid.load.suggestedPrice), Fmt.money(bid.load.priceFloor)),
                    error = bid.amountError?.asString(),
                )
                LogyxButton(
                    stringResource(R.string.trips_bid_submit), onRequestConfirm,
                    variant = ButtonVariant.Money, loading = bid.submitting,
                )
            }
        }
        if (bid.confirming) {
            val amount = pe.logyx.core.designsystem.components.parseMoney(bid.amountText)
            LogyxConfirmDialog(
                title = stringResource(R.string.trips_bid_confirm_title, Fmt.money(amount)),
                message = stringResource(R.string.trips_bid_confirm_message, bid.load.vehiclePlate ?: ""),
                confirmLabel = stringResource(R.string.trips_bid_submit),
                onConfirm = onSubmit, onDismiss = onDismissConfirm,
                dismissLabel = stringResource(R.string.trips_back), loading = bid.submitting,
            )
        }
    }
}

@Composable
private fun ReturnLoadCard(r: ReturnLoadView, onBid: () -> Unit) {
    LogyxCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.money(r.suggestedPrice), style = LogyxText.moneyLarge, color = LogyxTheme.colors.moneyText)
                Text(stringResource(R.string.trips_return_suggested), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (r.alreadyBid) StatusPill(stringResource(R.string.trips_return_already_bid), PillTone.Petrol)
            else InfoChip(stringResource(R.string.trips_return_score, r.matchScore.toInt()), emphasized = true)
        }
        Spacer(Modifier.height(14.dp))
        RouteLine(
            cityLabel(r.originCity), cityLabel(r.destinationCity),
            originSub = stringResource(R.string.trips_return_pickup, Fmt.date(r.pickupDate)),
            destinationSub = r.requesterName,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoChip(kgText(r.weightKg))
            r.cargoType?.let { InfoChip(it.label) }
            r.distanceKm?.let { InfoChip(stringResource(R.string.trips_return_distance, Fmt.km(it))) }
        }
        r.cargoDescription?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            listOfNotNull(
                r.fromCity?.let { stringResource(R.string.trips_return_from, cityLabel(it)) },
                r.vehiclePlate?.let { stringResource(R.string.trips_return_vehicle, it) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        LogyxButton(
            stringResource(R.string.trips_return_bid), onBid,
            variant = ButtonVariant.Money, size = ButtonSize.Medium, enabled = !r.alreadyBid,
        )
    }
}

// ───────────────────────── Sugerencias Smart Load ─────────────────────────

@Composable
fun SmartLoadsRoute(onBack: () -> Unit, viewModel: SmartLoadsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SmartLoadsScreen(
        state, onBack, viewModel::refresh, viewModel::setFilter, viewModel::generate,
        viewModel::askAccept, viewModel::askReject, viewModel::confirm, viewModel::dismissConfirm, viewModel::onMessageShown,
    )
}

private val SMART_FILTERS = listOf(SmartLoadStatus.PENDING, SmartLoadStatus.ACCEPTED, SmartLoadStatus.REJECTED, SmartLoadStatus.EXPIRED)

@Composable
fun SmartLoadsScreen(
    state: SmartLoadsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onFilter: (SmartLoadStatus) -> Unit,
    onGenerate: () -> Unit,
    onAccept: (SmartLoadView) -> Unit,
    onReject: (SmartLoadView) -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    LogyxScreen(stringResource(R.string.trips_smart_title), onBack = onBack, snackbar = snackbar) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ChipRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SMART_FILTERS.forEach { status ->
                    LogyxFilterChip(status.pill().first, selected = status == state.filter, onClick = { onFilter(status) })
                }
            }
            when {
                state.loading -> SkeletonList()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh, Modifier.weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            LogyxButton(
                                stringResource(R.string.trips_smart_generate), onGenerate,
                                variant = ButtonVariant.Secondary, size = ButtonSize.Medium, loading = state.generating, icon = Icons.Rounded.Search,
                            )
                        }
                        if (state.items.isEmpty()) {
                            item {
                                if (state.filter == SmartLoadStatus.PENDING) {
                                    EmptyState(stringResource(R.string.trips_smart_empty_title), stringResource(R.string.trips_smart_empty_message))
                                } else {
                                    EmptyState(stringResource(R.string.trips_smart_empty_filtered_title), stringResource(R.string.trips_smart_empty_filtered_message))
                                }
                            }
                        } else {
                            items(state.items, key = { it.id }) { s -> SmartLoadCard(s, { onAccept(s) }, { onReject(s) }) }
                        }
                    }
                }
            }
        }
    }
    state.pending?.let { action ->
        if (action.accept) {
            LogyxConfirmDialog(
                title = stringResource(R.string.trips_smart_accept_title),
                message = stringResource(R.string.trips_smart_accept_message, action.item.items.size),
                confirmLabel = stringResource(R.string.trips_smart_accept),
                onConfirm = onConfirm, onDismiss = onDismissConfirm,
                dismissLabel = stringResource(R.string.trips_back), loading = state.busy,
            )
        } else {
            LogyxConfirmDialog(
                title = stringResource(R.string.trips_smart_reject_title),
                message = stringResource(R.string.trips_smart_reject_message),
                confirmLabel = stringResource(R.string.trips_smart_reject_confirm),
                onConfirm = onConfirm, onDismiss = onDismissConfirm,
                dismissLabel = stringResource(R.string.trips_back), destructive = true, loading = state.busy,
            )
        }
    }
}

@Composable
private fun SmartLoadCard(s: SmartLoadView, onAccept: () -> Unit, onReject: () -> Unit) {
    LogyxCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.money(s.combinedIncome), style = LogyxText.moneyLarge, color = LogyxTheme.colors.moneyText)
                Text(stringResource(R.string.trips_smart_income), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(s.status.pill())
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            s.vehiclePlate?.let { InfoChip(it) }
            InfoChip(stringResource(R.string.trips_smart_occupancy, Fmt.decimal1(s.occupancyPct)), emphasized = true)
            InfoChip(stringResource(R.string.trips_smart_weight, kgText(s.totalWeightKg), kgText(s.capacityKg)))
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            s.route.forEachIndexed { i, stop ->
                Text(
                    "${i + 1}. " + stringResource(
                        if (stop.type == StopType.PICKUP) R.string.trips_smart_stop_pickup else R.string.trips_smart_stop_delivery,
                        cityLabel(stop.city),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        s.items.forEach { item ->
            Text(
                "${cityLabel(item.originCity)} → ${cityLabel(item.destinationCity)} · ${kgText(item.weightKg)} · ${Fmt.money(item.income)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (s.status == SmartLoadStatus.PENDING) {
            Spacer(Modifier.height(6.dp))
            val left = Fmt.remaining(s.expiresAt)
            Text(
                if (left != null) stringResource(R.string.trips_smart_expires, left) else stringResource(R.string.trips_smart_expired),
                style = MaterialTheme.typography.labelMedium, color = LogyxTheme.colors.onWarningContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LogyxButton(stringResource(R.string.trips_smart_reject), onReject, Modifier.weight(1f), variant = ButtonVariant.Secondary, size = ButtonSize.Medium)
                LogyxButton(stringResource(R.string.trips_smart_accept), onAccept, Modifier.weight(1.4f), variant = ButtonVariant.Money, size = ButtonSize.Medium, enabled = left != null)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SmartLoadCardPreview() {
    LogyxTheme {
        Box(Modifier.padding(16.dp)) {
            SmartLoadCard(
                SmartLoadView(
                    "s1", vehiclePlate = "ASA-202", capacityKg = 20000.0, totalWeightKg = 13500.0, combinedIncome = 14772.9, occupancyPct = 67.5,
                    status = SmartLoadStatus.PENDING, expiresAt = "2099-01-01T00:00:00Z",
                    items = listOf(SmartLoadItemView("r1", "LIMA", "JULIACA", 7500.0, 8211.4)),
                    route = listOf(SmartLoadRouteStop(StopType.PICKUP, "LIMA"), SmartLoadRouteStop(StopType.DELIVERY, "JULIACA")),
                ),
                {}, {},
            )
        }
    }
}
