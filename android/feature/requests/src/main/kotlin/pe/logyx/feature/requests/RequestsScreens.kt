package pe.logyx.feature.requests

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import pe.logyx.core.designsystem.auctionClosureText
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.AuctionCountdown
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.InfoChip
import pe.logyx.core.designsystem.components.KpiTile
import pe.logyx.core.designsystem.components.LiveEffect
import pe.logyx.core.designsystem.components.LogyxBadge
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxBottomSheet
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxDateField
import pe.logyx.core.designsystem.components.LogyxReasonSheet
import pe.logyx.core.designsystem.components.LogyxTextField
import pe.logyx.core.designsystem.components.AlertBanner
import pe.logyx.core.designsystem.components.MessageKind
import pe.logyx.core.designsystem.components.MoneyField
import pe.logyx.core.designsystem.components.TrustScoreBadge
import pe.logyx.core.designsystem.components.asString
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.PillTone
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.RouteLine
import pe.logyx.core.designsystem.components.RouteMap
import pe.logyx.core.designsystem.components.SegmentedControl
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.parseMoney
import pe.logyx.core.designsystem.components.VerifiedBadge
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.label
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.AuctionStatus
import pe.logyx.core.model.AuctionView
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.BidView
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.CounterOfferStatus
import pe.logyx.core.model.CounterOfferView
import pe.logyx.core.model.canReopenAuction
import pe.logyx.core.model.canRepublish
import java.time.LocalDate
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
    LiveEffect(viewModel) { viewModel.liveLoop() }
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
                            state.activeTripBookings?.let { n ->
                                item {
                                    KpiTile(
                                        "$n", stringResource(R.string.requests_kpi_trip_bookings), Modifier.fillMaxWidth(),
                                        stringResource(R.string.requests_kpi_trip_bookings_context),
                                    )
                                }
                            }
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
            if (r.status == RequestStatus.PUBLISHED) {
                if (r.canReopenAuction) StatusPill(stringResource(R.string.requests_list_closed_unassigned), PillTone.Amber)
                else AuctionCountdown(r.auction)
            }
        }
        Spacer(Modifier.height(16.dp))
        RouteLine(
            cityLabel(r.originCity), cityLabel(r.destinationCity),
            distance = r.distanceKm?.let { Fmt.km(it) },
            originSub = stringResource(R.string.requests_pickup, Fmt.date(r.pickupDate)),
        )
        if (r.canReopenAuction) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.requests_list_closed_hint), style = MaterialTheme.typography.bodySmall, color = LogyxTheme.colors.onWarningContainer)
        }
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

/** Callbacks de la pantalla de detalle (un solo objeto para no pasar 20 parámetros). */
class DetailActions(
    val onRefresh: () -> Unit = {},
    val onAskAccept: (BidView) -> Unit = {},
    val onConfirmAccept: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onAskRespond: (BidView, Boolean) -> Unit = { _, _ -> },
    val onConfirmRespond: () -> Unit = {},
    val onAskCancel: () -> Unit = {},
    val onCancelReason: (String) -> Unit = {},
    val onConfirmCancel: () -> Unit = {},
    val onCloseCancel: () -> Unit = {},
    val onAskReopen: () -> Unit = {},
    val onAskRepublish: () -> Unit = {},
    val onRescheduleDate: (LocalDate) -> Unit = {},
    val onConfirmReschedule: () -> Unit = {},
    val onCloseReschedule: () -> Unit = {},
)

@Composable
fun RequestDetailRoute(
    requestId: String,
    onBack: () -> Unit,
    onOpenShipment: (String) -> Unit,
    onOpenChat: (carrierOrgId: String, carrierName: String?) -> Unit = { _, _ -> },
    onOpenThreads: () -> Unit = {},
    onOpenOrganization: (String) -> Unit = {},
    onOpenRequest: (String) -> Unit = {},
    viewModel: RequestDetailViewModel = koinViewModel { parametersOf(requestId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiveEffect(viewModel) { viewModel.liveLoop() }
    LaunchedEffect(state.createdShipmentId) {
        state.createdShipmentId?.let { viewModel.navigationHandled(); onOpenShipment(it) }
    }
    LaunchedEffect(state.openRequestId) {
        state.openRequestId?.let { viewModel.navigationHandled(); onOpenRequest(it) }
    }
    RequestDetailScreen(
        state, onBack,
        actions = DetailActions(
            viewModel::refresh, viewModel::askAccept, viewModel::confirmAccept, viewModel::dismissDialogs, viewModel::onMessageShown,
            viewModel::askRespond, viewModel::confirmRespond,
            viewModel::askCancel, viewModel::onCancelReason, viewModel::confirmCancel, viewModel::closeCancel,
            viewModel::askReopen, viewModel::askRepublish, viewModel::onRescheduleDate, viewModel::confirmReschedule, viewModel::closeReschedule,
        ),
        onOpenChat = onOpenChat, onOpenThreads = onOpenThreads, onOpenOrganization = onOpenOrganization,
        onOpenOriginal = onOpenRequest,
        counter = CounterActions(
            viewModel::openCounter, viewModel::onCounterAmount, viewModel::onCounterMessage,
            viewModel::askConfirmCounter, viewModel::confirmCounter, viewModel::cancelCounterConfirm, viewModel::closeCounter,
        ),
    )
}

@Composable
fun RequestDetailScreen(
    state: RequestDetailUiState,
    onBack: () -> Unit,
    actions: DetailActions = DetailActions(),
    onOpenChat: (String, String?) -> Unit = { _, _ -> },
    onOpenThreads: () -> Unit = {},
    onOpenOrganization: (String) -> Unit = {},
    onOpenOriginal: (String) -> Unit = {},
    counter: CounterActions = CounterActions(),
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, actions.onMessageShown)
    LogyxScreen(stringResource(R.string.requests_detail_title), onBack = onBack, snackbar = snackbar) { padding ->
        val r = state.request
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList(Modifier.fillMaxSize(), count = 2)
                r == null -> ErrorState(state.error ?: stringResource(R.string.requests_not_found), actions.onRefresh)
                else -> Refreshable(state.refreshing, actions.onRefresh) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val mapDescription = stringResource(R.string.requests_map_description)
                        RouteMap(state.origin, state.destination, path = state.route?.points, contentDescription = mapDescription, height = 180.dp)
                        RequestSummaryCard(r, onOpenOriginal)
                        RecoveryBanners(r, state.busy, actions)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                r.auction?.let { stringResource(R.string.requests_bids_title_count, state.bids.size, it.maxBids) }
                                    ?: stringResource(R.string.requests_bids_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            LogyxButton(
                                stringResource(R.string.requests_messages), onOpenThreads,
                                variant = ButtonVariant.Secondary, size = ButtonSize.Medium, fullWidth = false,
                            )
                        }
                        if (state.bids.isEmpty()) {
                            EmptyState(
                                stringResource(R.string.requests_bids_empty_title),
                                stringResource(R.string.requests_bids_empty_message),
                            )
                        }
                        state.bids.forEach { row ->
                            val pending = row.bid.status == BidStatus.PENDING
                            BidComparisonCard(
                                row,
                                canAccept = state.canDecide && pending && !state.busy,
                                onAccept = { actions.onAskAccept(row.bid) },
                                canCounter = state.canDecide && pending && !state.busy,
                                onCounter = { counter.onOpen(row.bid) },
                                carrierCounter = state.counterToRespond(row.bid.id),
                                ownCounter = state.counterWaiting(row.bid.id),
                                busy = state.busy,
                                onRespond = { accept -> actions.onAskRespond(row.bid, accept) },
                                onChat = { row.bid.carrierOrgId?.let { id -> onOpenChat(id, row.bid.carrierName) } },
                                onOpenOrganization = { row.bid.carrierOrgId?.let(onOpenOrganization) },
                            )
                        }
                        if (state.canDecide) {
                            LogyxButton(
                                stringResource(R.string.requests_cancel_request), actions.onAskCancel,
                                variant = ButtonVariant.Destructive, enabled = !state.busy, requiresOperate = true,
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
                onConfirm = actions.onConfirmAccept,
                onDismiss = { if (!state.busy) actions.onDismiss() },
                dismissLabel = stringResource(R.string.requests_dismiss_back),
                loading = state.busy,
            )
        }
        state.respond?.let { resp ->
            val who = resp.bid.carrierName ?: stringResource(R.string.requests_accept_carrier_fallback)
            LogyxConfirmDialog(
                title = stringResource(if (resp.accept) R.string.requests_counter_response_accepted_title else R.string.requests_counter_response_rejected_title),
                message = if (resp.accept) stringResource(R.string.requests_counter_response_accepted_message, who, Fmt.money(resp.counter.amount))
                else stringResource(R.string.requests_counter_response_rejected_message, who),
                confirmLabel = stringResource(if (resp.accept) R.string.requests_counter_accept else R.string.requests_counter_reject),
                onConfirm = actions.onConfirmRespond,
                onDismiss = { if (!state.busy) actions.onDismiss() },
                dismissLabel = stringResource(R.string.requests_dismiss_back),
                destructive = !resp.accept,
                loading = state.busy,
            )
        }
        state.counter?.let { CounterSheet(it, counter) }
        state.cancel?.let { c ->
            LogyxReasonSheet(
                title = stringResource(R.string.requests_cancel_title),
                message = stringResource(R.string.requests_cancel_message),
                reasonLabel = stringResource(R.string.requests_cancel_reason_label),
                value = c.reason, onValueChange = actions.onCancelReason,
                confirmLabel = stringResource(R.string.requests_cancel_confirm),
                onConfirm = actions.onConfirmCancel, onDismiss = actions.onCloseCancel,
                error = c.error?.asString(), optional = true, loading = c.sending,
            )
        }
        state.reschedule?.let { d ->
            val reopen = d.mode == RescheduleMode.REOPEN
            val current = r?.pickupDate?.let { Fmt.date(it) }
            LogyxBottomSheet(
                onDismiss = { if (!d.sending) actions.onCloseReschedule() },
                title = stringResource(if (reopen) R.string.requests_reopen_title else R.string.requests_republish_title),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(if (reopen) R.string.requests_reopen_message else R.string.requests_republish_message),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LogyxDateField(
                        label = stringResource(R.string.requests_reschedule_date_label), value = d.date,
                        onValueChange = actions.onRescheduleDate, optional = true, error = d.error?.asString(),
                        hint = if (current != null) stringResource(R.string.requests_reschedule_date_hint, current)
                        else stringResource(R.string.requests_reschedule_date_hint_none),
                    )
                    LogyxButton(
                        stringResource(if (reopen) R.string.requests_reopen_confirm else R.string.requests_republish_confirm),
                        actions.onConfirmReschedule, variant = ButtonVariant.Primary, loading = d.sending, requiresOperate = true,
                    )
                    LogyxButton(stringResource(R.string.requests_dismiss_back), actions.onCloseReschedule, variant = ButtonVariant.Ghost, size = ButtonSize.Medium, enabled = !d.sending)
                }
            }
        }
    }
}

/** Avisos con la acción siguiente: reabrir una subasta cerrada por tiempo o republicar una cancelada. */
@Composable
private fun RecoveryBanners(r: RequestView, busy: Boolean, actions: DetailActions) {
    if (r.canReopenAuction) {
        AlertBanner(
            stringResource(R.string.requests_closed_unassigned_text), kind = MessageKind.Warning,
            title = stringResource(R.string.requests_closed_unassigned_title),
        )
        LogyxButton(
            stringResource(R.string.requests_reopen), actions.onAskReopen,
            variant = ButtonVariant.Primary, enabled = !busy, requiresOperate = true,
        )
    }
    if (r.canRepublish) {
        AlertBanner(
            r.cancelReason?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.requests_cancelled_reason, it) }
                ?: stringResource(R.string.requests_cancelled_no_reason),
            kind = MessageKind.Info, title = stringResource(R.string.requests_cancelled_title),
        )
        LogyxButton(
            stringResource(R.string.requests_republish), actions.onAskRepublish,
            variant = ButtonVariant.Primary, enabled = !busy, requiresOperate = true,
        )
    }
}

@Composable
private fun RequestSummaryCard(r: RequestView, onOpenOriginal: (String) -> Unit = {}) {
    LogyxCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatusPill(r.status.pill())
            if (r.status == RequestStatus.PUBLISHED) AuctionCountdown(r.auction)
        }
        auctionClosureText(r.auction)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        r.republishedFrom?.let { original ->
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.requests_republished_from),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LogyxButton(
                stringResource(R.string.requests_open_original), { onOpenOriginal(original) },
                variant = ButtonVariant.Ghost, size = ButtonSize.Medium, fullWidth = false,
            )
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
private fun BidComparisonCard(
    row: BidRow,
    canAccept: Boolean,
    onAccept: () -> Unit,
    canCounter: Boolean = false,
    onCounter: () -> Unit = {},
    carrierCounter: CounterOfferView? = null,
    ownCounter: CounterOfferView? = null,
    busy: Boolean = false,
    onRespond: (accept: Boolean) -> Unit = {},
    onChat: () -> Unit = {},
    onOpenOrganization: () -> Unit = {},
) {
    val bid = row.bid
    LogyxCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    bid.carrierName ?: stringResource(R.string.requests_carrier_fallback),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1,
                )
                if (bid.carrierRucVerified) VerifiedBadge()
                TrustScoreBadge(bid.carrierTrustScore, onClick = onOpenOrganization)
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
        if (bid.status == BidStatus.WITHDRAWN) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.requests_bid_withdrawn_note),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (carrierCounter != null) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().background(LogyxTheme.colors.infoContainer, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.requests_counter_from_carrier_generic, Fmt.money(carrierCounter.amount)), style = MaterialTheme.typography.titleSmall)
                carrierCounter.message?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LogyxButton(
                    stringResource(R.string.requests_counter_accept), { onRespond(true) },
                    variant = ButtonVariant.Money, size = ButtonSize.Medium, enabled = !busy, requiresOperate = true,
                )
                LogyxButton(
                    stringResource(R.string.requests_counter_reject), { onRespond(false) },
                    variant = ButtonVariant.Destructive, size = ButtonSize.Medium, enabled = !busy, requiresOperate = true,
                )
            }
        }
        if (ownCounter != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.requests_counter_waiting, Fmt.money(ownCounter.amount)),
                style = MaterialTheme.typography.bodyMedium, color = LogyxTheme.colors.onWarningContainer,
                modifier = Modifier.fillMaxWidth().background(LogyxTheme.colors.warningContainer, RoundedCornerShape(12.dp)).padding(12.dp),
            )
        }
        if (bid.carrierOrgId != null || canCounter) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bid.carrierOrgId != null) {
                    LogyxButton(
                        stringResource(R.string.requests_chat), onChat,
                        modifier = Modifier.weight(1f), variant = ButtonVariant.Secondary, size = ButtonSize.Medium,
                    )
                }
                if (canCounter) {
                    LogyxButton(
                        stringResource(R.string.requests_counter), onCounter,
                        modifier = Modifier.weight(1f), variant = ButtonVariant.Secondary, size = ButtonSize.Medium, requiresOperate = true,
                    )
                }
            }
        }
        if (canAccept) {
            Spacer(Modifier.height(12.dp))
            LogyxButton(
                stringResource(R.string.requests_accept_offer), onAccept,
                variant = ButtonVariant.Money, size = ButtonSize.Medium, requiresOperate = true,
            )
        }
    }
}

// ───────────────────────── Contraoferta ─────────────────────────

/** Callbacks de la hoja "Contraofertar". */
class CounterActions(
    val onOpen: (BidView) -> Unit = {},
    val onAmount: (String) -> Unit = {},
    val onMessage: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onCancelConfirm: () -> Unit = {},
    val onClose: () -> Unit = {},
)

@Composable
private fun CounterSheet(c: CounterDraft, actions: CounterActions) {
    LogyxBottomSheet(onDismiss = actions.onClose, title = stringResource(R.string.requests_counter_title)) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(R.string.requests_counter_current, c.bid.carrierName ?: stringResource(R.string.requests_carrier_fallback), Fmt.money(c.bid.amount)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyField(
                c.amount, actions.onAmount, stringResource(R.string.requests_counter_amount_label),
                error = c.amountError?.asString(), enabled = !c.sending,
            )
            LogyxTextField(
                c.message, actions.onMessage, stringResource(R.string.requests_counter_message_label),
                error = c.messageError?.asString(), optional = true, singleLine = false, minLines = 2, enabled = !c.sending,
                hint = stringResource(R.string.requests_counter_message_hint, MAX_COUNTER_MESSAGE),
            )
            LogyxButton(stringResource(R.string.requests_counter_send), actions.onSubmit, enabled = !c.sending, requiresOperate = true)
            if (c.loadingPrevious || c.previous.isNotEmpty()) {
                Text(stringResource(R.string.requests_counter_previous), style = MaterialTheme.typography.titleSmall)
                c.previous.forEach { PreviousCounterRow(it) }
            }
        }
    }
    if (c.confirming) {
        LogyxConfirmDialog(
            title = stringResource(R.string.requests_counter_confirm_title),
            message = stringResource(
                R.string.requests_counter_confirm_message,
                c.bid.carrierName ?: stringResource(R.string.requests_carrier_fallback),
                Fmt.money(parseMoney(c.amount)),
            ),
            confirmLabel = stringResource(R.string.requests_counter_send),
            onConfirm = actions.onConfirm,
            onDismiss = actions.onCancelConfirm,
            dismissLabel = stringResource(R.string.requests_dismiss_back),
            loading = c.sending,
        )
    }
}

@Composable
private fun PreviousCounterRow(o: CounterOfferView) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(Fmt.money(o.amount), style = LogyxText.moneySmall, color = LogyxTheme.colors.moneyText)
            Text(
                Fmt.dateTime(o.createdAt) + " · " +
                    stringResource(if (o.proposedByCarrier) R.string.requests_counter_by_carrier else R.string.requests_counter_by_you),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusPill(o.status.pill())
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
    LogyxTheme { RequestsScreen(RequestsUiState(loading = false, items = listOf(previewRequest), activeTripBookings = 2), {}, {}) }
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
            RequestDetailUiState(
                loading = false, request = previewRequest, bids = bids,
                counters = mapOf("b2" to CounterOfferView("c1", "b2", amount = 1900.0, status = CounterOfferStatus.PENDING, proposedByCarrier = true, message = "Puedo salir hoy")),
            ),
            onBack = {},
        )
    }
}
