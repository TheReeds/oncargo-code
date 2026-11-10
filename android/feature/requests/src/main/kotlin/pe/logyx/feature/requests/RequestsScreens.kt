package pe.logyx.feature.requests

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.AuctionCountdown
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.InfoChip
import pe.logyx.core.designsystem.components.LogyxBadge
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.PillTone
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.RouteLine
import pe.logyx.core.designsystem.components.RouteMap
import pe.logyx.core.designsystem.components.SegmentedControl
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.VerifiedBadge
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.label
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.AuctionStatus
import pe.logyx.core.model.AuctionView
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.BidView
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.RequestStatus
import pe.logyx.core.model.RequestView
import kotlin.math.abs
import kotlin.math.roundToInt

// ───────────────────────── Lista ─────────────────────────

private enum class StatusFilter(val status: RequestStatus?) {
    All(null), Active(RequestStatus.PUBLISHED), Matched(RequestStatus.MATCHED), Cancelled(RequestStatus.CANCELLED),
}

@Composable
private fun StatusFilter.text() = stringResource(
    when (this) {
        StatusFilter.All -> R.string.requests_filter_all
        StatusFilter.Active -> R.string.requests_filter_active
        StatusFilter.Matched -> R.string.requests_filter_matched
        StatusFilter.Cancelled -> R.string.requests_filter_cancelled
    },
)

@Composable
fun RequestsRoute(onOpenRequest: (String) -> Unit, viewModel: RequestsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RequestsScreen(state, viewModel::refresh, onOpenRequest)
}

@Composable
fun RequestsScreen(state: RequestsUiState, onRefresh: () -> Unit, onOpen: (String) -> Unit) {
    var filterName by rememberSaveable { mutableStateOf(StatusFilter.All.name) }
    val filter = StatusFilter.valueOf(filterName)
    LogyxScreen(stringResource(R.string.requests_title)) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList(Modifier.fillMaxSize())
                state.error != null && state.items.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> {
                    val visible = state.items.filter { filter.status == null || it.status == filter.status }
                    Refreshable(state.refreshing, onRefresh) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (state.items.isNotEmpty()) {
                                item {
                                    val filterDescription = stringResource(R.string.requests_filter_description)
                                    val labels = StatusFilter.entries.associateWith { it.text() }
                                    SegmentedControl(
                                        StatusFilter.entries, filter, { filterName = it.name }, { labels.getValue(it) },
                                        Modifier.semantics { contentDescription = filterDescription },
                                    )
                                }
                            }
                            if (visible.isEmpty()) {
                                item {
                                    if (state.items.isEmpty()) {
                                        EmptyState(
                                            stringResource(R.string.requests_empty_title),
                                            stringResource(R.string.requests_empty_message),
                                        )
                                    } else {
                                        EmptyState(
                                            stringResource(R.string.requests_empty_filtered_title),
                                            stringResource(R.string.requests_empty_filtered_message),
                                        )
                                    }
                                }
                            }
                            items(visible, key = { it.id }) { r -> RequestCard(r) { onOpen(r.id) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(r: RequestView, onClick: () -> Unit) {
    LogyxCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatusPill(r.status.pill())
            if (r.status == RequestStatus.PUBLISHED) AuctionCountdown(r.auction)
        }
        Spacer(Modifier.height(16.dp))
        RouteLine(
            cityLabel(r.originCity), cityLabel(r.destinationCity),
            distance = r.distanceKm?.let { Fmt.km(it) },
            originSub = stringResource(R.string.requests_pickup, Fmt.date(r.pickupDate)),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(r.cargoType.label)
                InfoChip(Fmt.tons(r.weightKg))
            }
            val count = r.auction?.bidCount ?: 0L
            val max = r.auction?.maxBids ?: 5
            Text(
                if (count == 0L) stringResource(R.string.requests_no_bids)
                else pluralStringResource(R.plurals.requests_bids_count, count.toInt(), count.toInt(), max),
                style = MaterialTheme.typography.labelLarge,
                color = if (count > 0) LogyxTheme.colors.onMoneyContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ───────────────────────── Detalle: ofertas ─────────────────────────

@Composable
fun RequestDetailRoute(
    requestId: String,
    onBack: () -> Unit,
    onOpenShipment: (String) -> Unit,
    viewModel: RequestDetailViewModel = koinViewModel { parametersOf(requestId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdShipmentId) {
        state.createdShipmentId?.let { viewModel.navigationHandled(); onOpenShipment(it) }
    }
    RequestDetailScreen(
        state, onBack, viewModel::refresh, viewModel::askAccept, viewModel::confirmAccept,
        viewModel::askCancel, viewModel::confirmCancel, viewModel::dismissDialogs, viewModel::onMessageShown,
    )
}

@Composable
fun RequestDetailScreen(
    state: RequestDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAskAccept: (BidView) -> Unit,
    onConfirmAccept: () -> Unit,
    onAskCancel: () -> Unit,
    onConfirmCancel: () -> Unit,
    onDismiss: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    LogyxScreen(stringResource(R.string.requests_detail_title), onBack = onBack, snackbar = snackbar) { padding ->
        val r = state.request
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList(Modifier.fillMaxSize(), count = 2)
                r == null -> ErrorState(state.error ?: stringResource(R.string.requests_not_found), onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val mapDescription = stringResource(R.string.requests_map_description)
                        RouteMap(state.origin, state.destination, Modifier.semantics { contentDescription = mapDescription }, height = 180.dp)
                        RequestSummaryCard(r)
                        Text(
                            r.auction?.let { stringResource(R.string.requests_bids_title_count, state.bids.size, it.maxBids) }
                                ?: stringResource(R.string.requests_bids_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (state.bids.isEmpty()) {
                            EmptyState(
                                stringResource(R.string.requests_bids_empty_title),
                                stringResource(R.string.requests_bids_empty_message),
                            )
                        }
                        state.bids.forEach { row ->
                            BidComparisonCard(
                                row,
                                canAccept = state.canDecide && row.bid.status == BidStatus.PENDING && !state.busy,
                                onAccept = { onAskAccept(row.bid) },
                            )
                        }
                        if (state.canDecide) {
                            LogyxButton(
                                stringResource(R.string.requests_cancel_request), onAskCancel,
                                variant = ButtonVariant.Destructive, enabled = !state.busy,
                            )
                        }
                    }
                }
            }
        }
        state.confirmBid?.let { bid ->
            LogyxConfirmDialog(
                title = stringResource(R.string.requests_accept_title),
                message = stringResource(
                    R.string.requests_accept_message,
                    bid.carrierName ?: stringResource(R.string.requests_accept_carrier_fallback),
                    Fmt.money(bid.amount),
                    bid.vehicleType?.let { vt ->
                    val plate = bid.vehiclePlate?.takeIf { it.isNotBlank() }
                    if (plate != null) stringResource(R.string.requests_accept_vehicle, vt.label, plate)
                    else stringResource(R.string.requests_accept_vehicle_no_plate, vt.label)
                    }.orEmpty(),
                ),
                confirmLabel = stringResource(R.string.requests_accept_offer),
                onConfirm = onConfirmAccept,
                onDismiss = { if (!state.busy) onDismiss() },
                dismissLabel = stringResource(R.string.requests_dismiss_back),
                loading = state.busy,
            )
        }
        if (state.confirmCancel) {
            LogyxConfirmDialog(
                title = stringResource(R.string.requests_cancel_title),
                message = stringResource(R.string.requests_cancel_message),
                confirmLabel = stringResource(R.string.requests_cancel_confirm),
                onConfirm = onConfirmCancel,
                onDismiss = { if (!state.busy) onDismiss() },
                dismissLabel = stringResource(R.string.requests_cancel_dismiss),
                destructive = true,
                loading = state.busy,
            )
        }
    }
}

@Composable
private fun RequestSummaryCard(r: RequestView) {
    LogyxCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatusPill(r.status.pill())
            if (r.status == RequestStatus.PUBLISHED) AuctionCountdown(r.auction)
        }
        Spacer(Modifier.height(16.dp))
        RouteLine(
            cityLabel(r.originCity), cityLabel(r.destinationCity),
            distance = r.distanceKm?.let { Fmt.km(it) }, originSub = r.originAddress, destinationSub = r.destinationAddress,
        )
        Spacer(Modifier.height(16.dp))
        Text(r.cargoDescription ?: r.cargoType.label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(r.cargoType.label)
            InfoChip(Fmt.tons(r.weightKg))
            InfoChip(stringResource(R.string.requests_pickup, Fmt.date(r.pickupDate)))
        }
        r.minVehicleType?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.requests_min_vehicle, it.label),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (r.requesterRucVerified) {
            Spacer(Modifier.height(8.dp))
            VerifiedBadge(text = stringResource(R.string.requests_requester_verified))
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            PriceInfo(stringResource(R.string.requests_suggested_price), Fmt.money(r.suggestedPrice))
            PriceInfo(stringResource(R.string.requests_price_floor), Fmt.money(r.priceFloor))
        }
    }
}

@Composable
private fun PriceInfo(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = LogyxText.moneySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun BidComparisonCard(row: BidRow, canAccept: Boolean, onAccept: () -> Unit) {
    val bid = row.bid
    LogyxCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    bid.carrierName ?: stringResource(R.string.requests_carrier_fallback),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1,
                )
                if (bid.carrierRucVerified) VerifiedBadge()
                Text(Fmt.dateTime(bid.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Fmt.money(bid.amount), style = LogyxText.moneyMedium, color = LogyxTheme.colors.moneyText)
                row.diffVsSuggestedPct?.let { d ->
                    val pct = abs(d).roundToInt()
                    val cheaper = d <= 0.5
                    Text(
                        when {
                            d < -0.5 -> stringResource(R.string.requests_diff_below, pct)
                            d > 0.5 -> stringResource(R.string.requests_diff_above, pct)
                            else -> stringResource(R.string.requests_diff_equal)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (cheaper) LogyxTheme.colors.onMoneyContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        bid.vehicleType?.let { vt ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(vt.label)
                bid.vehiclePlate?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = LogyxText.mono, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (!bid.notes.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.requests_notes_quote, bid.notes.orEmpty()),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.isBest || bid.status != BidStatus.PENDING) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.isBest) LogyxBadge(stringResource(R.string.requests_best_price), tone = PillTone.Emerald)
                if (bid.status != BidStatus.PENDING) StatusPill(bid.status.pill())
            }
        }
        if (canAccept) {
            Spacer(Modifier.height(16.dp))
            LogyxButton(
                stringResource(R.string.requests_accept_offer), onAccept,
                variant = ButtonVariant.Money, size = ButtonSize.Medium,
            )
        }
    }
}

// ───────────────────────── Previews ─────────────────────────

private val previewRequest = RequestView(
    id = "r1", originCity = "LIMA", destinationCity = "JULIACA", cargoType = CargoType.GENERAL, weightKg = 2500.0,
    distanceKm = 1010.0, suggestedPrice = 2000.0, priceFloor = 1500.0, status = RequestStatus.PUBLISHED,
    pickupDate = "2026-11-28", cargoDescription = "Cajas de textiles",
    auction = AuctionView("a1", AuctionStatus.OPEN, "2099-01-01T00:00:00-05:00", 5, 2),
)

@Preview(showBackground = true)
@Composable
private fun RequestsScreenPreview() {
    LogyxTheme { RequestsScreen(RequestsUiState(loading = false, items = listOf(previewRequest)), {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun RequestDetailScreenPreview() {
    val bids = buildBidRows(
        listOf(
            BidView("b1", "r1", carrierName = "Transportes Andes", carrierRucVerified = true, amount = 1700.0, status = BidStatus.PENDING),
            BidView("b2", "r1", carrierName = "Cargo Sur", amount = 2100.0, status = BidStatus.PENDING, notes = "Salgo mañana temprano"),
        ),
        2000.0,
    )
    LogyxTheme {
        RequestDetailScreen(
            RequestDetailUiState(loading = false, request = previewRequest, bids = bids), {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}
