package pe.logyx.feature.loads

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.AlertBanner
import pe.logyx.core.designsystem.components.AuctionCountdown
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.ChipRow
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.InfoChip
import pe.logyx.core.designsystem.components.KpiTile
import pe.logyx.core.designsystem.components.LoadCard
import pe.logyx.core.designsystem.components.LogyxBottomSheet
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxCombobox
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxDateRangeField
import pe.logyx.core.designsystem.components.LogyxDropdown
import pe.logyx.core.designsystem.components.LogyxFilterChip
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.LogyxTextField
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.MessageKind
import pe.logyx.core.designsystem.components.MoneyField
import pe.logyx.core.designsystem.components.PriceTag
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.RouteLine
import pe.logyx.core.designsystem.components.RouteMap
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.SkeletonLoadCard
import pe.logyx.core.designsystem.components.VerifiedBadge
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.moneyToInput
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.label
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.AuctionStatus
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.isCompatibleWith
import java.time.LocalDate

// ───────────────────────── Home "Tu actividad" ─────────────────────────

@Composable
fun CarrierHomeRoute(
    onOpenLoad: (String) -> Unit,
    onOpenFeed: () -> Unit,
    onOpenBids: () -> Unit,
    onOpenShipment: (String) -> Unit,
    viewModel: CarrierHomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CarrierHomeScreen(state, viewModel::refresh, onOpenLoad, onOpenFeed, onOpenBids, onOpenShipment)
}

@Composable
fun CarrierHomeScreen(
    state: CarrierHomeUiState,
    onRefresh: () -> Unit,
    onOpenLoad: (String) -> Unit,
    onOpenFeed: () -> Unit,
    onOpenBids: () -> Unit,
    onOpenShipment: (String) -> Unit,
) {
    LogyxScreen(stringResource(R.string.loads_home_title)) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList()
                state.error != null && state.suggested.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    LazyColumn(
                        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            val first = state.userName.substringBefore(' ').ifBlank { stringResource(R.string.loads_home_default_name) }
                            Text(stringResource(R.string.loads_home_greeting, first), style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (state.compatibleCount > 0) pluralStringResource(R.plurals.loads_home_summary, state.compatibleCount, state.compatibleCount)
                                else stringResource(R.string.loads_home_summary_none),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                KpiTile(
                                    "${state.compatibleCount}", stringResource(R.string.loads_kpi_for_you), Modifier.weight(1f),
                                    stringResource(R.string.loads_kpi_compatible_context),
                                )
                                KpiTile(
                                    "${state.pendingBids}", stringResource(R.string.loads_kpi_bids), Modifier.weight(1f),
                                    stringResource(R.string.loads_kpi_bids_context, state.totalBids),
                                    accent = LogyxTheme.colors.moneyText,
                                )
                            }
                        }
                        if (state.activeShipments != null || state.deliveredThisMonth != null) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    KpiTile(
                                        "${state.activeShipments ?: 0}", stringResource(R.string.loads_kpi_active), Modifier.weight(1f),
                                        stringResource(R.string.loads_kpi_active_context),
                                    )
                                    KpiTile(
                                        "${state.deliveredThisMonth ?: 0}", stringResource(R.string.loads_kpi_delivered), Modifier.weight(1f),
                                        stringResource(R.string.loads_kpi_delivered_context),
                                    )
                                }
                            }
                        }
                        state.activeShipment?.let { s -> item { ActiveShipmentCard(s) { onOpenShipment(s.id) } } }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.loads_home_suggested), style = MaterialTheme.typography.titleMedium)
                                LogyxButton(
                                    stringResource(R.string.loads_home_see_all), onOpenFeed,
                                    variant = ButtonVariant.Ghost, size = ButtonSize.Medium, fullWidth = false,
                                )
                            }
                        }
                        if (state.suggested.isEmpty()) {
                            item {
                                EmptyState(
                                    stringResource(R.string.loads_home_empty_title),
                                    stringResource(
                                        if (state.vehicles.isEmpty()) R.string.loads_home_empty_no_fleet else R.string.loads_home_empty_with_fleet,
                                    ),
                                    actionLabel = stringResource(R.string.loads_home_empty_action), onAction = onOpenFeed,
                                )
                            }
                        } else {
                            items(state.suggested, key = { it.id }) { r -> LoadCard(r, compatible = true, onClick = { onOpenLoad(r.id) }) }
                        }
                        item {
                            LogyxButton(
                                stringResource(R.string.loads_home_see_my_bids), onOpenBids,
                                variant = ButtonVariant.Secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveShipmentCard(s: ShipmentView, onClick: () -> Unit) {
    LogyxCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocalShipping, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.loads_active_trip), style = MaterialTheme.typography.titleMedium)
            }
            StatusPill(s.status.pill())
        }
        Spacer(Modifier.height(16.dp))
        RouteLine(
            cityLabel(s.originCity), cityLabel(s.destinationCity),
            originSub = s.vehiclePlate, destinationSub = s.driverName ?: stringResource(R.string.loads_no_driver),
        )
    }
}

// ───────────────────────── Feed ─────────────────────────

@Composable
fun LoadsFeedRoute(onOpenLoad: (String) -> Unit, viewModel: LoadsFeedViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoadsFeedScreen(
        state, viewModel::refresh, viewModel::toggle, onOpenLoad,
        onLoadMore = viewModel::loadMore, onOrigin = viewModel::setOrigin, onDestination = viewModel::setDestination,
        onDates = viewModel::setDates, onCargoType = viewModel::setCargoType, onClearFilters = viewModel::clearFilters,
        onMessageShown = viewModel::messageShown,
    )
}

private data class FilterMeta(val filter: LoadFilter, @StringRes val label: Int, val icon: ImageVector)

private val filterMeta: List<FilterMeta> = listOf(
    FilterMeta(LoadFilter.MyVehicle, R.string.loads_filter_my_vehicle, Icons.Rounded.LocalShipping),
    FilterMeta(LoadFilter.BestPay, R.string.loads_filter_best_pay, Icons.Rounded.Paid),
    FilterMeta(LoadFilter.Nearby, R.string.loads_filter_nearby, Icons.Rounded.NearMe),
)

@Composable
fun LoadsFeedScreen(
    state: LoadsFeedUiState,
    onRefresh: () -> Unit,
    onToggle: (LoadFilter) -> Unit,
    onOpenLoad: (String) -> Unit,
    onLoadMore: () -> Unit = {},
    onOrigin: (CityItem?) -> Unit = {},
    onDestination: (CityItem?) -> Unit = {},
    onDates: (LocalDate?, LocalDate?) -> Unit = { _, _ -> },
    onCargoType: (CargoType?) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onMessageShown: () -> Unit = {},
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    var showFilters by rememberSaveable { mutableStateOf(false) }
    LogyxScreen(stringResource(R.string.loads_feed_title), snackbar = snackbar) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ChipRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                filterMeta.forEach { m ->
                    LogyxFilterChip(
                        label = stringResource(m.label), selected = m.filter in state.filters,
                        onClick = { onToggle(m.filter) }, leadingIcon = m.icon,
                    )
                }
                LogyxFilterChip(
                    label = if (state.advancedCount > 0) stringResource(R.string.loads_filter_more_count, state.advancedCount)
                    else stringResource(R.string.loads_filter_more),
                    selected = state.advancedCount > 0, onClick = { showFilters = true }, leadingIcon = Icons.Rounded.Tune,
                )
            }
            when {
                state.loading -> SkeletonList()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh, Modifier.weight(1f)) {
                    val visible = state.visible
                    val listState = rememberLazyListState()
                    val nearEnd by remember {
                        derivedStateOf {
                            val info = listState.layoutInfo
                            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
                        }
                    }
                    LaunchedEffect(nearEnd, state.items.size, state.hasMore) { if (nearEnd && state.hasMore) onLoadMore() }
                    LazyColumn(
                        Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (state.nearbyUnavailable) {
                            item {
                                AlertBanner(
                                    stringResource(R.string.loads_feed_nearby_banner), kind = MessageKind.Warning,
                                    title = stringResource(R.string.loads_feed_nearby_banner_title),
                                )
                            }
                        }
                        if (visible.isNotEmpty()) {
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        pluralStringResource(R.plurals.loads_feed_count, state.total, state.total),
                                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (state.hasActiveFilters) {
                                        LogyxButton(
                                            stringResource(R.string.loads_filter_clear), onClearFilters,
                                            variant = ButtonVariant.Ghost, size = ButtonSize.Medium, fullWidth = false,
                                        )
                                    }
                                }
                            }
                        } else {
                            item {
                                EmptyState(
                                    title = stringResource(if (state.hasActiveFilters) R.string.loads_feed_no_match_title else R.string.loads_feed_empty_title),
                                    message = stringResource(if (state.hasActiveFilters) R.string.loads_feed_no_match_message else R.string.loads_feed_empty_message),
                                    actionLabel = if (state.hasActiveFilters) stringResource(R.string.loads_filter_clear) else null,
                                    onAction = if (state.hasActiveFilters) onClearFilters else null,
                                )
                            }
                        }
                        items(visible, key = { it.id }) { r -> LoadCard(r, state.isCompatible(r), { onOpenLoad(r.id) }) }
                        if (state.loadingMore) item { SkeletonLoadCard() }
                    }
                }
            }
        }
        if (showFilters) {
            FiltersSheet(state, { showFilters = false }, onOrigin, onDestination, onDates, onCargoType, onClearFilters)
        }
    }
}

@Composable
private fun FiltersSheet(
    state: LoadsFeedUiState,
    onDismiss: () -> Unit,
    onOrigin: (CityItem?) -> Unit,
    onDestination: (CityItem?) -> Unit,
    onDates: (LocalDate?, LocalDate?) -> Unit,
    onCargoType: (CargoType?) -> Unit,
    onClear: () -> Unit,
) {
    val anyCargo = stringResource(R.string.loads_filter_cargo_any)
    LogyxBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.loads_filters_title)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LogyxCombobox(
                label = stringResource(R.string.loads_filter_origin), options = state.cities, selected = state.originCity,
                onSelected = onOrigin, optionLabel = { it.name }, clearable = true,
                placeholder = stringResource(R.string.loads_filter_city_any),
            )
            LogyxCombobox(
                label = stringResource(R.string.loads_filter_destination), options = state.cities, selected = state.destinationCity,
                onSelected = onDestination, optionLabel = { it.name }, clearable = true,
                placeholder = stringResource(R.string.loads_filter_city_any),
            )
            LogyxDateRangeField(
                label = stringResource(R.string.loads_filter_dates), start = state.dateFrom, end = state.dateTo,
                onValueChange = { from, to -> onDates(from, to) }, optional = true,
            )
            LogyxDropdown(
                label = stringResource(R.string.loads_filter_cargo_type),
                options = listOf<CargoType?>(null) + CargoType.entries, selected = state.cargoType,
                onSelected = onCargoType, optionLabel = { it?.label ?: anyCargo },
                placeholder = anyCargo, optional = true,
            )
            LogyxButton(
                stringResource(R.string.loads_filter_apply, pluralStringResource(R.plurals.loads_feed_count, state.total, state.total)),
                onDismiss, variant = ButtonVariant.Primary,
            )
            if (state.hasActiveFilters) {
                LogyxButton(stringResource(R.string.loads_filter_clear), onClear, variant = ButtonVariant.Ghost, size = ButtonSize.Medium)
            }
        }
    }
}

// ───────────────────────── Detalle ─────────────────────────

@Composable
fun LoadDetailRoute(requestId: String, onBack: () -> Unit, viewModel: LoadDetailViewModel = koinViewModel { parametersOf(requestId) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoadDetailScreen(
        state, onBack, viewModel::refresh, viewModel::openBidSheet, viewModel::dismissBidSheet,
        viewModel::selectVehicle, viewModel::onAmountChange, viewModel::onNotesChange,
        viewModel::requestConfirm, viewModel::dismissConfirm, viewModel::submitBid, viewModel::messageShown,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoadDetailScreen(
    state: LoadDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenBid: () -> Unit,
    onDismissBid: () -> Unit,
    onSelectVehicle: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onRequestConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onConfirmBid: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    LogyxScreen(stringResource(R.string.loads_detail_title), onBack = onBack, snackbar = snackbar) { padding ->
        val r = state.request
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList(count = 2)
                r == null -> ErrorState(state.error ?: stringResource(R.string.loads_detail_not_found), onRefresh)
                else -> Column(Modifier.fillMaxSize()) {
                    Refreshable(state.refreshing, onRefresh, Modifier.weight(1f)) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            val mapDescription = stringResource(R.string.loads_map_description, cityLabel(r.originCity), cityLabel(r.destinationCity))
                            RouteMap(state.origin, state.destination, Modifier.semantics { contentDescription = mapDescription }, height = 220.dp)
                            LogyxCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                    PriceTag(r.suggestedPrice, label = stringResource(R.string.loads_reference_price))
                                    AuctionCountdown(r.auction)
                                }
                                Spacer(Modifier.height(16.dp))
                                RouteLine(
                                    cityLabel(r.originCity), cityLabel(r.destinationCity),
                                    distance = r.distanceKm?.let { Fmt.km(it) },
                                    originSub = r.originAddress, destinationSub = r.destinationAddress,
                                )
                            }
                            LogyxCard(Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.loads_cargo_section), style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(r.cargoDescription ?: r.cargoType.label, style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(12.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InfoChip(r.cargoType.label)
                                    InfoChip(Fmt.tons(r.weightKg))
                                    r.volumeM3?.let { InfoChip(stringResource(R.string.loads_volume, Fmt.decimal1(it))) }
                                    r.minVehicleType?.let { InfoChip(stringResource(R.string.loads_min_vehicle_chip, it.label), icon = Icons.Rounded.LocalShipping) }
                                    InfoChip(stringResource(R.string.loads_pickup_on, Fmt.date(r.pickupDate)))
                                    r.auction?.let { InfoChip(stringResource(R.string.loads_bids_count, it.bidCount, it.maxBids)) }
                                    if (r.isCompatibleWith(state.vehicles)) {
                                        InfoChip(stringResource(R.string.loads_compatible_with_fleet), emphasized = true, icon = Icons.Rounded.CheckCircle)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        stringResource(R.string.loads_published_by, r.requesterName ?: "—"),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (r.requesterRucVerified) VerifiedBadge()
                                }
                                r.minVehicleType?.let {
                                    Text(
                                        stringResource(R.string.loads_min_vehicle, it.label),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                r.priceFloor?.let {
                                    Text(
                                        stringResource(R.string.loads_min_bid, Fmt.money(it)),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            r.myBid?.let { bid ->
                                LogyxCard(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.loads_your_bid), style = MaterialTheme.typography.titleMedium)
                                        StatusPill(bid.status.pill())
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    PriceTag(bid.amount, large = false)
                                    bid.notes?.takeIf { it.isNotBlank() }?.let {
                                        Spacer(Modifier.height(4.dp))
                                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (r.myBid == null && r.auction != null && r.auction?.status != AuctionStatus.OPEN) {
                                AlertBanner(
                                    stringResource(R.string.loads_auction_closed), kind = MessageKind.Info,
                                    title = stringResource(R.string.loads_auction_closed_title),
                                )
                            }
                        }
                    }
                    val canBid = r.myBid == null && r.auction?.status == AuctionStatus.OPEN
                    if (canBid) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            LogyxButton(stringResource(R.string.loads_action_bid), onOpenBid, variant = ButtonVariant.Money)
                        }
                    }
                }
            }
        }
        if (state.form != null && r != null) {
            BidSheet(state, r, onDismissBid, onSelectVehicle, onAmountChange, onNotesChange, onRequestConfirm, onDismissConfirm, onConfirmBid)
        }
    }
}

@Composable
private fun BidSheet(
    state: LoadDetailUiState,
    request: RequestView,
    onDismiss: () -> Unit,
    onSelectVehicle: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onRequestConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onConfirm: () -> Unit,
) {
    val form = state.form ?: return
    val vehicles = state.compatibleVehicles
    val selected = vehicles.firstOrNull { it.id == form.vehicleId }
    val resources = LocalContext.current.resources
    LogyxBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.loads_sheet_title)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.loads_sheet_route, cityLabel(request.originCity), cityLabel(request.destinationCity), Fmt.tons(request.weightKg)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vehicles.isEmpty()) {
                AlertBanner(
                    stringResource(R.string.loads_sheet_no_vehicle, Fmt.tons(request.weightKg)), kind = MessageKind.Error,
                    title = stringResource(R.string.loads_sheet_no_vehicle_title),
                )
            } else {
                LogyxDropdown(
                    label = stringResource(R.string.loads_sheet_vehicle), options = vehicles, selected = selected,
                    onSelected = { onSelectVehicle(it.id) }, optionLabel = { it.plate },
                    placeholder = stringResource(R.string.loads_sheet_vehicle_placeholder),
                    error = form.vehicleError,
                    optionSupporting = { v -> resources.getString(R.string.loads_sheet_vehicle_option, v.type.label, Fmt.tons(v.maxWeightKg)) },
                )
            }
            MoneyField(
                value = form.amountText, onValueChange = onAmountChange, label = stringResource(R.string.loads_sheet_amount),
                hint = bidHint(request), error = form.amountError,
            )
            ChipRow {
                request.priceFloor?.let { QuickAmount(stringResource(R.string.loads_quick_floor, Fmt.moneyShort(it)), it, form.amountText, onAmountChange) }
                request.suggestedPrice?.let { QuickAmount(stringResource(R.string.loads_quick_reference, Fmt.moneyShort(it)), it, form.amountText, onAmountChange) }
            }
            LogyxTextField(
                value = form.notes, onValueChange = onNotesChange, label = stringResource(R.string.loads_sheet_notes),
                optional = true, singleLine = false, minLines = 2, maxLines = 4,
                hint = stringResource(R.string.loads_sheet_notes_hint, form.notes.length),
            )
            if (form.error != null) {
                AlertBanner(form.error, kind = MessageKind.Error, title = stringResource(R.string.loads_sheet_error_title))
            }
            LogyxButton(
                stringResource(R.string.loads_sheet_submit), onRequestConfirm, variant = ButtonVariant.Money,
                loading = form.submitting, enabled = vehicles.isNotEmpty(),
            )
        }
    }
    if (form.confirming) {
        LogyxConfirmDialog(
            title = stringResource(R.string.loads_confirm_title, Fmt.money(form.amount)),
            message = if (selected != null) stringResource(R.string.loads_confirm_message, selected.plate)
            else stringResource(R.string.loads_confirm_message_no_vehicle),
            confirmLabel = stringResource(R.string.loads_sheet_submit),
            onConfirm = onConfirm, onDismiss = onDismissConfirm,
            dismissLabel = stringResource(R.string.loads_confirm_review),
            loading = form.submitting,
        )
    }
}

@Composable
private fun bidHint(request: RequestView): String? = when {
    request.priceFloor != null && request.suggestedPrice != null ->
        stringResource(R.string.loads_sheet_amount_hint_both, Fmt.money(request.priceFloor), Fmt.money(request.suggestedPrice))
    request.priceFloor != null -> stringResource(R.string.loads_sheet_amount_hint_floor, Fmt.money(request.priceFloor))
    request.suggestedPrice != null -> stringResource(R.string.loads_sheet_amount_hint_ref, Fmt.money(request.suggestedPrice))
    else -> null
}

@Composable
private fun QuickAmount(label: String, amount: Double, current: String, onAmountChange: (String) -> Unit) {
    val text = moneyToInput(amount)
    LogyxFilterChip(label = label, selected = current == text, onClick = { onAmountChange(text) })
}

// ───────────────────────── Mis ofertas ─────────────────────────

@Composable
fun MyBidsRoute(onOpenLoad: (String) -> Unit, viewModel: MyBidsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyBidsScreen(state, viewModel::refresh, onOpenLoad)
}

@Composable
fun MyBidsScreen(state: MyBidsUiState, onRefresh: () -> Unit, onOpenLoad: (String) -> Unit) {
    LogyxScreen(stringResource(R.string.loads_bids_title)) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (state.items.isEmpty()) {
                            item {
                                EmptyState(stringResource(R.string.loads_bids_empty_title), stringResource(R.string.loads_bids_empty_message))
                            }
                        }
                        items(state.items, key = { it.id }) { r -> BidRow(r) { onOpenLoad(r.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun BidRow(r: RequestView, onClick: () -> Unit) {
    val bid = r.myBid ?: return
    LogyxCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.money(bid.amount), style = LogyxText.moneyMedium, color = LogyxTheme.colors.moneyText)
            StatusPill(bid.status.pill())
        }
        Spacer(Modifier.height(12.dp))
        RouteLine(
            cityLabel(r.originCity), cityLabel(r.destinationCity), distance = r.distanceKm?.let { Fmt.km(it) },
            originSub = stringResource(R.string.loads_pickup_on, Fmt.date(r.pickupDate)),
        )
        if (bid.vehiclePlate != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                bid.vehicleType?.let { stringResource(R.string.loads_bid_vehicle, it.label, bid.vehiclePlate!!) }
                    ?: stringResource(R.string.loads_bid_vehicle_plate, bid.vehiclePlate!!),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        FlowRow2 {
            InfoChip(r.cargoType.label)
            InfoChip(Fmt.tons(r.weightKg))
            if (bid.status == BidStatus.PENDING) AuctionCountdown(r.auction)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow2(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

// ───────────────────────── Previews ─────────────────────────

@Preview(showBackground = true)
@Composable
private fun HomeLoadingPreview() {
    LogyxTheme { CarrierHomeScreen(CarrierHomeUiState(), {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun HomeEmptyPreview() {
    LogyxTheme { CarrierHomeScreen(CarrierHomeUiState(loading = false, userName = "Carla Quispe"), {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun FeedErrorPreview() {
    LogyxTheme { LoadsFeedScreen(LoadsFeedUiState(loading = false, error = "No pudimos conectarnos. Revisa tu conexión e inténtalo de nuevo."), {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun FeedEmptyPreview() {
    LogyxTheme { LoadsFeedScreen(LoadsFeedUiState(loading = false, filters = setOf(LoadFilter.Nearby)), {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun DetailErrorPreview() {
    LogyxTheme { LoadDetailScreen(LoadDetailUiState(loading = false), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun BidsEmptyPreview() {
    LogyxTheme { MyBidsScreen(MyBidsUiState(loading = false), {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun BidsLoadingPreview() {
    LogyxTheme { MyBidsScreen(MyBidsUiState(), {}, {}) }
}
