package pe.logyx.feature.loads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxPalette
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.AuctionCountdown
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.InfoChip
import pe.logyx.core.designsystem.components.KpiTile
import pe.logyx.core.designsystem.components.LoadCard
import pe.logyx.core.designsystem.components.LoadingState
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.PriceTag
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.RouteLine
import pe.logyx.core.designsystem.components.RouteMap
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.TruckIllustration
import pe.logyx.core.designsystem.label
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.AuctionStatus
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.VehicleView
import pe.logyx.core.model.isCompatibleWith

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
    LogyxScreen("Tu actividad") { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                state.error != null && state.suggested.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    LazyColumn(
                        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            val first = state.userName.substringBefore(' ').ifBlank { "transportista" }
                            Text("Hola, $first", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                if (state.compatibleCount > 0) "Hay ${state.compatibleCount} cargas que tu flota puede llevar hoy."
                                else "Aún no hay cargas compatibles con tu flota.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                KpiTile("${state.compatibleCount}", "Para ti", Modifier.weight(1f), "cargas compatibles")
                                KpiTile(
                                    "${state.pendingBids}", "Ofertas", Modifier.weight(1f), "pendientes de ${state.totalBids}",
                                    accent = LogyxTheme.colors.moneyText,
                                )
                            }
                        }
                        state.activeShipment?.let { s -> item { ActiveShipmentCard(s) { onOpenShipment(s.id) } } }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Cargas sugeridas", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = onOpenFeed) { Text("Ver todas") }
                            }
                        }
                        if (state.suggested.isEmpty()) {
                            item {
                                EmptyState(
                                    "Sin cargas sugeridas",
                                    if (state.vehicles.isEmpty()) "Registra tus vehículos en la web para ver cargas compatibles." else "Cuando una PYME publique una carga para tu flota, aparecerá aquí.",
                                    actionLabel = "Explorar el marketplace", onAction = onOpenFeed,
                                )
                            }
                        } else {
                            items(state.suggested, key = { it.id }) { r -> LoadCard(r, compatible = true, onClick = { onOpenLoad(r.id) }) }
                        }
                        item {
                            TextButton(onClick = onOpenBids, Modifier.fillMaxWidth()) { Text("Ver mis ofertas") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveShipmentCard(s: ShipmentView, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(LogyxPalette.Ink900, MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(16.dp),
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalShipping, null, tint = Color.White)
                    Spacer(Modifier.size(8.dp))
                    Text("Viaje activo", style = MaterialTheme.typography.titleSmall, color = Color.White)
                }
                StatusPill(s.status.pill())
            }
            Spacer(Modifier.height(12.dp))
            CompositionLocalOnDark { RouteLine(cityLabel(s.originCity), cityLabel(s.destinationCity), originSub = s.vehiclePlate, destinationSub = s.driverName ?: "Sin conductor") }
        }
    }
}

/** Sobre fondo oscuro, el texto debe ser claro. */
@Composable
private fun CompositionLocalOnDark(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides Color.White,
    ) { content() }
}

// ───────────────────────── Feed ─────────────────────────

@Composable
fun LoadsFeedRoute(onOpenLoad: (String) -> Unit, viewModel: LoadsFeedViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoadsFeedScreen(state, viewModel::refresh, viewModel::toggle, onOpenLoad)
}

private val filterMeta: List<Triple<LoadFilter, String, ImageVector>> = listOf(
    Triple(LoadFilter.MyVehicle, "Mi vehículo", Icons.Rounded.LocalShipping),
    Triple(LoadFilter.BestPay, "Mejor pago", Icons.Rounded.Paid),
    Triple(LoadFilter.Nearby, "Cerca de mí", Icons.Rounded.NearMe),
)

@Composable
fun LoadsFeedScreen(
    state: LoadsFeedUiState,
    onRefresh: () -> Unit,
    onToggle: (LoadFilter) -> Unit,
    onOpenLoad: (String) -> Unit,
) {
    LogyxScreen("Cargas disponibles") { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filterMeta) { (filter, label, icon) ->
                    FilterChip(
                        selected = filter in state.filters, onClick = { onToggle(filter) }, label = { Text(label) },
                        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                state.loading -> LoadingState()
                state.error != null && state.all.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh, Modifier.weight(1f)) {
                    val visible = state.visible
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (visible.isEmpty()) {
                            item {
                                EmptyState(
                                    title = if (state.all.isEmpty()) "Sin cargas por ahora" else "Ninguna carga coincide",
                                    message = when {
                                        state.nearbyUnavailable -> "Tu empresa no tiene una ciudad registrada, así que no podemos filtrar por cercanía."
                                        state.all.isEmpty() -> "Cuando las PYMEs publiquen cargas las verás aquí. Desliza hacia abajo para actualizar."
                                        else -> "Prueba quitando algún filtro para ver más cargas."
                                    },
                                )
                            }
                        }
                        items(visible, key = { it.id }) { r -> LoadCard(r, state.isCompatible(r), { onOpenLoad(r.id) }) }
                    }
                }
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
        viewModel::selectVehicle, viewModel::onAmountChange, viewModel::onNotesChange, viewModel::submitBid, viewModel::messageShown,
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
    onSubmitBid: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }
    LogyxScreen("Detalle de carga", onBack = onBack, snackbarHostState = snackbar) { padding ->
        val r = state.request
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                r == null -> ErrorState(state.error ?: "No encontramos esta carga.", onRefresh)
                else -> Column(Modifier.fillMaxSize()) {
                    Refreshable(state.refreshing, onRefresh, Modifier.weight(1f)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            RouteMap(state.origin, state.destination, height = 220.dp)
                            LogyxCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                    PriceTag(r.suggestedPrice, label = "Precio referencial")
                                    AuctionCountdown(r.auction)
                                }
                                Spacer(Modifier.height(14.dp))
                                RouteLine(
                                    cityLabel(r.originCity), cityLabel(r.destinationCity),
                                    distance = r.distanceKm?.let { Fmt.km(it) },
                                    originSub = r.originAddress, destinationSub = r.destinationAddress,
                                )
                            }
                            LogyxCard(Modifier.fillMaxWidth()) {
                                Text("La carga", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                Text(r.cargoDescription ?: r.cargoType.label, style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(10.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    InfoChip(r.cargoType.label)
                                    InfoChip(Fmt.tons(r.weightKg))
                                    r.volumeM3?.let { InfoChip("${Fmt.decimal1(it)} m³") }
                                    InfoChip("Recojo ${Fmt.date(r.pickupDate)}")
                                    r.auction?.let { InfoChip("${it.bidCount}/${it.maxBids} ofertas") }
                                    if (r.isCompatibleWith(state.vehicles)) InfoChip("Compatible con tu flota", emphasized = true, icon = Icons.Rounded.CheckCircle)
                                }
                                Spacer(Modifier.height(10.dp))
                                Text("Publica: ${r.requesterName ?: "—"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                r.priceFloor?.let {
                                    Text("Monto mínimo para ofertar: ${Fmt.money(it)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            r.myBid?.let { bid ->
                                LogyxCard(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Tu oferta", style = MaterialTheme.typography.titleSmall)
                                        StatusPill(bid.status.pill())
                                    }
                                    PriceTag(bid.amount, large = false)
                                    bid.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                    val canBid = r.myBid == null && r.auction?.status == AuctionStatus.OPEN
                    if (canBid) {
                        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                            Button(
                                onClick = onOpenBid, modifier = Modifier.fillMaxWidth().height(54.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LogyxTheme.colors.moneyStrong, contentColor = Color.White),
                            ) { Text("Ofertar", style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                }
            }
        }
        if (state.form != null && r != null) {
            BidSheet(state, r, onDismissBid, onSelectVehicle, onAmountChange, onNotesChange, onSubmitBid)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BidSheet(
    state: LoadDetailUiState,
    request: RequestView,
    onDismiss: () -> Unit,
    onSelectVehicle: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val form = state.form ?: return
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ofertar", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${cityLabel(request.originCity)} → ${cityLabel(request.destinationCity)} · ${Fmt.tons(request.weightKg)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Vehículo", style = MaterialTheme.typography.titleSmall)
            if (state.compatibleVehicles.isEmpty()) {
                Text(
                    "Ninguno de tus vehículos disponibles soporta ${Fmt.tons(request.weightKg)}. Registra o libera un vehículo en la web para poder ofertar.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
            }
            state.compatibleVehicles.forEach { v ->
                VehicleOption(v, selected = v.id == form.vehicleId) { onSelectVehicle(v.id) }
            }
            OutlinedTextField(
                value = form.amountText, onValueChange = onAmountChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Tu monto") }, prefix = { Text("S/ ") },
                textStyle = LogyxText.moneyMedium,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                supportingText = {
                    Text("Piso ${Fmt.money(request.priceFloor)} · Referencial ${Fmt.money(request.suggestedPrice)}")
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                request.priceFloor?.let { QuickAmount("Piso", it, onAmountChange) }
                request.suggestedPrice?.let { QuickAmount("Referencial", it, onAmountChange) }
            }
            OutlinedTextField(
                value = form.notes, onValueChange = onNotesChange, modifier = Modifier.fillMaxWidth(),
                label = { Text("Nota para la PYME (opcional)") }, maxLines = 3,
                supportingText = { Text("${form.notes.length}/300") },
            )
            if (form.error != null) {
                Text(
                    form.error,
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small).padding(12.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = onSubmit, enabled = !form.submitting && state.compatibleVehicles.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LogyxTheme.colors.moneyStrong, contentColor = Color.White),
            ) {
                if (form.submitting) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Enviar oferta", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun QuickAmount(label: String, amount: Double, onAmountChange: (String) -> Unit) {
    FilterChip(
        selected = false,
        onClick = { onAmountChange(String.format(java.util.Locale.US, "%.2f", amount)) },
        label = { Text("$label ${Fmt.moneyShort(amount)}") },
    )
}

@Composable
private fun VehicleOption(v: VehicleView, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .androidBorder(border, if (selected) 2.dp else 1.dp)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TruckIllustration(v.type, Modifier.size(width = 72.dp, height = 40.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.plate, style = LogyxText.moneySmall)
            Text("${v.type.label} · hasta ${Fmt.tons(v.maxWeightKg)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
    }
}

private fun Modifier.androidBorder(color: Color, width: androidx.compose.ui.unit.Dp) =
    this.then(Modifier.border(width, color, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)))

// ───────────────────────── Mis ofertas ─────────────────────────

@Composable
fun MyBidsRoute(onOpenLoad: (String) -> Unit, viewModel: MyBidsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyBidsScreen(state, viewModel::refresh, onOpenLoad)
}

@Composable
fun MyBidsScreen(state: MyBidsUiState, onRefresh: () -> Unit, onOpenLoad: (String) -> Unit) {
    LogyxScreen("Mis ofertas") { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.items.isEmpty()) {
                            item { EmptyState("Aún no has ofertado", "Explora el marketplace y envía tu primera oferta. Aquí verás si ganas la carga.") }
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
        Spacer(Modifier.height(10.dp))
        RouteLine(cityLabel(r.originCity), cityLabel(r.destinationCity), distance = r.distanceKm?.let { Fmt.km(it) }, originSub = "Recojo ${Fmt.date(r.pickupDate)}")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoChip(r.cargoType.label)
            InfoChip(Fmt.tons(r.weightKg))
            if (bid.status == pe.logyx.core.model.BidStatus.PENDING) AuctionCountdown(r.auction)
        }
    }
}

