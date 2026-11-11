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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.LiveEffect
import pe.logyx.core.designsystem.components.AlertBanner
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.InfoChip
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxCombobox
import pe.logyx.core.designsystem.components.LogyxDateField
import pe.logyx.core.designsystem.components.LogyxDropdown
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.LogyxTextField
import pe.logyx.core.designsystem.components.LogyxTimeField
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.MessageKind
import pe.logyx.core.designsystem.components.MoneyField
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.SegmentedControl
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.asString
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.label
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.BookingStatus
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.TripBookingView
import pe.logyx.core.model.TripPricingMode
import pe.logyx.core.model.TripStatus
import pe.logyx.core.model.TripView
import pe.logyx.core.model.VehicleType
import pe.logyx.core.model.VehicleView
import pe.logyx.core.model.VehicleStatus
import pe.logyx.core.model.isManageable
import java.time.LocalDate
import java.time.LocalTime

// ───────────────────────── Publicar viaje ─────────────────────────

@Composable
fun PublishTripRoute(
    onBack: () -> Unit,
    onPublished: (String) -> Unit,
    viewModel: PublishTripViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.publishedId) { state.publishedId?.let(onPublished) }
    PublishTripScreen(
        state, onBack,
        onOrigin = viewModel::onOrigin, onDestination = viewModel::onDestination, onDate = viewModel::onDate, onTime = viewModel::onTime,
        onVehicle = viewModel::onVehicle, onCapacity = viewModel::onCapacity, onPricingMode = viewModel::onPricingMode,
        onRate = viewModel::onRate, onNotes = viewModel::onNotes, onSubmit = { viewModel.submit() },
        onRetry = viewModel::load, onMessageShown = viewModel::onMessageShown,
    )
}

@Composable
fun PublishTripScreen(
    state: PublishTripUiState,
    onBack: () -> Unit,
    onOrigin: (CityItem?) -> Unit,
    onDestination: (CityItem?) -> Unit,
    onDate: (LocalDate) -> Unit,
    onTime: (LocalTime) -> Unit,
    onVehicle: (String) -> Unit,
    onCapacity: (String) -> Unit,
    onPricingMode: (TripPricingMode) -> Unit,
    onRate: (String) -> Unit,
    onNotes: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit = {},
    onMessageShown: () -> Unit = {},
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    LogyxScreen(stringResource(R.string.trips_publish_title), onBack = onBack, snackbar = snackbar) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList()
                state.error != null -> ErrorState(state.error, onRetry)
                state.vehicles.isEmpty() -> EmptyState(
                    stringResource(R.string.trips_no_vehicles_title), stringResource(R.string.trips_no_vehicles_message),
                )
                else -> Column(
                    Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val f = state.form
                    val e = state.errors
                    LogyxCombobox(
                        stringResource(R.string.trips_f_origin), state.cities, f.origin, onOrigin, { it.name },
                        error = e.origin?.asString(),
                    )
                    LogyxCombobox(
                        stringResource(R.string.trips_f_destination), state.cities.filter { it.code != f.origin?.code }, f.destination,
                        onDestination, { it.name }, error = e.destination?.asString(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LogyxDateField(
                            stringResource(R.string.trips_f_date), f.date, onDate, Modifier.weight(1f),
                            error = e.departure?.asString(),
                        )
                        LogyxTimeField(
                            stringResource(R.string.trips_f_time), f.time, onTime, Modifier.weight(1f),
                            hint = stringResource(R.string.trips_f_time_hint),
                        )
                    }
                    val supports = state.vehicles.associate { it.id to vehicleSupport(it) }
                    LogyxDropdown(
                        stringResource(R.string.trips_f_vehicle), state.vehicles, state.selectedVehicle, { onVehicle(it.id) },
                        optionLabel = { it.plate }, error = e.vehicle?.asString(),
                        optionSupporting = { v -> supports[v.id] },
                    )
                    LogyxTextField(
                        f.capacityText, onCapacity, stringResource(R.string.trips_f_capacity),
                        suffix = "kg", error = e.capacity?.asString(),
                        hint = state.selectedVehicle?.let { stringResource(R.string.trips_f_capacity_hint, kgText(it.maxWeightKg)) }
                            ?: stringResource(R.string.trips_f_capacity_hint_generic),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.trips_f_pricing), style = MaterialTheme.typography.labelLarge)
                        val modeLabels = TripPricingMode.entries.associateWith { it.label }
                        SegmentedControl(TripPricingMode.entries, f.pricingMode, onPricingMode, { modeLabels.getValue(it) })
                    }
                    MoneyField(
                        f.rateText, onRate,
                        stringResource(if (f.pricingMode == TripPricingMode.PER_KG) R.string.trips_f_rate_per_kg else R.string.trips_f_rate_per_trip),
                        hint = stringResource(if (f.pricingMode == TripPricingMode.PER_KG) R.string.trips_f_rate_hint_per_kg else R.string.trips_f_rate_hint_per_trip),
                        error = e.rate?.asString(),
                    )
                    LogyxTextField(
                        f.notes, onNotes, stringResource(R.string.trips_f_notes), optional = true, singleLine = false, minLines = 2,
                        hint = stringResource(R.string.trips_f_notes_hint),
                    )
                    LogyxButton(
                        stringResource(R.string.trips_publish_action), onSubmit,
                        variant = ButtonVariant.Primary, loading = state.submitting,
                    )
                }
            }
        }
    }
}

@Composable
private fun vehicleSupport(v: VehicleView): String =
    "${v.type.label} · ${stringResource(R.string.trips_f_vehicle_support, kgText(v.maxWeightKg))}"

// ───────────────────────── Mis viajes ─────────────────────────

@Composable
fun CarrierTripsRoute(
    onBack: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onPublish: () -> Unit,
    viewModel: CarrierTripsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiveEffect(viewModel) { viewModel.liveLoop() }
    CarrierTripsScreen(state, onBack, viewModel::refresh, onOpenTrip, onPublish)
}

@Composable
fun CarrierTripsScreen(
    state: CarrierTripsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onPublish: () -> Unit,
) {
    LogyxScreen(stringResource(R.string.trips_my_title), onBack = onBack) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList()
                state.error != null && state.trips.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            LogyxButton(
                                stringResource(R.string.trips_publish_action), onPublish,
                                size = ButtonSize.Large, icon = Icons.Rounded.Add,
                            )
                        }
                        if (state.trips.isEmpty()) {
                            item {
                                EmptyState(
                                    stringResource(R.string.trips_empty_title), stringResource(R.string.trips_empty_message),
                                )
                            }
                        } else {
                            items(state.trips, key = { it.id }) { t -> TripCard(t, showCarrier = false, onClick = { onOpenTrip(t.id) }) }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Detalle del viaje y reservas ─────────────────────────

@Composable
fun CarrierTripDetailRoute(
    tripId: String,
    justPublished: Boolean,
    onBack: () -> Unit,
    onOpenShipment: (String) -> Unit,
    viewModel: CarrierTripDetailViewModel = koinViewModel { parametersOf(tripId, justPublished) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiveEffect(viewModel) { viewModel.liveLoop() }
    CarrierTripDetailScreen(
        state, onBack, viewModel::refresh, onOpenShipment,
        onConfirm = viewModel::askConfirm, onReject = viewModel::askReject, onClose = viewModel::askClose, onCancel = viewModel::askCancel,
        onPerform = viewModel::perform, onDismissConfirm = viewModel::dismissConfirm, onMessageShown = viewModel::onMessageShown,
    )
}

@Composable
fun CarrierTripDetailScreen(
    state: TripDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenShipment: (String) -> Unit,
    onConfirm: (TripBookingView) -> Unit,
    onReject: (TripBookingView) -> Unit,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    onPerform: () -> Unit,
    onDismissConfirm: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    LogyxScreen(stringResource(R.string.trips_detail_title), onBack = onBack, snackbar = snackbar) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val trip = state.trip
            when {
                state.loading -> SkeletonList()
                trip == null -> ErrorState(state.error ?: stringResource(R.string.trips_detail_not_found), onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { TripCard(trip, showCarrier = false) }
                        if (trip.isManageable) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    LogyxButton(
                                        stringResource(R.string.trips_close_action), onClose, Modifier.weight(1f),
                                        variant = ButtonVariant.Secondary, size = ButtonSize.Medium,
                                    )
                                    LogyxButton(
                                        stringResource(R.string.trips_cancel_action), onCancel, Modifier.weight(1f),
                                        variant = ButtonVariant.Destructive, size = ButtonSize.Medium,
                                    )
                                }
                            }
                        }
                        item {
                            Text(
                                stringResource(R.string.trips_bookings_title), Modifier.semantics { heading() },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        if (state.bookings.isEmpty()) {
                            item {
                                EmptyState(
                                    stringResource(R.string.trips_bookings_empty_title), stringResource(R.string.trips_bookings_empty_message),
                                )
                            }
                        } else {
                            items(state.bookings, key = { it.id }) { b ->
                                BookingCard(b, forCarrier = true, onConfirm = { onConfirm(b) }, onReject = { onReject(b) }, onOpenShipment = onOpenShipment)
                            }
                        }
                    }
                }
            }
        }
    }
    state.pending?.let { action ->
        val (title, message, label, destructive) = confirmCopy(action)
        LogyxConfirmDialog(
            title = title, message = message, confirmLabel = label,
            onConfirm = onPerform, onDismiss = onDismissConfirm,
            dismissLabel = stringResource(R.string.trips_back), destructive = destructive, loading = state.busy,
        )
    }
}

private data class ConfirmCopy(val title: String, val message: String, val label: String, val destructive: Boolean)

@Composable
private fun confirmCopy(action: TripAction): ConfirmCopy = when (action) {
    is TripAction.Confirm -> ConfirmCopy(
        stringResource(R.string.trips_confirm_booking_title, kgText(action.booking.weightKg)),
        stringResource(R.string.trips_confirm_booking_message, action.booking.requesterName ?: "la PYME"),
        stringResource(R.string.trips_booking_confirm), false,
    )
    is TripAction.Reject -> ConfirmCopy(
        stringResource(R.string.trips_reject_booking_title),
        stringResource(R.string.trips_reject_booking_message, action.booking.requesterName ?: "la PYME"),
        stringResource(R.string.trips_booking_reject), true,
    )
    TripAction.Close -> ConfirmCopy(
        stringResource(R.string.trips_close_title), stringResource(R.string.trips_close_message),
        stringResource(R.string.trips_close_action), false,
    )
    TripAction.Cancel -> ConfirmCopy(
        stringResource(R.string.trips_cancel_title), stringResource(R.string.trips_cancel_message),
        stringResource(R.string.trips_cancel_action), true,
    )
}

/**
 * Reserva de espacio. [forCarrier]: muestra a la PYME y, si está por confirmar, Confirmar/Rechazar;
 * si no (PYME), muestra al transportista y [onCancel] cuando la reserva sigue viva.
 */
@Composable
internal fun BookingCard(
    booking: TripBookingView,
    forCarrier: Boolean,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit = {},
    onReject: () -> Unit = {},
    onCancel: (() -> Unit)? = null,
    onOpenShipment: (String) -> Unit = {},
) {
    LogyxCard(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                (if (forCarrier) booking.requesterName else booking.carrierName).orEmpty(), Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            StatusPill(booking.status.pill())
        }
        if (!forCarrier && booking.originCity != null && booking.destinationCity != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${cityLabel(booking.originCity)} → ${cityLabel(booking.destinationCity)} · ${stringResource(R.string.trips_departure, Fmt.dateTime(booking.departureAt))}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoChip(kgText(booking.weightKg))
            booking.cargoType?.let { InfoChip(it.label) }
            InfoChip(Fmt.money(booking.price), emphasized = true)
        }
        if (!booking.cargoDescription.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(booking.cargoDescription.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        }
        if (forCarrier) {
            booking.pickupAddress?.let { Text(stringResource(R.string.trips_booking_pickup, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            booking.deliveryAddress?.let { Text(stringResource(R.string.trips_booking_delivery, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (!booking.decisionNote.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            AlertBanner(stringResource(R.string.trips_booking_note, booking.decisionNote.orEmpty()), kind = MessageKind.Info)
        }
        val shipmentId = booking.shipmentId
        if (shipmentId != null) {
            Spacer(Modifier.height(10.dp))
            LogyxButton(
                stringResource(R.string.trips_view_shipment), { onOpenShipment(shipmentId) },
                variant = ButtonVariant.Secondary, size = ButtonSize.Medium,
            )
        }
        if (forCarrier && booking.status == BookingStatus.PENDING) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LogyxButton(stringResource(R.string.trips_booking_reject), onReject, Modifier.weight(1f), variant = ButtonVariant.Destructive, size = ButtonSize.Medium)
                LogyxButton(stringResource(R.string.trips_booking_confirm), onConfirm, Modifier.weight(1f), variant = ButtonVariant.Money, size = ButtonSize.Medium)
            }
        }
        if (!forCarrier && onCancel != null && (booking.status == BookingStatus.PENDING || booking.status == BookingStatus.CONFIRMED)) {
            Spacer(Modifier.height(12.dp))
            LogyxButton(stringResource(R.string.trips_booking_cancel), onCancel, variant = ButtonVariant.Destructive, size = ButtonSize.Medium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TripCardPreview() {
    LogyxTheme {
        Column(Modifier.padding(16.dp)) {
            TripCard(
                TripView(
                    "t1", carrierName = "Transportes Sur Andino", carrierTrustScore = 77.8, vehicleType = VehicleType.TRUCK_LARGE, vehiclePlate = "ASA-202",
                    originCity = "LIMA", destinationCity = "JULIACA", departureAt = "2026-11-14T13:00:00Z", capacityKg = 12000.0,
                    confirmedWeightKg = 4500.0, remainingKg = 7500.0, pricingMode = TripPricingMode.PER_KG, rate = 0.48,
                    status = TripStatus.FILLING, pendingBookings = 2,
                ),
                showCarrier = false,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PublishTripPreview() {
    LogyxTheme {
        PublishTripScreen(
            PublishTripUiState(
                loading = false, cities = listOf(CityItem("LIMA", "Lima", 0.0, 0.0)),
                vehicles = listOf(VehicleView("v1", "ASA-202", VehicleType.TRUCK_LARGE, 20000.0, null, VehicleStatus.AVAILABLE)),
            ),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}
