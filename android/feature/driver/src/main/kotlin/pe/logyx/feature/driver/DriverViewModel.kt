package pe.logyx.feature.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.data.userMessage
import pe.logyx.core.model.GeoPoint
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.StopStatus
import pe.logyx.core.model.StopType
import pe.logyx.core.model.StopView
import pe.logyx.core.model.nextStop
import pe.logyx.core.model.normalizeCity
import pe.logyx.core.model.toGeoPoint

enum class StopOp { ARRIVE, COMPLETE }

/** La única acción que el conductor puede hacer ahora mismo: un botón, un estado. */
data class DriverAction(val stop: StopView, val op: StopOp, val step: DriverStep)

/** Los cuatro pasos del viaje; la pantalla resuelve el texto de cada uno. */
enum class DriverStep { ARRIVE_PICKUP, PICKED_UP, ARRIVE_DESTINATION, DELIVERED }

/** Resultado exitoso de avanzar; la pantalla lo traduce a un mensaje. */
enum class DriverNotice { STOP_UPDATED, DELIVERED }

/**
 * Avanza el viaje: Llegué al recojo → Carga recogida → Llegué a destino → Entregado.
 * Devuelve null si el viaje ya terminó o no tiene paradas.
 */
fun ShipmentView.driverAction(): DriverAction? {
    if (status == ShipmentStatus.DELIVERED || status == ShipmentStatus.CANCELLED) return null
    val stop = nextStop() ?: return null
    val pickup = stop.type == StopType.PICKUP
    return when (stop.status) {
        StopStatus.PENDING -> DriverAction(stop, StopOp.ARRIVE, if (pickup) DriverStep.ARRIVE_PICKUP else DriverStep.ARRIVE_DESTINATION)
        StopStatus.ARRIVED -> DriverAction(stop, StopOp.COMPLETE, if (pickup) DriverStep.PICKED_UP else DriverStep.DELIVERED)
        StopStatus.COMPLETED -> null
    }
}

data class DriverUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val trip: ShipmentView? = null,
    val origin: GeoPoint? = null,
    val destination: GeoPoint? = null,
    /** Hay una llamada de parada en curso: el botón se deshabilita. */
    val acting: Boolean = false,
    /** Error al avanzar el viaje (se muestra sobre el botón, sin perder el viaje en pantalla). */
    val actionError: String? = null,
    /** Éxito de la última acción (se muestra una vez con MessageEffect). */
    val notice: DriverNotice? = null,
) {
    val action: DriverAction? get() = trip?.driverAction()
    val delivered: Boolean get() = trip?.status == ShipmentStatus.DELIVERED
}

class DriverTripViewModel(
    private val repo: ShipmentsRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DriverUiState())
    val state: StateFlow<DriverUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.trip == null, refreshing = !initial, error = null) }
        viewModelScope.launch {
            repo.driverActive().fold(
                onSuccess = { trip ->
                    val cities = catalog.cities().getOrDefault(emptyList())
                    fun geo(code: String?) = code?.let { c -> cities.firstOrNull { normalizeCity(it.code) == normalizeCity(c) }?.toGeoPoint() }
                    _state.update {
                        it.copy(
                            loading = false, refreshing = false, trip = trip,
                            origin = geo(trip?.originCity), destination = geo(trip?.destinationCity), actionError = null,
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    /** Ejecuta la acción actual (arrive/complete) y reemplaza el viaje con la respuesta del servidor. */
    fun advance() {
        val s = _state.value
        val trip = s.trip ?: return
        val action = s.action ?: return
        if (s.acting) return
        _state.update { it.copy(acting = true, actionError = null) }
        viewModelScope.launch {
            val result = when (action.op) {
                StopOp.ARRIVE -> repo.arrive(trip.id, action.stop.id)
                StopOp.COMPLETE -> repo.complete(trip.id, action.stop.id)
            }
            result.fold(
                onSuccess = { updated ->
                    val notice = if (updated.status == ShipmentStatus.DELIVERED) DriverNotice.DELIVERED else DriverNotice.STOP_UPDATED
                    _state.update { it.copy(acting = false, trip = updated, notice = notice) }
                },
                onFailure = { e -> _state.update { it.copy(acting = false, actionError = e.userMessage()) } },
            )
        }
    }

    fun onNoticeShown() = _state.update { it.copy(notice = null) }

    fun dismissActionError() = _state.update { it.copy(actionError = null) }
}
