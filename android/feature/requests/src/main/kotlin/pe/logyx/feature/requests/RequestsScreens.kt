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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.AuctionCountdown
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
import pe.logyx.core.designsystem.label
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.RequestStatus
import pe.logyx.core.model.RequestView

// ───────────────────────── Lista ─────────────────────────

@Composable
fun RequestsRoute(onOpenRequest: (String) -> Unit, viewModel: RequestsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RequestsScreen(state, viewModel::refresh, onOpenRequest)
}

@Composable
fun RequestsScreen(state: RequestsUiState, onRefresh: () -> Unit, onOpen: (String) -> Unit) {
    LogyxScreen("Mis solicitudes") { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.items.isEmpty()) {
                            item {
                                EmptyState(
                                    "Aún no tienes solicitudes",
                                    "Publica tu primera carga desde la web de LOGYX; aquí podrás comparar las ofertas y seguir el envío.",
                                )
                            }
                        }
                        items(state.items, key = { it.id }) { r -> RequestCard(r) { onOpen(r.id) } }
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
        Spacer(Modifier.height(12.dp))
        RouteLine(
            cityLabel(r.originCity), cityLabel(r.destinationCity),
            distance = r.distanceKm?.let { Fmt.km(it) }, originSub = "Recojo ${Fmt.date(r.pickupDate)}",
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChip(r.cargoType.label)
                InfoChip(Fmt.tons(r.weightKg))
            }
            val count = r.auction?.bidCount ?: 0
            val max = r.auction?.maxBids ?: 5
            Text(
                if (count == 0L) "Sin ofertas aún" else "$count/$max ofertas",
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
        viewModel::askCancel, viewModel::confirmCancel, viewModel::dismissDialogs, viewModel::messageShown,
    )
}

@Composable
fun RequestDetailScreen(
    state: RequestDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAskAccept: (pe.logyx.core.model.BidView) -> Unit,
    onConfirmAccept: () -> Unit,
    onAskCancel: () -> Unit,
    onConfirmCancel: () -> Unit,
    onDismiss: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); onMessageShown() } }
    LogyxScreen("Solicitud", onBack = onBack, snackbarHostState = snackbar) { padding ->
        val r = state.request
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                r == null -> ErrorState(state.error ?: "No encontramos esta solicitud.", onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        RouteMap(state.origin, state.destination, height = 180.dp)
                        LogyxCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(r.status.pill())
                                if (r.status == RequestStatus.PUBLISHED) AuctionCountdown(r.auction)
                            }
                            Spacer(Modifier.height(12.dp))
                            RouteLine(cityLabel(r.originCity), cityLabel(r.destinationCity), distance = r.distanceKm?.let { Fmt.km(it) }, originSub = r.originAddress, destinationSub = r.destinationAddress)
                            Spacer(Modifier.height(10.dp))
                            Text(r.cargoDescription ?: r.cargoType.label, style = MaterialTheme.typography.bodyLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                                InfoChip(r.cargoType.label); InfoChip(Fmt.tons(r.weightKg)); InfoChip("Recojo ${Fmt.date(r.pickupDate)}")
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Precio sugerido ${Fmt.money(r.suggestedPrice)} · piso ${Fmt.money(r.priceFloor)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "Ofertas recibidas (${state.bids.size}${r.auction?.let { "/${it.maxBids}" } ?: ""})",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (state.bids.isEmpty()) {
                            EmptyState("Sin ofertas todavía", "Los transportistas compiten por tu carga. Te avisaremos cuando lleguen; también puedes desplazar para actualizar.")
                        }
                        state.bids.forEach { row ->
                            BidComparisonCard(row, canAccept = state.canDecide && row.bid.status == BidStatus.PENDING && !state.busy, onAccept = { onAskAccept(row.bid) })
                        }
                        if (state.canDecide) {
                            OutlinedButton(
                                onClick = onAskCancel, enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text("Cancelar solicitud") }
                        }
                    }
                }
            }
        }
        state.confirmBid?.let { bid ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("¿Aceptar esta oferta?") },
                text = { Text("Contratarás a ${bid.carrierName ?: "este transportista"} por ${Fmt.money(bid.amount)}. Las demás ofertas serán rechazadas y se creará el envío.") },
                confirmButton = { Button(onClick = onConfirmAccept, colors = ButtonDefaults.buttonColors(containerColor = LogyxTheme.colors.moneyStrong, contentColor = Color.White)) { Text("Aceptar oferta") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Volver") } },
            )
        }
        if (state.confirmCancel) {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("¿Cancelar la solicitud?") },
                text = { Text("Se cerrará la subasta y los transportistas dejarán de ver tu carga.") },
                confirmButton = { TextButton(onClick = onConfirmCancel) { Text("Sí, cancelar", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("No") } },
            )
        }
    }
}

@Composable
private fun BidComparisonCard(row: BidRow, canAccept: Boolean, onAccept: () -> Unit) {
    val bid = row.bid
    LogyxCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bid.carrierName ?: "Transportista", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    if (bid.carrierRucVerified) {
                        Spacer(Modifier.size(4.dp))
                        Icon(Icons.Rounded.Verified, "RUC verificado", Modifier.size(16.dp), tint = LogyxTheme.colors.money)
                    }
                }
                Text(Fmt.dateTime(bid.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.money(bid.amount), style = LogyxText.moneyMedium, color = LogyxTheme.colors.moneyText)
                row.diffVsSuggestedPct?.let { d ->
                    val label = when {
                        d < -0.5 -> "%.0f%% bajo el sugerido".format(-d)
                        d > 0.5 -> "%.0f%% sobre el sugerido".format(d)
                        else -> "Igual al sugerido"
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (d <= 0.5) LogyxTheme.colors.onMoneyContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!bid.notes.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("“${bid.notes}”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.isBest) InfoChip("Mejor precio", emphasized = true)
                if (bid.status != BidStatus.PENDING) StatusPill(bid.status.pill())
            }
            if (canAccept) {
                Button(onClick = onAccept, colors = ButtonDefaults.buttonColors(containerColor = LogyxTheme.colors.moneyStrong, contentColor = Color.White)) { Text("Aceptar") }
            }
        }
    }
}
