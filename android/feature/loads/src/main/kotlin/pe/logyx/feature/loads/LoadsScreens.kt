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
import androidx.compose.material.icons.rounded.Route
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.AlertBanner
import pe.logyx.core.designsystem.components.AuctionCountdown
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.TrustScoreBadge
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.ChipRow
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.InfoChip
import pe.logyx.core.designsystem.components.KpiTile
import pe.logyx.core.designsystem.components.LiveEffect
import pe.logyx.core.designsystem.components.LoadCard
import pe.logyx.core.designsystem.components.LoadingMoreFooter
import pe.logyx.core.designsystem.components.LogyxBottomSheet
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxCombobox
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxDateRangeField
import pe.logyx.core.designsystem.components.LogyxDropdown
import pe.logyx.core.designsystem.components.LogyxReasonSheet
import pe.logyx.core.designsystem.components.SegmentedControl
import pe.logyx.core.designsystem.components.BidStandingRow
import pe.logyx.core.designsystem.components.VehicleChoiceList
import pe.logyx.core.designsystem.auctionClosureText
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
import pe.logyx.core.designsystem.components.PillTone
import pe.logyx.core.designsystem.components.moneyToInput
import pe.logyx.core.designsystem.components.asString
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.label
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.AuctionStatus
import pe.logyx.core.model.BidOutcome
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.CounterOfferStatus
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.VehicleType
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.CounterOfferView
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.ReturnLoadHintView
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.RequestStatus
import pe.logyx.core.model.isCompatibleWith
import pe.logyx.core.model.myBidOutcome
import java.time.LocalDate

// ───────────────────────── Home "Tu actividad" ─────────────────────────

@Composable
fun CarrierHomeRoute(
    onOpenLoad: (String) -> Unit,
    onOpenFeed: () -> Unit,
    onOpenBids: () -> Unit,
    onOpenShipment: (String) -> Unit,
    onOpenTrips: () -> Unit,
    onOpenReturnLoads: () -> Unit,
    onOpenSmartLoads: () -> Unit,
    viewModel: CarrierHomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiveEffect(viewModel) { viewModel.liveLoop() }
    CarrierHomeScreen(state, viewModel::refresh, onOpenLoad, onOpenFeed, onOpenBids, onOpenShipment, onOpenTrips, onOpenReturnLoads, onOpenSmartLoads)
}

@Composable
fun CarrierHomeScreen(
    state: CarrierHomeUiState,
    onRefresh: () -> Unit,
    onOpenLoad: (String) -> Unit,
    onOpenFeed: () -> Unit,
    onOpenBids: () -> Unit,
    onOpenShipment: (String) -> Unit,
    onOpenTrips: () -> Unit = {},
    onOpenReturnLoads: () -> Unit = {},
    onOpenSmartLoads: () -> Unit = {},
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
                        item { TripsActivityCard(state.openTrips ?: 0, state.pendingTripBookings ?: 0, onOpenTrips) }
                        if ((state.pendingSmartLoads ?: 0) > 0) {
                            item { SmartLoadsCard(state.pendingSmartLoads ?: 0, onOpenSmartLoads) }
                        }
                        if ((state.returnLoadCount ?: 0) > 0) {
                            item { ReturnLoadsSection(state.returnLoadCount ?: 0, state.topReturnLoads, onOpenReturnLoads) }
                        }
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

/** Viajes publicados: abiertos y reservas por confirmar, con acceso a "Mis viajes". */
@Composable
private fun TripsActivityCard(openTrips: Int, pendingBookings: Int, onClick: () -> Unit) {
    LogyxCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Route, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.loads_home_trips_title), style = MaterialTheme.typography.titleMedium)
            }
            if (pendingBookings > 0) {
                StatusPill(pluralStringResource(R.plurals.loads_home_trips_pending, pendingBookings, pendingBookings), PillTone.Amber)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (openTrips > 0) pluralStringResource(R.plurals.loads_home_trips_open, openTrips, openTrips)
            else stringResource(R.string.loads_home_trips_none),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.loads_home_trips_action), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Sugerencias Smart Load por revisar. */
@Composable
private fun SmartLoadsCard(pending: Int, onClick: () -> Unit) {
    LogyxCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.loads_home_smart_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            StatusPill(pluralStringResource(R.plurals.loads_home_smart_pending, pending, pending), PillTone.Amber)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.loads_home_smart_message), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.loads_home_smart_action), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** Cargas de retorno: las 3 mejores del dashboard y acceso a la lista completa. */
@Composable
private fun ReturnLoadsSection(count: Int, top: List<ReturnLoadHintView>, onOpen: () -> Unit) {
    LogyxCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.loads_home_return_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            StatusPill(pluralStringResource(R.plurals.loads_home_return_count, count, count), PillTone.Emerald)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.loads_home_return_message), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        top.forEach { hint ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${cityLabel(hint.originCity)} → ${cityLabel(hint.destinationCity)}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${Fmt.tons(hint.weightKg)} · ${stringResource(R.string.loads_home_return_pickup, Fmt.date(hint.pickupDate))}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Fmt.money(hint.suggestedPrice), style = LogyxText.moneySmall, color = LogyxTheme.colors.moneyText)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.loads_home_return_action), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
    LiveEffect(viewModel) { viewModel.liveLoop() }
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
                        if (state.loadingMore) item { LoadingMoreFooter() }
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
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
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
            val cargoLabels = CargoType.entries.associateWith { it.label }
            LogyxDropdown(
                label = stringResource(R.string.loads_filter_cargo_type),
                options = listOf<CargoType?>(null) + CargoType.entries, selected = state.cargoType,
                onSelected = onCargoType, optionLabel = { it?.let(cargoLabels::getValue) ?: anyCargo },
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
fun LoadDetailRoute(
    requestId: String,
    onBack: () -> Unit,
    onOpenChat: (requestId: String, title: String?) -> Unit = { _, _ -> },
    onOpenOrganization: (String) -> Unit = {},
    viewModel: LoadDetailViewModel = koinViewModel { parametersOf(requestId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiveEffect(viewModel) { viewModel.liveLoop() }
    LoadDetailScreen(
        state, onBack, viewModel::refresh, viewModel::openBidSheet, viewModel::dismissBidSheet,
        viewModel::selectVehicle, viewModel::onAmountChange, viewModel::onNotesChange,
        viewModel::requestConfirm, viewModel::dismissConfirm, viewModel::submitBid, viewModel::messageShown,
        onOpenChat = onOpenChat, onOpenOrganization = onOpenOrganization,
        myBid = MyBidActions(
            onAskAcceptCounter = viewModel::askAcceptCounter, onAskRejectCounter = viewModel::askRejectCounter,
            onConfirmCounterAnswer = viewModel::confirmCounterAnswer, onDismissCounterAnswer = viewModel::dismissCounterConfirm,
            onOpenCounter = viewModel::openCounterSheet, onCounterAmount = viewModel::onCounterAmount,
            onCounterMessage = viewModel::onCounterMessage, onAskSendCounter = viewModel::askConfirmCounterSend,
            onSendCounter = viewModel::submitCounter, onCancelSendCounter = viewModel::cancelCounterSendConfirm,
            onCloseCounter = viewModel::closeCounterSheet, onAskWithdraw = viewModel::askWithdraw,
            onConfirmWithdraw = viewModel::confirmWithdraw, onDismissWithdraw = viewModel::dismissWithdraw,
        ),
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
    onOpenChat: (String, String?) -> Unit = { _, _ -> },
    onOpenOrganization: (String) -> Unit = {},
    myBid: MyBidActions = MyBidActions(),
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
                            RouteMap(state.origin, state.destination, path = state.route?.points, contentDescription = mapDescription, height = 220.dp)
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
                                if (r.requesterOrgId != null && r.requesterTrustScore != null) {
                                    Spacer(Modifier.height(8.dp))
                                    TrustScoreBadge(r.requesterTrustScore, onClick = { onOpenOrganization(r.requesterOrgId!!) })
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
                            LogyxButton(
                                stringResource(R.string.loads_ask_company), { onOpenChat(r.id, r.requesterName ?: r.cargoDescription) },
                                variant = ButtonVariant.Secondary, size = ButtonSize.Medium,
                            )
                            if (r.myBid != null) MyBidCard(state, r, myBid)
                            ClosureBanners(r)
                        }
                    }
                    val canBid = r.myBid == null && r.auction?.status == AuctionStatus.OPEN
                    if (canBid) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            LogyxButton(stringResource(R.string.loads_action_bid), onOpenBid, variant = ButtonVariant.Money, requiresOperate = true)
                        }
                    }
                }
            }
        }
        if (state.form != null && r != null) {
            BidSheet(state, r, onDismissBid, onSelectVehicle, onAmountChange, onNotesChange, onRequestConfirm, onDismissConfirm, onConfirmBid)
        }
        MyBidDialogs(state, r, myBid)
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
    LogyxBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.loads_sheet_title)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.loads_sheet_route, cityLabel(request.originCity), cityLabel(request.destinationCity), Fmt.tons(request.weightKg)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.loads_sheet_vehicle), style = MaterialTheme.typography.labelLarge)
            // RN02 (F09): toda la flota, con los no aptos deshabilitados y su motivo (kg, tipo de carga, m³, disponibilidad).
            VehicleChoiceList(
                choices = state.vehicleChoices, selectedId = form.vehicleId, onSelect = onSelectVehicle,
                weightKg = request.weightKg, volumeM3 = request.volumeM3, cargoType = request.cargoType,
                error = form.vehicleError?.asString(), enabled = !form.submitting,
            )
            MoneyField(
                value = form.amountText, onValueChange = onAmountChange, label = stringResource(R.string.loads_sheet_amount),
                hint = bidHint(request), error = form.amountError?.asString(),
            )
            ChipRow {
                request.priceFloor?.let { QuickAmount(stringResource(R.string.loads_quick_floor, Fmt.moneyShort(it)), it, form.amountText, onAmountChange) }
                request.suggestedPrice?.let { QuickAmount(stringResource(R.string.loads_quick_reference, Fmt.moneyShort(it)), it, form.amountText, onAmountChange) }
            }
            LogyxTextField(
                value = form.notes, onValueChange = onNotesChange, label = stringResource(R.string.loads_sheet_notes),
                optional = true, singleLine = false, minLines = 2, maxLines = 4,
                hint = stringResource(R.string.loads_sheet_notes_hint, form.notes.length),
                error = form.notesError?.asString(),
            )
            if (form.error != null) {
                AlertBanner(form.error, kind = MessageKind.Error, title = stringResource(R.string.loads_sheet_error_title))
            }
            LogyxButton(
                stringResource(R.string.loads_sheet_submit), onRequestConfirm, variant = ButtonVariant.Money,
                loading = form.submitting, enabled = vehicles.isNotEmpty(), requiresOperate = true,
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

// ───────────────────────── Mi oferta: posición, contraofertas y retiro ─────────────────────────

/** Callbacks de la tarjeta "Tu oferta" del detalle: contraoferta de la PYME, mi contraoferta y retirar. */
class MyBidActions(
    val onAskAcceptCounter: (CounterOfferView) -> Unit = {},
    val onAskRejectCounter: (CounterOfferView) -> Unit = {},
    val onConfirmCounterAnswer: () -> Unit = {},
    val onDismissCounterAnswer: () -> Unit = {},
    val onOpenCounter: () -> Unit = {},
    val onCounterAmount: (String) -> Unit = {},
    val onCounterMessage: (String) -> Unit = {},
    val onAskSendCounter: () -> Unit = {},
    val onSendCounter: () -> Unit = {},
    val onCancelSendCounter: () -> Unit = {},
    val onCloseCounter: () -> Unit = {},
    val onAskWithdraw: () -> Unit = {},
    val onConfirmWithdraw: () -> Unit = {},
    val onDismissWithdraw: () -> Unit = {},
)

@Composable
private fun MyBidCard(state: LoadDetailUiState, r: RequestView, actions: MyBidActions) {
    val bid = r.myBid ?: return
    LogyxCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.loads_your_bid), style = MaterialTheme.typography.titleMedium)
            StatusPill(r.myBidOutcome?.pill() ?: bid.status.pill())
        }
        Spacer(Modifier.height(8.dp))
        PriceTag(bid.amount, large = false)
        bid.notes?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        r.myBidStanding?.let {
            Spacer(Modifier.height(12.dp))
            BidStandingRow(it)
        }
        if (bid.status == BidStatus.WITHDRAWN) {
            Spacer(Modifier.height(12.dp))
            AlertBanner(stringResource(R.string.loads_withdrawn_info), kind = MessageKind.Info)
        }
        state.counterFromPyme?.let { c ->
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().background(LogyxTheme.colors.infoContainer, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.loads_counter_proposes, Fmt.money(c.amount)), style = MaterialTheme.typography.titleSmall)
                c.message?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LogyxButton(
                    stringResource(R.string.loads_counter_accept), { actions.onAskAcceptCounter(c) },
                    variant = ButtonVariant.Money, size = ButtonSize.Medium, enabled = !state.busy, requiresOperate = true,
                )
                LogyxButton(
                    stringResource(R.string.loads_counter_reject), { actions.onAskRejectCounter(c) },
                    variant = ButtonVariant.Destructive, size = ButtonSize.Medium, enabled = !state.busy, requiresOperate = true,
                )
            }
        }
        state.myPendingCounter?.let { c ->
            Spacer(Modifier.height(12.dp))
            AlertBanner(
                stringResource(R.string.loads_counter_mine_waiting, Fmt.money(c.amount)), kind = MessageKind.Info,
                title = stringResource(R.string.loads_counter_mine_title),
            )
        }
        val history = state.counters.filter { it.status != CounterOfferStatus.PENDING }.take(3)
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.loads_counter_history), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            history.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(Fmt.money(c.amount), style = LogyxText.moneySmall, color = LogyxTheme.colors.moneyText)
                        Text(
                            stringResource(if (c.proposedByCarrier) R.string.loads_counter_by_me else R.string.loads_counter_by_pyme),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusPill(c.status.pill())
                }
            }
        }
        if (state.canCounter || state.canWithdraw) {
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canCounter) {
                    LogyxButton(
                        stringResource(R.string.loads_counter_action), actions.onOpenCounter,
                        variant = ButtonVariant.Secondary, size = ButtonSize.Medium, enabled = !state.busy, requiresOperate = true,
                    )
                }
                if (state.canWithdraw) {
                    LogyxButton(
                        stringResource(R.string.loads_withdraw_action), actions.onAskWithdraw,
                        variant = ButtonVariant.Destructive, size = ButtonSize.Medium, enabled = !state.busy, requiresOperate = true,
                    )
                }
            }
        }
    }
}

/** Cierre de la subasta (motivo y fecha, F51) y motivo de la cancelación de la solicitud (F16). */
@Composable
private fun ClosureBanners(r: RequestView) {
    val closure = auctionClosureText(r.auction)
    if (closure != null) {
        val text = if (r.myBid == null) closure + " " + stringResource(R.string.loads_auction_closed) else closure
        AlertBanner(text, kind = MessageKind.Info, title = stringResource(R.string.loads_auction_closed_title))
    }
    if (r.status == RequestStatus.CANCELLED) {
        AlertBanner(
            r.cancelReason?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.loads_request_cancelled_reason, it) }
                ?: stringResource(R.string.loads_request_cancelled),
            kind = MessageKind.Warning, title = stringResource(R.string.loads_request_cancelled_title),
        )
    }
}

@Composable
private fun MyBidDialogs(state: LoadDetailUiState, r: RequestView?, a: MyBidActions) {
    state.counterConfirm?.let { action ->
        LogyxConfirmDialog(
            title = stringResource(if (action.accept) R.string.loads_counter_accept_title else R.string.loads_counter_reject_title),
            message = if (action.accept) stringResource(R.string.loads_counter_accept_message, Fmt.money(action.counter.amount))
            else stringResource(R.string.loads_counter_reject_message),
            confirmLabel = stringResource(if (action.accept) R.string.loads_counter_accept else R.string.loads_counter_reject),
            onConfirm = a.onConfirmCounterAnswer, onDismiss = a.onDismissCounterAnswer,
            destructive = !action.accept, loading = state.busy,
        )
    }
    if (state.confirmWithdraw) {
        LogyxConfirmDialog(
            title = stringResource(R.string.loads_withdraw_title),
            message = stringResource(R.string.loads_withdraw_message),
            confirmLabel = stringResource(R.string.loads_withdraw_confirm),
            onConfirm = a.onConfirmWithdraw, onDismiss = a.onDismissWithdraw,
            dismissLabel = stringResource(R.string.loads_withdraw_keep), destructive = true, loading = state.busy,
        )
    }
    val form = state.counterForm
    val bid = r?.myBid
    if (form != null && r != null && bid != null) {
        LogyxReasonSheet(
            title = stringResource(R.string.loads_counter_action),
            reasonLabel = stringResource(R.string.loads_counter_message_label),
            value = form.message, onValueChange = a.onCounterMessage,
            confirmLabel = stringResource(R.string.loads_counter_send),
            onConfirm = a.onAskSendCounter, onDismiss = a.onCloseCounter,
            message = stringResource(R.string.loads_counter_send_info, Fmt.money(bid.amount)),
            error = form.messageError?.asString(), optional = true, destructive = false,
            loading = form.sending, maxLength = MAX_COUNTER_MESSAGE,
        ) {
            MoneyField(
                form.amount, a.onCounterAmount, stringResource(R.string.loads_counter_amount_label),
                hint = r.priceFloor?.let { stringResource(R.string.loads_sheet_amount_hint_floor, Fmt.money(it)) },
                error = form.amountError?.asString(), enabled = !form.sending,
            )
        }
        if (form.confirming) {
            LogyxConfirmDialog(
                title = stringResource(R.string.loads_counter_confirm_title, Fmt.money(pe.logyx.core.designsystem.components.parseMoney(form.amount))),
                message = stringResource(R.string.loads_counter_confirm_message),
                confirmLabel = stringResource(R.string.loads_counter_send),
                onConfirm = a.onSendCounter, onDismiss = a.onCancelSendCounter,
                dismissLabel = stringResource(R.string.loads_confirm_review), loading = form.sending,
            )
        }
    }
}

// ───────────────────────── Mis ofertas ─────────────────────────

@Composable
fun MyBidsRoute(
    onOpenLoad: (String) -> Unit,
    onOpenChat: (requestId: String, title: String?) -> Unit = { _, _ -> },
    viewModel: MyBidsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiveEffect(viewModel) { viewModel.liveLoop() }
    MyBidsScreen(
        state, viewModel::refresh, onOpenLoad,
        onAskAccept = viewModel::askAccept, onAskReject = viewModel::askReject, onConfirm = viewModel::confirm,
        onDismissConfirm = viewModel::dismissConfirm, onMessageShown = viewModel::onMessageShown, onOpenChat = onOpenChat,
        onFilter = viewModel::setFilter,
    )
}

@Composable
fun MyBidsScreen(
    state: MyBidsUiState,
    onRefresh: () -> Unit,
    onOpenLoad: (String) -> Unit,
    onAskAccept: (CounterOfferView) -> Unit = {},
    onAskReject: (CounterOfferView) -> Unit = {},
    onConfirm: () -> Unit = {},
    onDismissConfirm: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    onOpenChat: (String, String?) -> Unit = { _, _ -> },
    onFilter: (MyBidsFilter) -> Unit = {},
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    LogyxScreen(stringResource(R.string.loads_bids_title), snackbar = snackbar) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (state.items.isNotEmpty()) {
                            item {
                                val labels = MyBidsFilter.entries.associateWith { it.text(state.activeCount) }
                                val description = stringResource(R.string.loads_bids_filter_description)
                                SegmentedControl(
                                    MyBidsFilter.entries, state.filter, onFilter, { labels.getValue(it) },
                                    Modifier.semantics { contentDescription = description },
                                )
                            }
                        }
                        if (state.items.isEmpty()) {
                            item {
                                EmptyState(stringResource(R.string.loads_bids_empty_title), stringResource(R.string.loads_bids_empty_message))
                            }
                        } else if (state.visible.isEmpty()) {
                            item {
                                EmptyState(stringResource(R.string.loads_bids_filtered_title), stringResource(R.string.loads_bids_filtered_message))
                            }
                        }
                        items(state.visible, key = { it.id }) { r ->
                            BidRow(
                                r, counter = r.myBid?.let { state.counters[it.id] }, busy = state.busy,
                                onAskAccept = onAskAccept, onAskReject = onAskReject,
                                onChat = { onOpenChat(r.id, r.requesterName ?: r.cargoDescription) },
                            ) { onOpenLoad(r.id) }
                        }
                    }
                }
            }
        }
        state.confirm?.let { action ->
            LogyxConfirmDialog(
                title = stringResource(if (action.accept) R.string.loads_counter_accept_title else R.string.loads_counter_reject_title),
                message = if (action.accept) stringResource(R.string.loads_counter_accept_message, Fmt.money(action.counter.amount))
                else stringResource(R.string.loads_counter_reject_message),
                confirmLabel = stringResource(if (action.accept) R.string.loads_counter_accept else R.string.loads_counter_reject),
                onConfirm = onConfirm,
                onDismiss = onDismissConfirm,
                destructive = !action.accept,
                loading = state.busy,
            )
        }
    }
}

@Composable
private fun MyBidsFilter.text(activeCount: Int) = when (this) {
    MyBidsFilter.All -> stringResource(R.string.loads_bids_filter_all)
    MyBidsFilter.Active -> stringResource(R.string.loads_bids_filter_active, activeCount)
    MyBidsFilter.Closed -> stringResource(R.string.loads_bids_filter_closed)
}

@Composable
private fun BidRow(
    r: RequestView,
    counter: CounterOfferView?,
    busy: Boolean,
    onAskAccept: (CounterOfferView) -> Unit,
    onAskReject: (CounterOfferView) -> Unit,
    onChat: () -> Unit,
    onClick: () -> Unit,
) {
    val bid = r.myBid ?: return
    LogyxCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.money(bid.amount), style = LogyxText.moneyMedium, color = LogyxTheme.colors.moneyText)
            StatusPill(r.myBidOutcome?.pill() ?: bid.status.pill())
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
            if (r.myBidOutcome == BidOutcome.OPEN) AuctionCountdown(r.auction)
        }
        // Posición (V15) y, si ya cerró, motivo y fecha del cierre (F51); nunca montos ajenos (RN04).
        r.myBidStanding?.let {
            Spacer(Modifier.height(12.dp))
            BidStandingRow(it)
        }
        auctionClosureText(r.auction)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (r.status == RequestStatus.CANCELLED) {
            Spacer(Modifier.height(8.dp))
            Text(
                r.cancelReason?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.loads_request_cancelled_reason, it) }
                    ?: stringResource(R.string.loads_request_cancelled),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (counter != null && counter.proposedByCarrier) {
            Spacer(Modifier.height(12.dp))
            AlertBanner(
                stringResource(R.string.loads_counter_mine_waiting, Fmt.money(counter.amount)), kind = MessageKind.Info,
                title = stringResource(R.string.loads_counter_mine_title),
            )
        } else if (counter != null) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().background(LogyxTheme.colors.infoContainer, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.loads_counter_proposes, Fmt.money(counter.amount)), style = MaterialTheme.typography.titleSmall)
                counter.message?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LogyxButton(
                    stringResource(R.string.loads_counter_accept), { onAskAccept(counter) },
                    variant = ButtonVariant.Money, size = ButtonSize.Medium, enabled = !busy, requiresOperate = true,
                )
                LogyxButton(
                    stringResource(R.string.loads_counter_reject), { onAskReject(counter) },
                    variant = ButtonVariant.Destructive, size = ButtonSize.Medium, enabled = !busy, requiresOperate = true,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LogyxButton(stringResource(R.string.loads_ask_company), onChat, variant = ButtonVariant.Ghost, size = ButtonSize.Medium)
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
