package pe.logyx.feature.shipments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.model.DriverStatus
import pe.logyx.core.model.DriverView
import pe.logyx.core.model.GeoPoint
import pe.logyx.core.model.OrgType
import pe.logyx.core.model.Role
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.isActive
import pe.logyx.core.model.normalizeCity
import pe.logyx.core.model.toGeoPoint

data class ShipmentsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<ShipmentView> = emptyList(),
    val isCarrier: Boolean = false,
)

class ShipmentsViewModel(auth: AuthRepository, private val repo: ShipmentsRepository) : ViewModel() {
    private val user = (auth.state.value as? SessionState.LoggedIn)?.user
    private val _state = MutableStateFlow(ShipmentsUiState(isCarrier = user?.organizationType == OrgType.CARRIER && user.role != Role.DRIVER))
    val state: StateFlow<ShipmentsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.items.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            repo.list().fold(
                onSuccess = { list ->
                    // Activos primero; luego por fecha descendente.
                    val sorted = list.sortedWith(compareByDescending<ShipmentView> { it.isActive }.thenByDescending { it.createdAt.orEmpty() })
                    _state.update { it.copy(loading = false, refreshing = false, items = sorted) }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}

data class ShipmentDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val shipment: ShipmentView? = null,
    val origin: GeoPoint? = null,
    val destination: GeoPoint? = null,
    val isCarrier: Boolean = false,
    /** Lista de conductores del bottom sheet; null = sheet cerrado. */
    val drivers: List<DriverView>? = null,
    val driversLoading: Boolean = false,
    val assigning: Boolean = false,
    val message: UiMessage? = null,
    /** Calificación (RN06): null = aún no se sabe / no aplica. */
    val canReview: Boolean? = null,
    val hasReviews: Boolean = false,
) {
    val canAssign: Boolean get() = isCarrier && shipment?.status == ShipmentStatus.PENDING
}

class ShipmentDetailViewModel(
    private val shipmentId: String,
    auth: AuthRepository,
    private val repo: ShipmentsRepository,
    private val catalog: CatalogRepository,
    private val reputation: pe.logyx.core.data.ReputationRepository,
) : ViewModel() {
    private val user = (auth.state.value as? SessionState.LoggedIn)?.user
    private val _state = MutableStateFlow(ShipmentDetailUiState(isCarrier = user?.organizationType == OrgType.CARRIER && user.role != Role.DRIVER))
    val state: StateFlow<ShipmentDetailUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.shipment == null, refreshing = !initial, error = null) }
        viewModelScope.launch {
            val s = async { repo.detail(shipmentId) }
            val c = async { catalog.cities() }
            s.await().fold(
                onSuccess = { sh ->
                    val cities = c.await().getOrDefault(emptyList())
                    fun geo(code: String) = cities.firstOrNull { normalizeCity(it.code) == normalizeCity(code) }?.toGeoPoint()
                    _state.update { it.copy(loading = false, refreshing = false, shipment = sh, origin = geo(sh.originCity), destination = geo(sh.destinationCity)) }
                    if (sh.status == ShipmentStatus.DELIVERED) {
                        reputation.shipmentReviews(sh.id).onSuccess { r ->
                            _state.update { it.copy(canReview = r.canReview, hasReviews = r.reviews.isNotEmpty()) }
                        }
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    fun openDriverSheet() {
        _state.update { it.copy(drivers = emptyList(), driversLoading = true) }
        viewModelScope.launch {
            repo.drivers().fold(
                onSuccess = { list -> _state.update { it.copy(drivers = list.filter { d -> d.status != DriverStatus.OFF_DUTY }, driversLoading = false) } },
                onFailure = { e -> _state.update { it.copy(drivers = null, driversLoading = false, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    fun dismissDriverSheet() = _state.update { if (it.assigning) it else it.copy(drivers = null) }
    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun assign(driver: DriverView) {
        if (_state.value.assigning) return
        _state.update { it.copy(assigning = true) }
        viewModelScope.launch {
            repo.assignDriver(shipmentId, driver.id).fold(
                onSuccess = { sh -> _state.update { it.copy(assigning = false, drivers = null, shipment = sh, message = UiMessage.success(R.string.shipments_driver_assigned)) } },
                onFailure = { e -> _state.update { it.copy(assigning = false, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }
}
