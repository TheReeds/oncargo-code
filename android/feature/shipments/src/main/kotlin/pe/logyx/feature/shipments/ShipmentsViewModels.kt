package pe.logyx.feature.shipments

import pe.logyx.core.data.RoutesRepository
import pe.logyx.core.data.between
import pe.logyx.core.model.RouteGeometry
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
import pe.logyx.core.data.LoadsRepository
import pe.logyx.core.data.fieldErrors
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.data.refreshSignals
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.designsystem.components.VehicleChoice
import pe.logyx.core.model.DriverStatus
import pe.logyx.core.model.DriverView
import pe.logyx.core.model.GeoPoint
import pe.logyx.core.model.OrgType
import pe.logyx.core.model.RealtimeTypes
import pe.logyx.core.model.Role
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.ShipmentView
import pe.logyx.core.model.VehicleType
import pe.logyx.core.model.VehicleView
import pe.logyx.core.model.fitFor
import pe.logyx.core.model.isActive
import pe.logyx.core.model.isCancellable
import pe.logyx.core.model.isReassignable
import pe.logyx.core.model.normalizeCity
import pe.logyx.core.model.toGeoPoint

data class ShipmentsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<ShipmentView> = emptyList(),
    val isCarrier: Boolean = false,
)

class ShipmentsViewModel(auth: AuthRepository, private val repo: ShipmentsRepository, private val realtime: RealtimeRepository) : ViewModel() {
    private val user = (auth.state.value as? SessionState.LoggedIn)?.user
    private val _state = MutableStateFlow(ShipmentsUiState(isCarrier = user?.organizationType == OrgType.CARRIER && user.role != Role.DRIVER))
    val state: StateFlow<ShipmentsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    /** Tiempo real (solo en primer plano, lo lanza la UI): cualquier cambio de envío/incidencia refresca en silencio. */
    suspend fun liveLoop() {
        realtime.refreshSignals(RealtimeTypes.SHIPMENT).collect { load(initial = false, silent = true) }
    }

    private fun load(initial: Boolean, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = initial && it.items.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            repo.list().fold(
                onSuccess = { list ->
                    // Activos primero; luego por fecha descendente.
                    val sorted = list.sortedWith(compareByDescending<ShipmentView> { it.isActive }.thenByDescending { it.createdAt.orEmpty() })
                    _state.update { it.copy(loading = false, refreshing = false, items = sorted) }
                },
                onFailure = { e -> if (!silent) _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}

/** Quién canceló el envío, visto desde la organización del usuario (`cancelledByOrgId`). */
enum class Canceller { SELF, OTHER, UNKNOWN }

fun ShipmentView.cancelledBy(userOrgId: String?): Canceller = when {
    status != ShipmentStatus.CANCELLED || cancelledByOrgId == null -> Canceller.UNKNOWN
    cancelledByOrgId == userOrgId -> Canceller.SELF
    else -> Canceller.OTHER
}

/** Hoja "Cancelar envío": motivo obligatorio (máx. 500, RN07 en `reason`). */
data class CancelSheetState(
    val reason: String = "",
    val reasonError: UiText? = null,
    val submitting: Boolean = false,
)

/** Hoja "Cambiar vehículo" (transportista): flota con RN02 y motivo (obligatorio en ruta, `SHP-006`). */
data class VehicleSheetState(
    val loading: Boolean = true,
    /** Flota con el veredicto de RN02 (sin el vehículo actual del envío). */
    val choices: List<VehicleChoice> = emptyList(),
    val selectedId: String? = null,
    val reason: String = "",
    val reasonError: UiText? = null,
    /** Errores `CAP-001..005` y `SHP-007` (field `vehicleId`) bajo el selector. */
    val vehicleError: UiText? = null,
    val submitting: Boolean = false,
)

data class ShipmentDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val shipment: ShipmentView? = null,
    val origin: GeoPoint? = null,
    val destination: GeoPoint? = null,
    /** Ruta por carretera para el mapa (ADR-0008); null = cargando o sin red (el mapa dibuja la recta). */
    val route: RouteGeometry? = null,
    val isCarrier: Boolean = false,
    /** Organización del usuario: sirve para decir "Cancelaste tú" / "Lo canceló la otra parte". */
    val userOrgId: String? = null,
    /** Lista de conductores del bottom sheet; null = sheet cerrado. */
    val drivers: List<DriverView>? = null,
    val driversLoading: Boolean = false,
    val assigning: Boolean = false,
    /** Conductor elegido que espera confirmación (asignar) o motivo (relevar). */
    val pendingDriver: DriverView? = null,
    val driverReason: String = "",
    val driverReasonError: UiText? = null,
    /** `SHP-004` (conductor con otro envío) y `FLT-003` (conductor desactivado): field `driverId`, bajo la lista. */
    val driverError: UiText? = null,
    val cancel: CancelSheetState? = null,
    val vehicle: VehicleSheetState? = null,
    val message: UiMessage? = null,
    /** Calificación (RN06): null = aún no se sabe / no aplica. */
    val canReview: Boolean? = null,
    val hasReviews: Boolean = false,
    /** Usuario de una PYME: puede compartir el seguimiento con su cliente final (si además puede operar, ver `Can`). */
    val isPyme: Boolean = false,
    /** Creando el enlace público de seguimiento. */
    val sharing: Boolean = false,
    /** URL recién creada: la pantalla abre la hoja de compartir una vez y llama a `onShareHandled()`. */
    val shareUrl: String? = null,
) {
    private val status: ShipmentStatus? get() = shipment?.status

    /** Asignar el primer conductor (envío por recoger y sin conductor). */
    val canAssign: Boolean get() = isCarrier && status == ShipmentStatus.PENDING && shipment?.driverId == null

    /** Relevar al conductor: ya hay uno y el envío está por recoger o en ruta. */
    val canRelieveDriver: Boolean get() = isCarrier && shipment?.let { it.isReassignable && it.driverId != null } == true

    val canChangeVehicle: Boolean get() = isCarrier && shipment?.isReassignable == true

    /** Las dos partes cancelan solo antes del recojo y sin incidencia activa (`SHP-003`, `TRK-005`). */
    val canCancel: Boolean get() = shipment?.isCancellable == true

    /** En ruta el motivo del relevo y del cambio de vehículo es obligatorio (`SHP-006`). */
    val reasonRequired: Boolean get() = status == ShipmentStatus.IN_TRANSIT

    val canceller: Canceller get() = shipment?.cancelledBy(userOrgId) ?: Canceller.UNKNOWN

    /**
     * «Compartir seguimiento» (PYME ADMIN/OPS; la pantalla además lo envuelve en `Can(OPERATE)`): no para envíos cancelados;
     * los entregados hace más de 7 días los rechaza el servidor (`SHL-003`).
     */
    val canShare: Boolean get() = isPyme && shipment != null && status != ShipmentStatus.CANCELLED
}

class ShipmentDetailViewModel(
    private val shipmentId: String,
    auth: AuthRepository,
    private val repo: ShipmentsRepository,
    private val catalog: CatalogRepository,
    private val reputation: pe.logyx.core.data.ReputationRepository,
    private val realtime: RealtimeRepository,
    private val fleet: LoadsRepository,
    private val routes: RoutesRepository = RoutesRepository.NONE,
) : ViewModel() {
    private val user = (auth.state.value as? SessionState.LoggedIn)?.user
    private val _state = MutableStateFlow(
        ShipmentDetailUiState(
            isCarrier = user?.organizationType == OrgType.CARRIER && user.role != Role.DRIVER, userOrgId = user?.organizationId,
            isPyme = user?.organizationType == OrgType.PYME,
        ),
    )
    val state: StateFlow<ShipmentDetailUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    /**
     * Tiempo real (solo en primer plano, lo lanza la UI): `shipment.*` e `incident.*` de ESTE envío refrescan en
     * silencio (sin spinner ni error si ya hay datos); respaldo solo si el stream no está en vivo.
     */
    suspend fun liveLoop() {
        realtime.refreshSignals(RealtimeTypes.SHIPMENT) { it.string("shipmentId") == shipmentId }
            .collect { load(initial = false, silent = true) }
    }

    private fun load(initial: Boolean, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = initial && it.shipment == null, refreshing = !initial, error = null) }
        viewModelScope.launch {
            val s = async { repo.detail(shipmentId) }
            val c = async { catalog.cities() }
            s.await().fold(
                onSuccess = { sh ->
                    val cities = c.await().getOrDefault(emptyList())
                    fun geo(code: String) = cities.firstOrNull { normalizeCity(it.code) == normalizeCity(code) }?.toGeoPoint()
                    _state.update { it.copy(loading = false, refreshing = false, shipment = sh, origin = geo(sh.originCity), destination = geo(sh.destinationCity)) }
                    routes.between(sh.originCity, sh.destinationCity)?.let { g -> _state.update { it.copy(route = g) } }
                    if (sh.status == ShipmentStatus.DELIVERED) {
                        reputation.shipmentReviews(sh.id).onSuccess { r ->
                            _state.update { it.copy(canReview = r.canReview, hasReviews = r.reviews.isNotEmpty()) }
                        }
                    }
                },
                onFailure = { e -> if (!silent) _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    // ── Compartir seguimiento con el cliente final (roadmap 1.3/1.4) ──

    /** Crea un enlace público (`POST /shipments/{id}/share-links`) y deja la URL lista para la hoja de compartir. */
    fun share() {
        val s = _state.value
        if (s.sharing || !s.canShare) return
        _state.update { it.copy(sharing = true) }
        viewModelScope.launch {
            repo.createShareLink(shipmentId).fold(
                onSuccess = { created -> _state.update { it.copy(sharing = false, shareUrl = created.url) } },
                // SHL-002 (cancelado), SHL-003 (entregado hace más de 7 días), SHL-004 (10 enlaces activos), AUTH-007 (Lector).
                onFailure = { e -> _state.update { it.copy(sharing = false, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    fun onShareHandled() = _state.update { it.copy(shareUrl = null) }

    // ── Conductor: asignar (sin conductor) o relevar (con motivo, obligatorio en ruta) ──

    fun openDriverSheet() {
        _state.update { it.copy(drivers = emptyList(), driversLoading = true, driverError = null) }
        viewModelScope.launch {
            repo.drivers().fold(
                // Un conductor desactivado no se puede asignar (`FLT-003`); el actual tampoco tiene sentido en un relevo.
                onSuccess = { list ->
                    val current = _state.value.shipment?.driverId
                    _state.update { it.copy(drivers = list.filter { d -> d.status != DriverStatus.OFF_DUTY && d.active && d.id != current }, driversLoading = false) }
                },
                onFailure = { e -> _state.update { it.copy(drivers = null, driversLoading = false, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    fun dismissDriverSheet() = _state.update {
        if (it.assigning) it else it.copy(drivers = null, pendingDriver = null, driverReason = "", driverReasonError = null, driverError = null)
    }

    /** Elige un conductor: el diálogo (asignar) o la hoja de motivo (relevar) lo confirma. */
    fun pickDriver(driver: DriverView) = _state.update { it.copy(pendingDriver = driver, driverError = null, driverReasonError = null) }

    fun cancelPick() = _state.update { if (it.assigning) it else it.copy(pendingDriver = null, driverReason = "", driverReasonError = null) }

    fun onDriverReason(text: String) = _state.update { it.copy(driverReason = text.take(MAX_REASON), driverReasonError = null) }

    /** Asigna o releva (`PUT /shipments/{id}/driver`); con el envío en ruta el motivo es obligatorio. */
    fun confirmPendingDriver() { _state.value.pendingDriver?.let(::assign) }

    fun assign(driver: DriverView) {
        val s = _state.value
        if (s.assigning) return
        val relieving = s.shipment?.driverId != null
        val reason = s.driverReason.trim()
        if (relieving && s.reasonRequired && reason.isEmpty()) {
            _state.update { it.copy(driverReasonError = REASON_REQUIRED) }
            return
        }
        _state.update { it.copy(assigning = true, driverReasonError = null, driverError = null) }
        viewModelScope.launch {
            repo.assignDriver(shipmentId, driver.id, if (relieving) reason.ifEmpty { null } else null).fold(
                onSuccess = { sh ->
                    _state.update {
                        it.copy(
                            assigning = false, drivers = null, pendingDriver = null, driverReason = "", shipment = sh,
                            message = UiMessage.success(if (relieving) R.string.shipments_driver_relieved else R.string.shipments_driver_assigned),
                        )
                    }
                },
                onFailure = { e ->
                    val fields = e.fieldErrors("driverId", "reason")
                    _state.update {
                        when {
                            fields["reason"] != null -> it.copy(assigning = false, driverReasonError = UiText.Raw(fields["reason"]!!))
                            // El conductor no sirve (otro envío, desactivado): vuelve a la lista con el error bajo ella.
                            fields["driverId"] != null -> it.copy(assigning = false, pendingDriver = null, driverError = UiText.Raw(fields["driverId"]!!))
                            else -> it.copy(assigning = false, pendingDriver = null, message = UiMessage.error(e.userMessage()))
                        }
                    }
                },
            )
        }
    }

    // ── Cancelar el envío ──

    fun openCancel() = _state.update { if (it.canCancel) it.copy(cancel = CancelSheetState()) else it }
    fun dismissCancel() = _state.update { if (it.cancel?.submitting == true) it else it.copy(cancel = null) }
    fun onCancelReason(text: String) = _state.update { s -> s.cancel?.let { s.copy(cancel = it.copy(reason = text.take(MAX_REASON), reasonError = null)) } ?: s }

    fun confirmCancel() {
        val form = _state.value.cancel ?: return
        if (form.submitting) return
        val reason = form.reason.trim()
        if (reason.isEmpty()) {
            _state.update { it.copy(cancel = form.copy(reasonError = REASON_REQUIRED)) }
            return
        }
        _state.update { it.copy(cancel = form.copy(submitting = true, reasonError = null)) }
        viewModelScope.launch {
            repo.cancel(shipmentId, reason).fold(
                onSuccess = { sh ->
                    _state.update { it.copy(cancel = null, shipment = sh, message = UiMessage.success(R.string.shipments_cancelled)) }
                    load(initial = false, silent = true)
                },
                onFailure = { e ->
                    val fields = e.fieldErrors("reason")
                    if (fields["reason"] != null) {
                        _state.update { it.copy(cancel = form.copy(submitting = false, reasonError = UiText.Raw(fields["reason"]!!))) }
                    } else {
                        // SHP-003 (ya salió) / TRK-005 (incidencia activa): el estado cambió; se explica y se refresca.
                        _state.update { it.copy(cancel = null, message = UiMessage.error(e.userMessage())) }
                        load(initial = false, silent = true)
                    }
                },
            )
        }
    }

    // ── Cambiar vehículo (transportista) ──

    fun openVehicleSheet() {
        if (!_state.value.canChangeVehicle) return
        _state.update { it.copy(vehicle = VehicleSheetState()) }
        viewModelScope.launch {
            fleet.vehicles().fold(
                onSuccess = { list -> _state.update { s -> s.vehicle?.let { s.copy(vehicle = it.copy(loading = false, choices = choicesFor(s, list))) } ?: s } },
                onFailure = { e -> _state.update { it.copy(vehicle = null, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    fun dismissVehicleSheet() = _state.update { if (it.vehicle?.submitting == true) it else it.copy(vehicle = null) }

    private fun updateVehicle(f: (VehicleSheetState) -> VehicleSheetState) = _state.update { s -> s.vehicle?.let { s.copy(vehicle = f(it)) } ?: s }

    fun selectVehicle(id: String) = updateVehicle { it.copy(selectedId = id, vehicleError = null) }
    fun onVehicleReason(text: String) = updateVehicle { it.copy(reason = text.take(MAX_REASON), reasonError = null) }

    /**
     * Opciones del selector: la flota **sin el vehículo actual**, con el veredicto de RN02 que se puede saber en el móvil
     * (disponibilidad y kg; el envío no trae tipo de carga ni volumen: `CAP-003/004` los dice el servidor bajo el selector).
     */
    private fun choicesFor(s: ShipmentDetailUiState, fleet: List<VehicleView>): List<VehicleChoice> {
        val weight = s.shipment?.weightKg ?: 0.0
        val current = s.shipment?.vehicleId
        return fleet.filter { it.id != current }.map { VehicleChoice(it, it.fitFor(weight, null, VehicleType.entries)) }
    }

    fun confirmVehicle() {
        val s = _state.value
        val form = s.vehicle ?: return
        if (form.submitting) return
        val vehicleId = form.selectedId?.takeIf { id -> form.choices.any { it.vehicle.id == id && it.fit.isFit } } ?: return
        val reason = form.reason.trim()
        if (s.reasonRequired && reason.isEmpty()) {
            updateVehicle { it.copy(reasonError = REASON_REQUIRED) }
            return
        }
        updateVehicle { it.copy(submitting = true, reasonError = null, vehicleError = null) }
        viewModelScope.launch {
            repo.changeVehicle(shipmentId, vehicleId, reason.ifEmpty { null }).fold(
                onSuccess = { sh ->
                    _state.update { it.copy(vehicle = null, shipment = sh, message = UiMessage.success(R.string.shipments_vehicle_changed)) }
                },
                onFailure = { e ->
                    val fields = e.fieldErrors("vehicleId", "reason")
                    when {
                        fields["reason"] != null -> updateVehicle { it.copy(submitting = false, reasonError = UiText.Raw(fields["reason"]!!)) }
                        fields["vehicleId"] != null -> updateVehicle { it.copy(submitting = false, vehicleError = UiText.Raw(fields["vehicleId"]!!)) }
                        else -> _state.update { it.copy(vehicle = null, message = UiMessage.error(e.userMessage())) }
                    }
                },
            )
        }
    }
}

/** Largo máximo del motivo (`@Size(max = 500)` del backend). */
const val MAX_REASON = 500

private val REASON_REQUIRED get() = UiText.of(R.string.shipments_reason_required)
