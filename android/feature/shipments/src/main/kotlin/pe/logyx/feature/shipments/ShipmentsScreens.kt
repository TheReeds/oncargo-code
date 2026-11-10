package pe.logyx.feature.shipments

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.InfoChip
import pe.logyx.core.designsystem.components.LoadingState
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.RouteLine
import pe.logyx.core.designsystem.components.RouteMap
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.StopTimeline
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.DriverView
import pe.logyx.core.model.ShipmentView

@Composable
fun ShipmentsRoute(onOpen: (String) -> Unit, viewModel: ShipmentsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShipmentsScreen(state, viewModel::refresh, onOpen)
}

@Composable
fun ShipmentsScreen(state: ShipmentsUiState, onRefresh: () -> Unit, onOpen: (String) -> Unit) {
    LogyxScreen("Envíos") { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.items.isEmpty()) {
                            item {
                                EmptyState(
                                    "Aún no hay envíos",
                                    if (state.isCarrier) "Cuando una PYME acepte tu oferta, el envío aparecerá aquí para que asignes un conductor."
                                    else "Cuando aceptes una oferta, podrás seguir tu envío aquí.",
                                )
                            }
                        }
                        items(state.items, key = { it.id }) { s -> ShipmentCard(s, state.isCarrier) { onOpen(s.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShipmentCard(s: ShipmentView, isCarrier: Boolean, onClick: () -> Unit) {
    LogyxCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatusPill(s.status.pill())
            Text(Fmt.money(s.agreedPrice), style = LogyxText.moneySmall, color = LogyxTheme.colors.moneyText)
        }
        Spacer(Modifier.height(12.dp))
        RouteLine(cityLabel(s.originCity), cityLabel(s.destinationCity), originSub = s.vehiclePlate)
        Spacer(Modifier.height(8.dp))
        Text(
            if (isCarrier) "Cliente: ${s.requesterName ?: "—"}" else "Transporta: ${s.carrierName ?: "—"}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val driver = s.driverName
        if (driver != null) Text("Conductor: $driver", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (isCarrier) InfoChip("Falta asignar conductor")
    }
}

@Composable
fun ShipmentDetailRoute(shipmentId: String, onBack: () -> Unit, viewModel: ShipmentDetailViewModel = koinViewModel { parametersOf(shipmentId) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShipmentDetailScreen(
        state, onBack, viewModel::refresh, viewModel::openDriverSheet, viewModel::dismissDriverSheet, viewModel::assign, viewModel::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); onMessageShown() } }
    LogyxScreen("Seguimiento", onBack = onBack, snackbarHostState = snackbar) { padding ->
        val s = state.shipment
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                s == null -> ErrorState(state.error ?: "No encontramos este envío.", onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        RouteMap(state.origin, state.destination, height = 200.dp)
                        LogyxCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(s.status.pill())
                                Text(Fmt.money(s.agreedPrice), style = LogyxText.moneyMedium, color = LogyxTheme.colors.moneyText)
                            }
                            Spacer(Modifier.height(12.dp))
                            RouteLine(cityLabel(s.originCity), cityLabel(s.destinationCity))
                            Spacer(Modifier.height(10.dp))
                            Text(s.cargoDescription ?: "Carga", style = MaterialTheme.typography.bodyLarge)
                            Text("${Fmt.tons(s.weightKg)} · ${s.vehiclePlate ?: "Sin vehículo"}", style = LogyxText.mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Text("Cliente: ${s.requesterName ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Transportista: ${s.carrierName ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Conductor: ${s.driverName ?: "Por asignar"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.canAssign) {
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = onOpenDrivers, Modifier.fillMaxWidth()) { Text(if (s.driverId == null) "Asignar conductor" else "Cambiar conductor") }
                            }
                        }
                        LogyxCard(Modifier.fillMaxWidth()) {
                            Text("Paradas", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(10.dp))
                            if (s.stops.isEmpty()) Text("Aún sin paradas.", style = MaterialTheme.typography.bodyMedium) else StopTimeline(s.stops)
                        }
                        s.deliveredAt?.let { Text("Entregado el ${Fmt.dateTime(it)}", style = MaterialTheme.typography.labelLarge, color = LogyxTheme.colors.onMoneyContainer) }
                    }
                }
            }
        }
        state.drivers?.let { drivers ->
            ModalBottomSheet(onDismissRequest = onDismissDrivers) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Asignar conductor", style = MaterialTheme.typography.headlineSmall)
                    when {
                        state.driversLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        drivers.isEmpty() -> Text("No tienes conductores disponibles. Registra uno en la web.", style = MaterialTheme.typography.bodyMedium)
                        else -> drivers.forEach { d ->
                            LogyxCard(Modifier.fillMaxWidth().clickable(enabled = !state.assigning) { onAssign(d) }) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(d.fullName, style = MaterialTheme.typography.titleSmall)
                                        Text("Licencia ${d.licenseNumber ?: "—"}", style = LogyxText.mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (state.assigning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
