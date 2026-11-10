package pe.logyx.feature.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxPalette
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LoadingState
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.RouteMap
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.StopTimeline
import pe.logyx.core.designsystem.label
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.nextStop

@Composable
fun DriverTripRoute(viewModel: DriverTripViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DriverTripScreen(state, viewModel::refresh, viewModel::advance)
}

/** Modo conductor (action-first): mapa, siguiente parada y UN botón grande. */
@Composable
fun DriverTripScreen(state: DriverUiState, onRefresh: () -> Unit, onAdvance: () -> Unit) {
    val trip = state.trip
    Box(Modifier.fillMaxSize().background(LogyxPalette.Ink900)) {
        when {
            state.loading -> LoadingState(Modifier.background(MaterialTheme.colorScheme.background))
            state.error != null && trip == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) { ErrorState(state.error, onRefresh) }
            trip == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentAlignment = Alignment.Center) {
                Refreshable(state.refreshing, onRefresh) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                        EmptyState("Sin viaje asignado", "Cuando tu transportista te asigne un envío aparecerá aquí. Desliza hacia abajo para actualizar.")
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(260.dp)) {
                    RouteMap(state.origin, state.destination, Modifier.fillMaxSize(), fillParent = true)
                    Row(
                        Modifier.statusBarsPadding().padding(12.dp).background(LogyxPalette.Ink900.copy(alpha = 0.92f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${cityLabel(trip.originCity)} → ${cityLabel(trip.destinationCity)}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.size(10.dp))
                        StatusPill(trip.status.pill())
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                ) {
                    Refreshable(state.refreshing, onRefresh, Modifier.weight(1f)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (state.delivered) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(48.dp), tint = LogyxTheme.colors.money)
                                    Spacer(Modifier.size(12.dp))
                                    Column {
                                        Text("¡Viaje entregado!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                        Text("Buen trabajo. Entregado ${Fmt.dateTime(trip.deliveredAt)}", style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            } else {
                                val next = trip.nextStop()
                                Text("SIGUIENTE PARADA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                if (next != null) {
                                    Text("${next.type.label} · ${cityLabel(next.city)}", fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                                    next.address?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                            Text("${Fmt.tons(trip.weightKg)} · ${trip.vehiclePlate ?: "—"} · ${trip.cargoDescription ?: "Carga"}", style = LogyxText.mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            StopTimeline(trip.stops, large = true)
                        }
                    }
                    state.action?.let { action ->
                        val haptic = LocalHapticFeedback.current
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                            if (state.actionError != null) {
                                Text(
                                    state.actionError,
                                    Modifier.fillMaxWidth().padding(bottom = 8.dp).background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small).padding(12.dp),
                                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            Button(
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAdvance() },
                                enabled = !state.acting,
                                modifier = Modifier.fillMaxWidth().height(76.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LogyxTheme.colors.moneyStrong, contentColor = Color.White),
                            ) {
                                if (state.acting) CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp, color = Color.White)
                                else Text(action.label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
