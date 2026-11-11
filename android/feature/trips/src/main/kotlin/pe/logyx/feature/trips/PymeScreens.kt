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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.LiveEffect
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.ChipRow
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LoadingMoreFooter
import pe.logyx.core.designsystem.components.LogyxBottomSheet
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCombobox
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxDateRangeField
import pe.logyx.core.designsystem.components.LogyxDropdown
import pe.logyx.core.designsystem.components.LogyxFilterChip
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.LogyxTextField
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.SegmentedControl
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.asString
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.label
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.TripBookingView
import pe.logyx.core.model.TripView
import java.time.LocalDate

private enum class PymeTripsTab { Search, Bookings }

/** Pestaña "Viajes" de la PYME: buscar viajes y reservar, y ver mis reservas. */
@Composable
fun PymeTripsRoute(
    onOpenShipment: (String) -> Unit,
    searchViewModel: TripSearchViewModel = koinViewModel(),
    bookingsViewModel: MyBookingsViewModel = koinViewModel(),
) {
    val search by searchViewModel.state.collectAsStateWithLifecycle()
    val bookings by bookingsViewModel.state.collectAsStateWithLifecycle()
    LiveEffect(bookingsViewModel) { bookingsViewModel.liveLoop() }
    var tab by rememberSaveable { mutableStateOf(PymeTripsTab.Search) }
    // Tras reservar con éxito se muestra el listado de reservas.
    LaunchedEffect(search.bookedTick) { if (search.bookedTick > 0) { tab = PymeTripsTab.Bookings; bookingsViewModel.refresh() } }
    LaunchedEffect(tab) { if (tab == PymeTripsTab.Bookings) bookingsViewModel.refresh() }

    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(search.message, snackbar, searchViewModel::onMessageShown)
    MessageEffect(bookings.message, snackbar, bookingsViewModel::onMessageShown)
    val tabLabels = mapOf(
        PymeTripsTab.Search to stringResource(R.string.trips_pyme_tab_search),
        PymeTripsTab.Bookings to stringResource(R.string.trips_pyme_tab_bookings),
    )
    LogyxScreen(stringResource(R.string.trips_pyme_title), snackbar = snackbar) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SegmentedControl(
                PymeTripsTab.entries, tab, { tab = it }, { tabLabels.getValue(it) },
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when (tab) {
                PymeTripsTab.Search -> TripSearchContent(
                    search, searchViewModel::refresh, searchViewModel::loadMore, searchViewModel::applyFilters, searchViewModel::clearFilters,
                    searchViewModel::openBook, Modifier.weight(1f),
                )
                PymeTripsTab.Bookings -> MyBookingsContent(
                    bookings, bookingsViewModel::refresh, bookingsViewModel::askCancel,
                    onSearch = { tab = PymeTripsTab.Search }, onOpenShipment = onOpenShipment, modifier = Modifier.weight(1f),
                )
            }
        }
    }
    search.book?.let { form ->
        BookSheet(
            form, searchViewModel::dismissBook, searchViewModel::onWeight, searchViewModel::onDescription, searchViewModel::onCargoType,
            searchViewModel::onPickup, searchViewModel::onDelivery, searchViewModel::requestConfirm, searchViewModel::submit,
            searchViewModel::dismissConfirm,
        )
    }
    bookings.pendingCancel?.let {
        LogyxConfirmDialog(
            title = stringResource(R.string.trips_booking_cancel_title),
            message = stringResource(R.string.trips_booking_cancel_message),
            confirmLabel = stringResource(R.string.trips_booking_cancel),
            onConfirm = bookingsViewModel::confirmCancel, onDismiss = bookingsViewModel::dismissCancel,
            dismissLabel = stringResource(R.string.trips_back), destructive = true, loading = bookings.busy,
        )
    }
}

// ───────────────────────── Buscar viajes ─────────────────────────

@Composable
fun TripSearchContent(
    state: TripSearchUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onApplyFilters: (CityItem?, CityItem?, LocalDate?, LocalDate?, String) -> Unit,
    onClearFilters: () -> Unit,
    onReserve: (TripView) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val activeCount = state.filter.activeCount
    Column(modifier.fillMaxSize()) {
        ChipRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            LogyxFilterChip(
                label = if (activeCount > 0) stringResource(R.string.trips_filters_count, activeCount) else stringResource(R.string.trips_filters),
                selected = activeCount > 0, onClick = { showFilters = true }, leadingIcon = Icons.Rounded.Tune,
            )
        }
        when {
            state.loading -> SkeletonList()
            state.error != null && state.items.isEmpty() -> ErrorState(state.error, onRefresh)
            else -> Refreshable(state.refreshing, onRefresh, Modifier.weight(1f)) {
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
                    if (state.items.isEmpty()) {
                        item {
                            if (activeCount > 0) {
                                EmptyState(
                                    stringResource(R.string.trips_search_no_match_title), stringResource(R.string.trips_search_no_match_message),
                                    actionLabel = stringResource(R.string.trips_filters_clear), onAction = onClearFilters,
                                )
                            } else {
                                EmptyState(stringResource(R.string.trips_search_empty_title), stringResource(R.string.trips_search_empty_message))
                            }
                        }
                    } else {
                        item {
                            Text(
                                pluralStringResource(R.plurals.trips_search_count, state.total, state.total),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(state.items, key = { it.id }) { t ->
                            TripCard(t, showCarrier = true) {
                                LogyxButton(
                                    stringResource(R.string.trips_reserve), { onReserve(t) },
                                    variant = ButtonVariant.Primary, size = ButtonSize.Medium,
                                )
                            }
                        }
                        if (state.loadingMore) item { LoadingMoreFooter() }
                    }
                }
            }
        }
    }
    if (showFilters) {
        FiltersSheet(
            state, onDismiss = { showFilters = false },
            onApply = { o, d, f, t, w -> showFilters = false; onApplyFilters(o, d, f, t, w) },
            onClear = { showFilters = false; onClearFilters() },
        )
    }
}

@Composable
private fun FiltersSheet(
    state: TripSearchUiState,
    onDismiss: () -> Unit,
    onApply: (CityItem?, CityItem?, LocalDate?, LocalDate?, String) -> Unit,
    onClear: () -> Unit,
) {
    var origin by remember { mutableStateOf(state.origin) }
    var destination by remember { mutableStateOf(state.destination) }
    var from by remember { mutableStateOf(state.fromDate) }
    var to by remember { mutableStateOf(state.toDate) }
    var weight by remember { mutableStateOf(state.minWeightText) }
    LogyxBottomSheet(onDismiss, title = stringResource(R.string.trips_filters_title)) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LogyxCombobox(stringResource(R.string.trips_f_origin), state.cities, origin, { origin = it }, { it.name }, clearable = true)
            LogyxCombobox(stringResource(R.string.trips_f_destination), state.cities, destination, { destination = it }, { it.name }, clearable = true)
            LogyxDateRangeField(stringResource(R.string.trips_filters_date), from, to, { a, b -> from = a; to = b }, optional = true)
            LogyxTextField(
                weight, { weight = sanitizeKgInput(it) }, stringResource(R.string.trips_filters_weight), suffix = "kg", optional = true,
                hint = stringResource(R.string.trips_filters_weight_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            LogyxButton(stringResource(R.string.trips_filters_apply), { onApply(origin, destination, from, to, weight) })
            LogyxButton(stringResource(R.string.trips_filters_clear), onClear, variant = ButtonVariant.Ghost, size = ButtonSize.Medium)
        }
    }
}

// ───────────────────────── Reservar espacio ─────────────────────────

@Composable
private fun BookSheet(
    form: BookFormState,
    onDismiss: () -> Unit,
    onWeight: (String) -> Unit,
    onDescription: (String) -> Unit,
    onCargoType: (CargoType) -> Unit,
    onPickup: (String) -> Unit,
    onDelivery: (String) -> Unit,
    onRequestConfirm: () -> Unit,
    onSubmit: () -> Unit,
    onDismissConfirm: () -> Unit,
) {
    val trip = form.trip
    val cargoLabels = CargoType.entries.associateWith { it.label }
    LogyxBottomSheet(onDismiss, title = stringResource(R.string.trips_book_title)) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "${cityLabel(trip.originCity)} → ${cityLabel(trip.destinationCity)} · ${Fmt.dateTime(trip.departureAt)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(tripRateText(trip), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LogyxTextField(
                form.weightText, onWeight, stringResource(R.string.trips_book_weight), suffix = "kg",
                hint = stringResource(R.string.trips_book_weight_hint, kgText(trip.remainingKg)),
                error = form.weightError?.asString(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            LogyxTextField(
                form.description, onDescription, stringResource(R.string.trips_book_description),
                error = form.descriptionError?.asString(), singleLine = false, minLines = 2,
            )
            LogyxDropdown(
                stringResource(R.string.trips_book_cargo_type), CargoType.entries, form.cargoType, onCargoType,
                optionLabel = { cargoLabels.getValue(it) }, error = form.cargoTypeError?.asString(),
            )
            LogyxTextField(form.pickup, onPickup, stringResource(R.string.trips_book_pickup), error = form.pickupError?.asString())
            LogyxTextField(form.delivery, onDelivery, stringResource(R.string.trips_book_delivery), error = form.deliveryError?.asString())
            form.estimatedPrice?.let { price ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.trips_book_price), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        Fmt.money(price), style = pe.logyx.core.designsystem.LogyxText.moneyMedium,
                        color = pe.logyx.core.designsystem.LogyxTheme.colors.moneyText,
                    )
                }
            }
            LogyxButton(
                stringResource(R.string.trips_book_submit), onRequestConfirm,
                variant = ButtonVariant.Primary, loading = form.submitting,
            )
        }
    }
    if (form.confirming) {
        LogyxConfirmDialog(
            title = stringResource(R.string.trips_book_confirm_title, kgText(form.weightKg), Fmt.money(form.estimatedPrice)),
            message = stringResource(R.string.trips_book_confirm_message),
            confirmLabel = stringResource(R.string.trips_book_confirm_action),
            onConfirm = onSubmit, onDismiss = onDismissConfirm,
            dismissLabel = stringResource(R.string.trips_back), loading = form.submitting,
        )
    }
}

// ───────────────────────── Mis reservas ─────────────────────────

@Composable
fun MyBookingsContent(
    state: MyBookingsUiState,
    onRefresh: () -> Unit,
    onCancel: (TripBookingView) -> Unit,
    onSearch: () -> Unit,
    onOpenShipment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> SkeletonList()
            state.error != null && state.bookings.isEmpty() -> ErrorState(state.error, onRefresh)
            else -> Refreshable(state.refreshing, onRefresh) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (state.bookings.isEmpty()) {
                        item {
                            EmptyState(
                                stringResource(R.string.trips_bookings_mine_empty_title), stringResource(R.string.trips_bookings_mine_empty_message),
                                actionLabel = stringResource(R.string.trips_bookings_mine_empty_action), onAction = onSearch,
                            )
                        }
                    } else {
                        items(state.bookings, key = { it.id }) { b ->
                            BookingCard(b, forCarrier = false, onCancel = { onCancel(b) }, onOpenShipment = onOpenShipment)
                        }
                    }
                }
            }
        }
    }
}
