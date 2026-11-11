package pe.logyx.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.data.TripsRepository
import pe.logyx.core.data.refreshSignals
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LiveEffect
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.model.BookingStatus
import pe.logyx.core.model.RealtimeTypes
import pe.logyx.core.model.TripBookingView
import pe.logyx.core.model.TripView

// ───────────────────────── Detalle del viaje (PYME) ─────────────────────────

data class PymeTripDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val trip: TripView? = null,
    /** Mis reservas en este viaje (vivas primero, luego las más recientes). */
    val bookings: List<TripBookingView> = emptyList(),
)

/** Detalle mínimo que abre el aviso de viaje de la PYME: el viaje y mis reservas en él. */
class PymeTripDetailViewModel(
    private val tripId: String,
    private val trips: TripsRepository,
    private val realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PymeTripDetailUiState())
    val state: StateFlow<PymeTripDetailUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    /** Tiempo real (solo en primer plano): `trip_booking.updated`/`trip.cancelled` de ESTE viaje refresca en silencio. */
    suspend fun liveLoop() {
        realtime.refreshSignals(setOf(RealtimeTypes.TRIP_BOOKING_UPDATED, RealtimeTypes.TRIP_CANCELLED)) { it.string("tripId") == tripId }
            .collect { load(initial = false, silent = true) }
    }

    private fun load(initial: Boolean, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = initial && it.trip == null, refreshing = !initial, error = null) }
        viewModelScope.launch {
            val tripRes = async { trips.trip(tripId) }
            val mineRes = async { trips.myBookings() }
            tripRes.await().fold(
                onSuccess = { t ->
                    val mine = mineRes.await().getOrNull()?.filter { it.tripId == tripId }
                    _state.update {
                        it.copy(
                            loading = false, refreshing = false, trip = t,
                            bookings = (mine ?: it.bookings).sortedWith(
                                compareBy<TripBookingView> { b -> b.status != BookingStatus.PENDING && b.status != BookingStatus.CONFIRMED }
                                    .thenByDescending { b -> b.createdAt.orEmpty() },
                            ),
                        )
                    }
                },
                onFailure = { e -> if (!silent) _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}

@Composable
fun PymeTripDetailRoute(
    tripId: String,
    onBack: () -> Unit,
    onOpenShipment: (String) -> Unit,
    viewModel: PymeTripDetailViewModel = koinViewModel { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiveEffect(viewModel) { viewModel.liveLoop() }
    PymeTripDetailScreen(state, onBack, viewModel::refresh, onOpenShipment)
}

@Composable
fun PymeTripDetailScreen(
    state: PymeTripDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenShipment: (String) -> Unit,
) {
    LogyxScreen(stringResource(R.string.trips_detail_title), onBack = onBack) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val trip = state.trip
            when {
                state.loading -> SkeletonList()
                trip == null -> ErrorState(state.error ?: stringResource(R.string.trips_detail_not_found), onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { TripCard(trip, showCarrier = true) }
                        item {
                            Text(
                                stringResource(R.string.trips_pyme_my_bookings_title), Modifier.semantics { heading() },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        if (state.bookings.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.trips_pyme_my_bookings_empty), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(state.bookings, key = { it.id }) { b ->
                                BookingCard(b, forCarrier = false, onOpenShipment = onOpenShipment)
                            }
                        }
                    }
                }
            }
        }
    }
}
