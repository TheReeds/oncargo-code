package pe.logyx.feature.loads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Detalle de carga del transportista: contraoferta, retiro, vehículos RN02 y errores de campo (A3/A4/A5). */
@OptIn(ExperimentalCoroutinesApi::class)
class MyBidNegotiationTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun myBid(status: BidStatus = BidStatus.PENDING) = BidView("b1", "1", amount = 1500.0, status = status, createdAt = "2026-11-10T10:00:00-05:00")

    private fun request(
        bid: BidView? = myBid(),
        status: RequestStatus = RequestStatus.PUBLISHED,
        auctionStatus: AuctionStatus = AuctionStatus.OPEN,
        cargo: CargoType = CargoType.GENERAL,
        standing: MyBidStanding? = null,
    ) = req("1", "LIMA", 3000.0, 1800.0, floor = 1000.0, myBid = bid).copy(
        status = status, cargoType = cargo, myBidStanding = standing, volumeM3 = 10.0,
        auction = AuctionView("a1", auctionStatus, "2099-01-01T00:00:00Z", 5, 2, closeReason = if (auctionStatus == AuctionStatus.OPEN) null else CloseReason.TIMEOUT, closedAt = "2026-11-10T12:00:00-05:00"),
    )

    private fun vm(req: RequestView, nego: FakeNegotiation = FakeNegotiation(), loads: FakeLoads = FakeLoads(Result.success(listOf(req)), fleet = listOf(truck("1", 5000.0)))) =
        LoadDetailViewModel("1", loads, FakeCatalog(), nego, FakeRealtime()) to (nego to loads)

    private fun pymeCounter(status: CounterOfferStatus = CounterOfferStatus.PENDING) =
        CounterOfferView("co1", "b1", amount = 1300.0, status = status, proposedByCarrier = false, createdAt = "2026-11-10T11:00:00-05:00")

    @Test fun `carga las contraofertas de mi oferta pendiente y separa la de la PYME de la mia`() = runTest {
        val mine = CounterOfferView("co2", "b1", amount = 1450.0, status = CounterOfferStatus.PENDING, proposedByCarrier = true, createdAt = "2026-11-10T12:00:00-05:00")
        val (vm, _) = vm(request(), FakeNegotiation(mapOf("b1" to listOf(pymeCounter(CounterOfferStatus.EXPIRED), mine))))
        assertEquals("co2", vm.state.value.myPendingCounter?.id)
        assertNull(vm.state.value.counterFromPyme)
        assertEquals(2, vm.state.value.counters.size)
        val (vm2, _) = vm(request(), FakeNegotiation(mapOf("b1" to listOf(pymeCounter()))))
        assertEquals("co1", vm2.state.value.counterFromPyme?.id)
    }

    @Test fun `oferta no pendiente no consulta contraofertas`() = runTest {
        val nego = FakeNegotiation()
        val (vm, _) = vm(request(bid = myBid(BidStatus.WITHDRAWN)), nego)
        assertTrue(nego.queried.isEmpty())
        assertTrue(vm.state.value.counters.isEmpty())
        assertFalse(vm.state.value.canCounter)
        assertFalse(vm.state.value.canWithdraw)
        assertEquals(BidOutcome.WITHDRAWN, vm.state.value.request?.myBidOutcome)
    }

    @Test fun `aceptar la contraoferta de la PYME pide confirmacion y responde`() = runTest {
        val nego = FakeNegotiation(mapOf("b1" to listOf(pymeCounter())))
        val (vm, _) = vm(request(), nego)
        vm.askAcceptCounter(vm.state.value.counterFromPyme!!)
        assertTrue(vm.state.value.counterConfirm?.accept == true)
        assertNull(nego.accepted)
        vm.confirmCounterAnswer()
        assertEquals("co1", nego.accepted)
        assertNull(vm.state.value.counterConfirm)
        assertEquals(R.string.loads_counter_accepted, vm.state.value.message?.textRes)
        assertFalse(vm.state.value.busy)
    }

    @Test fun `rechazar la contraoferta llama a reject`() = runTest {
        val nego = FakeNegotiation(mapOf("b1" to listOf(pymeCounter())))
        val (vm, _) = vm(request(), nego)
        vm.askRejectCounter(vm.state.value.counterFromPyme!!)
        vm.confirmCounterAnswer()
        assertEquals("co1", nego.rejected)
        assertEquals(R.string.loads_counter_rejected, vm.state.value.message?.textRes)
    }

    @Test fun `contraofertar valida piso y monto distinto, confirma y envia`() = runTest {
        val nego = FakeNegotiation()
        val (vm, _) = vm(request(), nego)
        vm.openCounterSheet()
        assertEquals("1500", vm.state.value.counterForm?.amount, "parte del monto de mi oferta")
        vm.askConfirmCounterSend()
        assertTrue((vm.state.value.counterForm?.amountError as UiText.Res).id == R.string.loads_counter_same_amount)
        vm.onCounterAmount("900")
        vm.askConfirmCounterSend()
        assertTrue((vm.state.value.counterForm?.amountError as UiText.Res).id == R.string.loads_error_amount_below_floor)
        vm.onCounterAmount("1400")
        vm.onCounterMessage("  Salgo el lunes  ")
        vm.askConfirmCounterSend()
        assertTrue(vm.state.value.counterForm?.confirming == true)
        assertNull(nego.proposed)
        vm.submitCounter()
        assertEquals(Triple("b1", 1400.0, "Salgo el lunes"), nego.proposed)
        assertNull(vm.state.value.counterForm)
        assertEquals(R.string.loads_counter_sent, vm.state.value.message?.textRes)
    }

    @Test fun `RN07 en el mensaje de la contraoferta queda bajo el campo mensaje`() = runTest {
        val nego = FakeNegotiation().apply {
            proposeResult = Result.failure(ApiException(400, "LOGYX-NEG-001", "No compartas datos de contacto", "message"))
        }
        val (vm, _) = vm(request(), nego)
        vm.openCounterSheet(); vm.onCounterAmount("1400"); vm.onCounterMessage("llámame al 987654321")
        vm.askConfirmCounterSend(); vm.submitCounter()
        val form = vm.state.value.counterForm
        assertEquals(UiText.Raw("No compartas datos de contacto"), form?.messageError)
        assertFalse(form?.sending == true)
        assertFalse(form?.confirming == true)
        assertNull(vm.state.value.message)
        vm.onCounterMessage("Salgo el lunes")
        assertNull(vm.state.value.counterForm?.messageError, "editar el campo limpia el error")
    }

    @Test fun `NEG-004 ya hay una pendiente se muestra como mensaje y el monto invalido bajo el monto`() = runTest {
        val nego = FakeNegotiation().apply { proposeResult = Result.failure(ApiException(409, "LOGYX-NEG-004", "Ya hay una contra-oferta tuya pendiente para esta oferta")) }
        val (vm, _) = vm(request(), nego)
        vm.openCounterSheet(); vm.onCounterAmount("1400"); vm.askConfirmCounterSend(); vm.submitCounter()
        assertEquals("Ya hay una contra-oferta tuya pendiente para esta oferta", vm.state.value.message?.text)
        assertNotNull(vm.state.value.counterForm, "la hoja sigue abierta")
        assertNull(vm.state.value.counterForm?.messageError)

        nego.proposeResult = Result.failure(ApiException(400, "LOGYX-NEG-002", "Menor al piso", "amount"))
        vm.askConfirmCounterSend(); vm.submitCounter()
        assertEquals(UiText.Raw("Menor al piso"), vm.state.value.counterForm?.amountError)
    }

    @Test fun `el mensaje de la contraoferta se corta en 500`() = runTest {
        val (vm, _) = vm(request())
        vm.openCounterSheet()
        vm.onCounterMessage("a".repeat(600))
        assertEquals(500, vm.state.value.counterForm?.message?.length)
    }

    @Test fun `retirar oferta pide confirmacion y la retira`() = runTest {
        val (vm, parts) = vm(request())
        val loads = parts.second
        assertTrue(vm.state.value.canWithdraw)
        vm.askWithdraw()
        assertTrue(vm.state.value.confirmWithdraw)
        assertTrue(loads.withdrawn.isEmpty())
        vm.confirmWithdraw()
        assertEquals(listOf("b1"), loads.withdrawn)
        assertFalse(vm.state.value.confirmWithdraw)
        assertEquals(R.string.loads_withdrawn, vm.state.value.message?.textRes)
    }

    @Test fun `BID-005 al retirar muestra el mensaje del servidor`() = runTest {
        val (vm, parts) = vm(request())
        parts.second.withdrawResult = Result.failure(ApiException(409, "LOGYX-BID-005", "La solicitud ya no admite cambios en las ofertas"))
        vm.askWithdraw(); vm.confirmWithdraw()
        assertEquals("La solicitud ya no admite cambios en las ofertas", vm.state.value.message?.text)
        assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.confirmWithdraw)
    }

    @Test fun `con la subasta cerrada se puede contraofertar pero no retirar`() = runTest {
        val (vm, _) = vm(request(auctionStatus = AuctionStatus.CLOSED))
        assertTrue(vm.state.value.canCounter)
        assertFalse(vm.state.value.canWithdraw)
        assertEquals(BidOutcome.AWAITING_DECISION, vm.state.value.request?.myBidOutcome)
    }

    // ── RN02: selector de vehículo ──

    private fun fleetLoads(req: RequestView, fleet: List<VehicleView>) = FakeLoads(Result.success(listOf(req)), fleet = fleet)

    @Test fun `el selector lista toda la flota con su veredicto y preselecciona el unico apto`() = runTest {
        val req = request(bid = null, cargo = CargoType.PERISHABLE)
        val reefer = VehicleView("r", "REF-1", VehicleType.REFRIGERATED, 8000.0, 30.0, VehicleStatus.AVAILABLE)
        val small = VehicleView("s", "SML-1", VehicleType.TRUCK_SMALL, 8000.0, 30.0, VehicleStatus.AVAILABLE)
        val busy = VehicleView("b", "BSY-1", VehicleType.REFRIGERATED, 8000.0, 30.0, VehicleStatus.IN_ROUTE)
        val (vm, _) = vm(req, loads = fleetLoads(req, listOf(small, reefer, busy)))
        assertEquals(3, vm.state.value.vehicleChoices.size)
        assertEquals(VehicleFit.TYPE, vm.state.value.vehicleChoices.first { it.vehicle.id == "s" }.fit)
        assertEquals(VehicleFit.UNAVAILABLE, vm.state.value.vehicleChoices.first { it.vehicle.id == "b" }.fit)
        assertEquals(listOf("r"), vm.state.value.compatibleVehicles.map { it.id })
        vm.openBidSheet()
        assertEquals("r", vm.state.value.form?.vehicleId)
        vm.selectVehicle("s")
        assertEquals("r", vm.state.value.form?.vehicleId, "un vehículo no apto no se puede elegir")
    }

    @Test fun `con varios vehiculos aptos hay que elegir y sin elegir marca el error`() = runTest {
        val req = request(bid = null)
        val (vm, _) = vm(req, loads = fleetLoads(req, listOf(truck("1", 5000.0), truck("2", 9000.0))))
        vm.openBidSheet()
        assertNull(vm.state.value.form?.vehicleId)
        vm.onAmountChange("1500")
        vm.requestConfirm()
        assertTrue((vm.state.value.form?.vehicleError as UiText.Res).id == R.string.loads_error_vehicle_required)
        vm.selectVehicle("2")
        assertNull(vm.state.value.form?.vehicleError)
        assertEquals("2", vm.state.value.form?.vehicleId)
    }

    @Test fun `sin vehiculos aptos el vehiculo no se preselecciona y el envio queda bloqueado`() = runTest {
        val req = request(bid = null, cargo = CargoType.OVERSIZED)
        val (vm, _) = vm(req, loads = fleetLoads(req, listOf(truck("1", 5000.0))))
        vm.openBidSheet()
        assertTrue(vm.state.value.compatibleVehicles.isEmpty())
        assertNull(vm.state.value.form?.vehicleId)
        assertEquals(VehicleFit.TYPE, vm.state.value.vehicleChoices.single().fit)
    }

    @Test fun `CAP-003 llega bajo el selector de vehiculo y CAP-005 sin field tambien`() = runTest {
        val req = request(bid = null)
        val loads = fleetLoads(req, listOf(truck("1", 5000.0)))
        val (vm, _) = vm(req, loads = loads)
        vm.openBidSheet(); vm.onAmountChange("1500")
        loads.placeResult = Result.failure(ApiException(400, "LOGYX-CAP-003", "El tipo de vehículo no sirve para esta carga", "vehicleId"))
        vm.requestConfirm(); vm.submitBid()
        assertEquals(UiText.Raw("El tipo de vehículo no sirve para esta carga"), vm.state.value.form?.vehicleError)
        assertNull(vm.state.value.form?.error)
        loads.placeResult = Result.failure(ApiException(409, "LOGYX-CAP-005", "El vehículo ya tiene otro envío en esas fechas"))
        vm.requestConfirm(); vm.submitBid()
        assertEquals(UiText.Raw("El vehículo ya tiene otro envío en esas fechas"), vm.state.value.form?.vehicleError)
    }

    @Test fun `RN07 en la nota de la oferta queda bajo el campo notas`() = runTest {
        val req = request(bid = null)
        val loads = fleetLoads(req, listOf(truck("1", 5000.0)))
        val (vm, _) = vm(req, loads = loads)
        vm.openBidSheet(); vm.onAmountChange("1500"); vm.onNotesChange("escríbeme a ana@correo.com")
        loads.placeResult = Result.failure(ApiException(400, "LOGYX-NEG-001", "No compartas datos de contacto", "notes"))
        vm.requestConfirm(); vm.submitBid()
        assertEquals(UiText.Raw("No compartas datos de contacto"), vm.state.value.form?.notesError)
        assertNull(vm.state.value.form?.error)
        vm.onNotesChange("Salgo mañana")
        assertNull(vm.state.value.form?.notesError)
    }

    @Test fun `la nota de la oferta se corta en 300`() = runTest {
        val req = request(bid = null)
        val (vm, _) = vm(req, loads = fleetLoads(req, listOf(truck("1", 5000.0))))
        vm.openBidSheet()
        vm.onNotesChange("x".repeat(400))
        assertEquals(300, vm.state.value.form?.notes?.length)
    }

    @Test fun `un error de acceso del rol Lector se muestra con el mensaje del backend`() = runTest {
        val req = request(bid = null)
        val loads = fleetLoads(req, listOf(truck("1", 5000.0)))
        val (vm, _) = vm(req, loads = loads)
        vm.openBidSheet(); vm.onAmountChange("1500")
        loads.placeResult = Result.failure(ApiException(403, "LOGYX-AUTH-007", "Tu rol solo permite consultar"))
        vm.requestConfirm(); vm.submitBid()
        assertEquals("Tu rol solo permite consultar", vm.state.value.form?.error)
    }
}
