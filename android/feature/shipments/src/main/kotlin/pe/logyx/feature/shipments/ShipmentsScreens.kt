package pe.logyx.feature.shipments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.AlertBanner
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
import pe.logyx.core.designsystem.components.MessageKind
import pe.logyx.core.designsystem.components.PillTone
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.RouteLine
import pe.logyx.core.designsystem.components.RouteMap
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.RadioCardRow
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.StopTimeline
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.DriverStatus
import pe.logyx.core.model.DriverView
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.VehicleType

@Composable
fun ShipmentsRoute(onOpen: (String) -> Unit, viewModel: ShipmentsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShipmentsScreen(state, viewModel::refresh, onOpen)
}

private enum class ShipmentFilter(val labelRes: Int, val statuses: Set<ShipmentStatus>?) {
    All(R.string.shipments_filter_all, null),
    Pending(R.string.shipments_filter_pending, setOf(ShipmentStatus.PENDING)),
    InTransit(R.string.shipments_filter_in_transit, setOf(ShipmentStatus.IN_TRANSIT)),
    Delivered(R.string.shipments_filter_delivered, setOf(ShipmentStatus.DELIVERED)),
}

@Composable
fun ShipmentsScreen(state: ShipmentsUiState, onRefresh: () -> Unit, onOpen: (String) -> Unit) {
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    val filter = ShipmentFilter.entries[filterIndex]
    LogyxScreen(stringResource(R.string.shipments_title)) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList(Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
                state.error != null && state.items.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    val visible = remember(state.items, filter) {
                        filter.statuses?.let { st -> state.items.filter { it.status in st } } ?: state.items
                    }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.items.isNotEmpty()) {
                            item(key = "filters") {
                                ChipRow {
                                    ShipmentFilter.entries.forEach { f ->
                                        LogyxFilterChip(stringResource(f.labelRes), f == filter, { filterIndex = f.ordinal })
                                    }
                                }
                            }
                        }
                        if (state.items.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    stringResource(R.string.shipments_empty_title),
                                    stringResource(if (state.isCarrier) R.string.shipments_empty_carrier else R.string.shipments_empty_pyme),
                                )
                            }
                        } else if (visible.isEmpty()) {
                            item(key = "empty-filter") {
                                EmptyState(
                                    stringResource(R.string.shipments_empty_filtered_title),
                                    stringResource(R.string.shipments_empty_filtered_message),
                                    actionLabel = stringResource(R.string.shipments_empty_filtered_action),
                                    onAction = { filterIndex = 0 },
                                )
                            }
                        }
                        items(visible, key = { it.id }) { s -> ShipmentCard(s, state.isCarrier) { onOpen(s.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShipmentCard(s: ShipmentView, isCarrier: Boolean, onClick: () -> Unit) {
    val dash = stringResource(R.string.shipments_no_value)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    LogyxCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatusPill(s.status.pill())
            Text(Fmt.money(s.agreedPrice), style = LogyxText.moneySmall, color = LogyxTheme.colors.moneyText)
        }
        Spacer(Modifier.height(16.dp))
        RouteLine(cityLabel(s.originCity), cityLabel(s.destinationCity))
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(
                if (isCarrier) R.string.shipments_client_format else R.string.shipments_carrier_format,
                (if (isCarrier) s.requesterName else s.carrierName) ?: dash,
            ),
            style = MaterialTheme.typography.bodyMedium, color = muted,
        )
        val driver = s.driverName
        if (driver != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(driver, style = MaterialTheme.typography.bodyMedium)
                s.vehiclePlate?.let { Text(it, style = LogyxText.mono, color = muted) }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            if (isCarrier) InfoChip(stringResource(R.string.shipments_driver_missing), emphasized = true)
            s.vehiclePlate?.let { Text(it, style = LogyxText.mono, color = muted) }
        }
    }
}

@Composable
fun ShipmentDetailRoute(shipmentId: String, onBack: () -> Unit, viewModel: ShipmentDetailViewModel = koinViewModel { parametersOf(shipmentId) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShipmentDetailScreen(
        state, onBack, viewModel::refresh, viewModel::openDriverSheet, viewModel::dismissDriverSheet, viewModel::assign, viewModel::onMessageShown,
    )
}

@Composable
fun ShipmentDetailScreen(
    state: ShipmentDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDrivers: () -> Unit,
    onDismissDrivers: () -> Unit,
    onAssign: (DriverView) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    var pending by remember { mutableStateOf<DriverView?>(null) }
    LaunchedEffect(state.assigning) { if (!state.assigning) pending = null }

    LogyxScreen(stringResource(R.string.shipments_detail_title), onBack = onBack, snackbar = snackbar) { padding ->
        val s = state.shipment
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList(Modifier.padding(16.dp), count = 2)
                s == null -> ErrorState(state.error ?: stringResource(R.string.shipments_detail_not_found), onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    TrackingContent(state, s, onOpenDrivers)
                }
            }
        }
        state.drivers?.let { drivers ->
            val s = state.shipment
            DriversSheet(
                drivers = drivers,
                loading = state.driversLoading,
                onDismiss = onDismissDrivers,
                onPick = { pending = it },
            )
            pending?.let { d ->
                LogyxConfirmDialog(
                    title = stringResource(R.string.shipments_confirm_title, d.fullName),
                    message = stringResource(
                        R.string.shipments_confirm_message,
                        cityLabel(s?.originCity), cityLabel(s?.destinationCity),
                    ),
                    confirmLabel = stringResource(R.string.shipments_confirm_action),
                    onConfirm = { onAssign(d) },
                    onDismiss = { if (!state.assigning) pending = null },
                    dismissLabel = stringResource(R.string.shipments_confirm_dismiss),
                    loading = state.assigning,
                )
            }
        }
    }
}

@Composable
private fun TrackingContent(state: ShipmentDetailUiState, s: ShipmentView, onOpenDrivers: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dash = stringResource(R.string.shipments_no_value)
    val mapDescription = stringResource(R.string.shipments_detail_map_description, cityLabel(s.originCity), cityLabel(s.destinationCity))
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RouteMap(state.origin, state.destination, contentDescription = mapDescription, height = 220.dp)
        LogyxCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                StatusPill(s.status.pill())
                Text(Fmt.money(s.agreedPrice), style = LogyxText.moneyMedium, color = LogyxTheme.colors.moneyText)
            }
            Spacer(Modifier.height(16.dp))
            RouteLine(cityLabel(s.originCity), cityLabel(s.destinationCity))
            Spacer(Modifier.height(16.dp))
            Text(s.cargoDescription ?: stringResource(R.string.shipments_detail_cargo_default), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "${Fmt.tons(s.weightKg)} · ${s.vehiclePlate ?: stringResource(R.string.shipments_detail_no_vehicle)}",
                style = LogyxText.mono, color = muted,
            )
            Spacer(Modifier.height(16.dp))
            InfoRow(stringResource(R.string.shipments_detail_requester), s.requesterName ?: dash)
            InfoRow(stringResource(R.string.shipments_detail_carrier), s.carrierName ?: dash)
            InfoRow(stringResource(R.string.shipments_detail_driver), s.driverName ?: stringResource(R.string.shipments_detail_driver_pending))
            if (state.canAssign) {
                Spacer(Modifier.height(16.dp))
                LogyxButton(
                    stringResource(if (s.driverId == null) R.string.shipments_assign_action else R.string.shipments_change_action),
                    onOpenDrivers,
                    variant = if (s.driverId == null) ButtonVariant.Primary else ButtonVariant.Secondary,
                    icon = Icons.Rounded.Person,
                )
            }
        }
        LogyxCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.shipments_detail_stops), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(16.dp))
            if (s.stops.isEmpty()) {
                Text(stringResource(R.string.shipments_detail_no_stops), style = MaterialTheme.typography.bodyMedium, color = muted)
            } else {
                StopTimeline(s.stops)
            }
        }
        s.deliveredAt?.let {
            AlertBanner(
                stringResource(R.string.shipments_detail_delivered_format, Fmt.dateTime(it)),
                kind = MessageKind.Success,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun DriversSheet(drivers: List<DriverView>, loading: Boolean, onDismiss: () -> Unit, onPick: (DriverView) -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = drivers.firstOrNull { it.id == selectedId }
    LogyxBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.shipments_sheet_title)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                loading -> SkeletonList(count = 3)
                drivers.isEmpty() -> EmptyState(
                    stringResource(R.string.shipments_sheet_empty_title),
                    stringResource(R.string.shipments_sheet_empty_message),
                    vehicle = VehicleType.VAN,
                )
                else -> {
                    drivers.forEach { d -> DriverRow(d, d.id == selectedId) { selectedId = d.id } }
                    LogyxButton(
                        text = selected?.let { stringResource(R.string.shipments_sheet_continue, it.fullName) }
                            ?: stringResource(R.string.shipments_sheet_continue_none),
                        onClick = { selected?.let(onPick) },
                        enabled = selected != null,
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverRow(d: DriverView, selected: Boolean, onClick: () -> Unit) {
    RadioCardRow(selected = selected, onClick = onClick) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(d.fullName, style = MaterialTheme.typography.titleMedium)
            Text(
                d.licenseNumber?.let { stringResource(R.string.shipments_sheet_license_format, it) }
                    ?: stringResource(R.string.shipments_sheet_license_missing),
                style = LogyxText.mono, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusPill(
            stringResource(if (d.status == DriverStatus.AVAILABLE) R.string.shipments_driver_available else R.string.shipments_driver_on_duty),
            if (d.status == DriverStatus.AVAILABLE) PillTone.Emerald else PillTone.Petrol,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ShipmentsScreenPreview() {
    LogyxTheme {
        ShipmentsScreen(
            ShipmentsUiState(
                loading = false, isCarrier = true,
                items = listOf(
                    ShipmentView(id = "1", originCity = "LIMA", destinationCity = "AREQUIPA", agreedPrice = 1850.0, vehiclePlate = "ABC-123", status = ShipmentStatus.PENDING, requesterName = "Textiles Andinos"),
                    ShipmentView(id = "2", originCity = "LIMA", destinationCity = "CUSCO", agreedPrice = 2400.0, vehiclePlate = "XYZ-987", driverName = "Luis Quispe", status = ShipmentStatus.IN_TRANSIT, requesterName = "Café Selva"),
                ),
            ),
            onRefresh = {}, onOpen = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ShipmentDetailScreenPreview() {
    LogyxTheme {
        ShipmentDetailScreen(
            ShipmentDetailUiState(
                loading = false, isCarrier = true,
                shipment = ShipmentView(
                    id = "1", originCity = "LIMA", destinationCity = "AREQUIPA", agreedPrice = 1850.0, weightKg = 2500.0,
                    vehiclePlate = "ABC-123", cargoDescription = "Textiles", status = ShipmentStatus.PENDING,
                    requesterName = "Textiles Andinos", carrierName = "Transportes Sur",
                ),
            ),
            onBack = {}, onRefresh = {}, onOpenDrivers = {}, onDismissDrivers = {}, onAssign = {}, onMessageShown = {},
        )
    }
}
