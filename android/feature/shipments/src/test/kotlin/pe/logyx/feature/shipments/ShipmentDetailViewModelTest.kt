package pe.logyx.feature.shipments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.CatalogRepository
import pe.logyx.core.data.LoadsRepository
import pe.logyx.core.data.ReputationRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.data.ShipmentsRepository
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun user(type: OrgType, org: String) =
    UserInfo("u", "a@b.pe", "Ana", Role.ADMIN, org, "Org", type, permissions = listOf(OrgPermission.READ, OrgPermission.OPERATE))

private class FakeAuth(type: OrgType, org: String) : AuthRepository {
    override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.LoggedIn(user(type, org)))
    override suspend fun login(email: String, password: String) = Result.failure<UserInfo>(IllegalStateException())
    override suspend fun logout() {}
}

private class FakeCatalog : CatalogRepository {
    override suspend fun cities() = Result.success(listOf(CityItem("LIMA", "Lima", -12.0, -77.0), CityItem("JULIACA", "Juliaca", -15.5, -70.1)))
    override suspend fun organization(id: String) = Result.failure<PublicOrganization>(IllegalStateException())
}

private class FakeReputation : ReputationRepository {
    override suspend fun reputation(organizationId: String) = Result.failure<ReputationView>(IllegalStateException())
    override suspend fun reviews(organizationId: String, page: Int) = Result.failure<Page<ReviewView>>(IllegalStateException())
    override suspend fun shipmentReviews(shipmentId: String) = Result.success(ShipmentReviewsView())
    override suspend fun submit(shipmentId: String, punctuality: Int, cargoCondition: Int, communication: Int, comment: String?) =
        Result.failure<ReviewView>(IllegalStateException())
}

private class FakeFleet(val fleet: List<VehicleView>) : LoadsRepository {
    override suspend fun marketplace() = Result.success(emptyList<RequestView>())
    override suspend fun load(id: String) = Result.failure<RequestView>(IllegalStateException())
    override suspend fun myBids() = Result.success(emptyList<RequestView>())
    override suspend fun vehicles() = Result.success(fleet)
    override suspend fun placeBid(requestId: String, vehicleId: String, amount: Double, notes: String?) = Result.failure<BidView>(IllegalStateException())
}

private class FakeShipments(var current: ShipmentView) : ShipmentsRepository {
    var cancelResult: Result<ShipmentView>? = null
    var vehicleResult: Result<ShipmentView>? = null
    var assignResult: Result<ShipmentView>? = null
    var lastCancelReason: String? = null
    var lastVehicle: Pair<String, String?>? = null
    var lastAssign: Pair<String, String?>? = null
    val drivers = listOf(
        DriverView("d1", "Luis Quispe", status = DriverStatus.AVAILABLE),
        DriverView("d2", "Rosa Mamani", status = DriverStatus.AVAILABLE),
        DriverView("d3", "Inactivo", status = DriverStatus.AVAILABLE, active = false),
        DriverView("d4", "Descanso", status = DriverStatus.OFF_DUTY),
    )
    override suspend fun list() = Result.success(listOf(current))
    override suspend fun detail(id: String) = Result.success(current)
    override suspend fun drivers() = Result.success(drivers)
    override suspend fun assignDriver(shipmentId: String, driverId: String, reason: String?): Result<ShipmentView> {
        lastAssign = driverId to reason
        return (assignResult ?: Result.success(current.copy(driverId = driverId, driverName = drivers.first { it.id == driverId }.fullName))).onSuccess { current = it }
    }
    override suspend fun cancel(shipmentId: String, reason: String): Result<ShipmentView> {
        lastCancelReason = reason
        return (cancelResult ?: Result.success(current.copy(status = ShipmentStatus.CANCELLED, cancellationReason = reason, cancelledByOrgId = "org-me"))).onSuccess { current = it }
    }
    override suspend fun changeVehicle(shipmentId: String, vehicleId: String, reason: String?): Result<ShipmentView> {
        lastVehicle = vehicleId to reason
        return (vehicleResult ?: Result.success(current.copy(vehicleId = vehicleId))).onSuccess { current = it }
    }
    override suspend fun driverShipments() = Result.success(emptyList<ShipmentView>())
    override suspend fun arrive(shipmentId: String, stopId: String) = Result.success(current)
    override suspend fun complete(shipmentId: String, stopId: String) = Result.success(current)
    var shareResult: Result<ShareLinkCreated> = Result.success(ShareLinkCreated(ShareLinkView("l1"), "tok", "https://oncargo.pe/seguimiento/tok"))
    var shareCalls = 0
    override suspend fun createShareLink(shipmentId: String, expiresInDays: Int?): Result<ShareLinkCreated> { shareCalls++; return shareResult }
}

private fun apiError(code: String, field: String? = null, message: String = "Error del servidor") = ApiException(409, code, message, field)

private fun shipment(status: ShipmentStatus = ShipmentStatus.PENDING, driverId: String? = null, incident: Boolean = false) = ShipmentView(
    "s1", originCity = "LIMA", destinationCity = "JULIACA", status = status, weightKg = 2500.0, vehicleId = "v-current", vehiclePlate = "ABC-111",
    driverId = driverId, driverName = driverId?.let { "Juan Pari" }, pickupDate = "2026-11-20", hasActiveIncident = incident,
)

private val fleet = listOf(
    VehicleView("v-current", "ABC-111", VehicleType.TRUCK_MEDIUM, 8000.0, null, VehicleStatus.AVAILABLE),
    VehicleView("v-ok", "XYZ-222", VehicleType.TRUCK_LARGE, 12000.0, null, VehicleStatus.AVAILABLE),
    VehicleView("v-small", "VAN-333", VehicleType.VAN, 1000.0, null, VehicleStatus.AVAILABLE),
    VehicleView("v-busy", "BUS-444", VehicleType.TRUCK_LARGE, 12000.0, null, VehicleStatus.IN_ROUTE),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ShipmentDetailViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: FakeShipments, type: OrgType = OrgType.CARRIER, org: String = "org-me") =
        ShipmentDetailViewModel("s1", FakeAuth(type, org), repo, FakeCatalog(), FakeReputation(), FakeRealtime(), FakeFleet(fleet))

    // ── Qué acciones se ofrecen ──

    @Test fun `por recoger sin conductor ofrece asignar, cambiar vehiculo y cancelar`() = runTest {
        val s = vm(FakeShipments(shipment())).state.value
        assertTrue(s.canAssign); assertFalse(s.canRelieveDriver); assertTrue(s.canChangeVehicle); assertTrue(s.canCancel)
        assertFalse(s.reasonRequired)
    }

    @Test fun `en ruta no se cancela y el relevo y el cambio de vehiculo exigen motivo`() = runTest {
        val s = vm(FakeShipments(shipment(ShipmentStatus.IN_TRANSIT, driverId = "d1"))).state.value
        assertFalse(s.canCancel); assertFalse(s.canAssign)
        assertTrue(s.canRelieveDriver && s.canChangeVehicle && s.reasonRequired)
    }

    @Test fun `la PYME solo ve cancelar y con incidencia activa tampoco`() = runTest {
        val pyme = vm(FakeShipments(shipment()), OrgType.PYME).state.value
        assertTrue(pyme.canCancel); assertFalse(pyme.canAssign || pyme.canChangeVehicle || pyme.canRelieveDriver)
        assertFalse(vm(FakeShipments(shipment(incident = true)), OrgType.PYME).state.value.canCancel)
    }

    // ── Cancelar ──

    @Test fun `cancelar exige motivo`() = runTest {
        val repo = FakeShipments(shipment()); val vm = vm(repo)
        vm.openCancel(); vm.confirmCancel()
        assertNotNull(vm.state.value.cancel?.reasonError)
        assertNull(repo.lastCancelReason)
    }

    @Test fun `cancelar con motivo cierra la hoja y deja el envio cancelado por mi organizacion`() = runTest {
        val repo = FakeShipments(shipment()); val vm = vm(repo)
        vm.openCancel(); vm.onCancelReason("  Se averió el camión ")
        vm.confirmCancel()
        assertEquals("Se averió el camión", repo.lastCancelReason)
        val s = vm.state.value
        assertNull(s.cancel)
        assertEquals(ShipmentStatus.CANCELLED, s.shipment?.status)
        assertEquals(Canceller.SELF, s.canceller)
        assertNotNull(s.message)
    }

    @Test fun `RN07 en el motivo de cancelacion queda bajo el campo`() = runTest {
        val repo = FakeShipments(shipment()).apply { cancelResult = Result.failure(apiError("LOGYX-NEG-001", "reason", "No incluyas teléfonos")) }
        val vm = vm(repo)
        vm.openCancel(); vm.onCancelReason("llámame al 987654321"); vm.confirmCancel()
        val c = vm.state.value.cancel
        assertEquals(UiText.Raw("No incluyas teléfonos"), c?.reasonError)
        assertFalse(c!!.submitting)
        assertNull(vm.state.value.message)
    }

    @Test fun `SHP-003 al cancelar un envio que ya salio cierra la hoja y avisa`() = runTest {
        val repo = FakeShipments(shipment()).apply { cancelResult = Result.failure(apiError("LOGYX-SHP-003", null, "Solo se cancela un envío que aún no salió del recojo")) }
        val vm = vm(repo)
        vm.openCancel(); vm.onCancelReason("x"); vm.confirmCancel()
        assertNull(vm.state.value.cancel)
        assertEquals("Solo se cancela un envío que aún no salió del recojo", vm.state.value.message?.text)
    }

    @Test fun `quien cancelo se compara con mi organizacion`() {
        val cancelled = shipment(ShipmentStatus.CANCELLED).copy(cancelledByOrgId = "org-other")
        assertEquals(Canceller.OTHER, cancelled.cancelledBy("org-me"))
        assertEquals(Canceller.SELF, cancelled.cancelledBy("org-other"))
        assertEquals(Canceller.UNKNOWN, cancelled.copy(cancelledByOrgId = null).cancelledBy("org-me"))
        assertEquals(Canceller.UNKNOWN, shipment().cancelledBy("org-me"))
    }

    // ── Cambiar vehículo ──

    @Test fun `el selector excluye el vehiculo actual y deshabilita los que no sirven`() = runTest {
        val vm = vm(FakeShipments(shipment()))
        vm.openVehicleSheet()
        val choices = vm.state.value.vehicle!!.choices
        assertEquals(listOf("v-ok", "v-small", "v-busy"), choices.map { it.vehicle.id })
        assertEquals(VehicleFit.FITS, choices.first { it.vehicle.id == "v-ok" }.fit)
        assertEquals(VehicleFit.WEIGHT, choices.first { it.vehicle.id == "v-small" }.fit)
        assertEquals(VehicleFit.UNAVAILABLE, choices.first { it.vehicle.id == "v-busy" }.fit)
    }

    @Test fun `antes del recojo el motivo es opcional`() = runTest {
        val repo = FakeShipments(shipment()); val vm = vm(repo)
        vm.openVehicleSheet(); vm.selectVehicle("v-ok"); vm.confirmVehicle()
        assertEquals("v-ok" to null, repo.lastVehicle)
        assertNull(vm.state.value.vehicle)
        assertEquals("v-ok", vm.state.value.shipment?.vehicleId)
    }

    @Test fun `en ruta el motivo es obligatorio SHP-006`() = runTest {
        val repo = FakeShipments(shipment(ShipmentStatus.IN_TRANSIT, driverId = "d1")); val vm = vm(repo)
        vm.openVehicleSheet(); vm.selectVehicle("v-ok"); vm.confirmVehicle()
        assertNotNull(vm.state.value.vehicle?.reasonError)
        assertNull(repo.lastVehicle)
        vm.onVehicleReason("Falla mecánica"); vm.confirmVehicle()
        assertEquals("v-ok" to "Falla mecánica", repo.lastVehicle)
    }

    @Test fun `no se puede elegir un vehiculo no apto`() = runTest {
        val repo = FakeShipments(shipment()); val vm = vm(repo)
        vm.openVehicleSheet(); vm.selectVehicle("v-small"); vm.confirmVehicle()
        assertNull(repo.lastVehicle)
    }

    @Test fun `CAP-003 y SHP-007 quedan bajo el selector`() = runTest {
        val repo = FakeShipments(shipment()).apply { vehicleResult = Result.failure(apiError("LOGYX-CAP-003", "vehicleId", "Ese tipo de vehículo no lleva esta carga")) }
        val vm = vm(repo)
        vm.openVehicleSheet(); vm.selectVehicle("v-ok"); vm.confirmVehicle()
        val v = vm.state.value.vehicle!!
        assertEquals(UiText.Raw("Ese tipo de vehículo no lleva esta carga"), v.vehicleError)
        assertFalse(v.submitting)
        vm.selectVehicle("v-ok")
        assertNull(vm.state.value.vehicle?.vehicleError)
    }

    @Test fun `RN07 en el motivo del cambio de vehiculo`() = runTest {
        val repo = FakeShipments(shipment(ShipmentStatus.IN_TRANSIT, "d1")).apply { vehicleResult = Result.failure(apiError("LOGYX-NEG-001", "reason", "Sin contacto")) }
        val vm = vm(repo)
        vm.openVehicleSheet(); vm.selectVehicle("v-ok"); vm.onVehicleReason("escríbeme a a@b.pe"); vm.confirmVehicle()
        assertEquals(UiText.Raw("Sin contacto"), vm.state.value.vehicle?.reasonError)
    }

    // ── Conductor ──

    @Test fun `la lista de conductores omite desactivados, en descanso y el actual`() = runTest {
        val vm = vm(FakeShipments(shipment(ShipmentStatus.IN_TRANSIT, driverId = "d1")))
        vm.openDriverSheet()
        assertEquals(listOf("d2"), vm.state.value.drivers?.map { it.id })
    }

    @Test fun `asignar el primer conductor no manda motivo`() = runTest {
        val repo = FakeShipments(shipment()); val vm = vm(repo)
        vm.openDriverSheet(); vm.pickDriver(repo.drivers[0]); vm.onDriverReason("ignorado"); vm.confirmPendingDriver()
        assertEquals("d1" to null, repo.lastAssign)
        assertNull(vm.state.value.drivers)
        assertEquals("d1", vm.state.value.shipment?.driverId)
    }

    @Test fun `relevar en ruta exige motivo y lo manda`() = runTest {
        val repo = FakeShipments(shipment(ShipmentStatus.IN_TRANSIT, driverId = "d1")); val vm = vm(repo)
        vm.openDriverSheet(); vm.pickDriver(repo.drivers[1]); vm.confirmPendingDriver()
        assertNotNull(vm.state.value.driverReasonError)
        assertNull(repo.lastAssign)
        vm.onDriverReason("Se enfermó"); vm.confirmPendingDriver()
        assertEquals("d2" to "Se enfermó", repo.lastAssign)
        assertEquals("d2", vm.state.value.shipment?.driverId)
    }

    @Test fun `SHP-004 vuelve a la lista con el error bajo los conductores`() = runTest {
        val repo = FakeShipments(shipment()).apply { assignResult = Result.failure(apiError("LOGYX-SHP-004", "driverId", "El conductor ya tiene otro envío activo")) }
        val vm = vm(repo)
        vm.openDriverSheet(); vm.pickDriver(repo.drivers[0]); vm.confirmPendingDriver()
        val s = vm.state.value
        assertNull(s.pendingDriver)
        assertNotNull(s.drivers)
        assertEquals(UiText.Raw("El conductor ya tiene otro envío activo"), s.driverError)
        assertFalse(s.assigning)
    }

    @Test fun `FLT-003 conductor desactivado tambien queda inline`() = runTest {
        val repo = FakeShipments(shipment()).apply { assignResult = Result.failure(apiError("LOGYX-FLT-003", "driverId", "Ese conductor está desactivado")) }
        val vm = vm(repo)
        vm.openDriverSheet(); vm.pickDriver(repo.drivers[1]); vm.confirmPendingDriver()
        assertEquals(UiText.Raw("Ese conductor está desactivado"), vm.state.value.driverError)
        vm.pickDriver(repo.drivers[0])
        assertNull(vm.state.value.driverError)
    }

    @Test fun `RN07 en el motivo del relevo queda bajo el campo y conserva el conductor elegido`() = runTest {
        val repo = FakeShipments(shipment(ShipmentStatus.IN_TRANSIT, "d1")).apply { assignResult = Result.failure(apiError("LOGYX-NEG-001", "reason", "Sin contacto")) }
        val vm = vm(repo)
        vm.openDriverSheet(); vm.pickDriver(repo.drivers[1]); vm.onDriverReason("987654321"); vm.confirmPendingDriver()
        val s = vm.state.value
        assertEquals(UiText.Raw("Sin contacto"), s.driverReasonError)
        assertNotNull(s.pendingDriver)
    }

    @Test fun `motivos mayores a 500 se recortan`() = runTest {
        val vm = vm(FakeShipments(shipment()))
        vm.openCancel(); vm.onCancelReason("a".repeat(600))
        assertEquals(500, vm.state.value.cancel?.reason?.length)
    }

    // ── Compartir seguimiento (PYME) ──

    @Test fun `la PYME comparte el seguimiento y la url queda lista una sola vez`() = runTest {
        val repo = FakeShipments(shipment().copy(code = "OC-7K2QX"))
        val vm = vm(repo, OrgType.PYME)
        assertTrue(vm.state.value.canShare)
        vm.share()
        assertEquals("https://oncargo.pe/seguimiento/tok", vm.state.value.shareUrl)
        assertFalse(vm.state.value.sharing)
        vm.onShareHandled()
        assertNull(vm.state.value.shareUrl)
        assertEquals(1, repo.shareCalls)
    }

    @Test fun `el transportista y los envios cancelados no comparten`() = runTest {
        assertFalse(vm(FakeShipments(shipment())).state.value.canShare)
        val repo = FakeShipments(shipment(ShipmentStatus.CANCELLED))
        val cancelled = vm(repo, OrgType.PYME)
        assertFalse(cancelled.state.value.canShare)
        cancelled.share()
        assertEquals(0, repo.shareCalls)
    }

    @Test fun `SHL-004 se muestra con el mensaje del servidor`() = runTest {
        val repo = FakeShipments(shipment()).apply { shareResult = Result.failure(apiError("LOGYX-SHL-004", message = "Ya tienes 10 enlaces activos")) }
        val vm = vm(repo, OrgType.PYME)
        vm.share()
        assertNull(vm.state.value.shareUrl)
        assertEquals("Ya tienes 10 enlaces activos", vm.state.value.message?.text)
        assertFalse(vm.state.value.sharing)
    }
}
