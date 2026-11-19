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
import pe.logyx.core.data.BidPreferences
import pe.logyx.core.data.BidRecall
import pe.logyx.core.model.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryPrefs(var saved: BidRecall = BidRecall()) : BidPreferences {
    override suspend fun recall() = saved
    override suspend fun remember(recall: BidRecall) { saved = recall }
}

private fun truckOf(id: String, kg: Double, status: VehicleStatus = VehicleStatus.AVAILABLE) =
    VehicleView(id, "ABC-$id", VehicleType.TRUCK_MEDIUM, kg, 20.0, status)

private fun eligibility(vararg v: VehicleEligibility) = Result.success(FleetEligibility("2026-12-01", "2026-12-04", 3, v.toList()))

/** Oferta rápida (roadmap 1.10) y RN02 por fecha en la hoja de oferta de Android. */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickBidTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val load = req("1", "LIMA", 3000.0, 2000.0, floor = 1500.0)

    private fun vm(repo: FakeLoads, prefs: BidPreferences = MemoryPrefs()) =
        LoadDetailViewModel("1", repo, FakeCatalog(), FakeNegotiation(), FakeRealtime(), prefs = prefs)

    @Test fun `sin historial se preselecciona el apto de menor capacidad`() = runTest {
        val repo = FakeLoads(Result.success(listOf(load)), fleet = listOf(truckOf("big", 20000.0), truckOf("small", 6000.0)))
        val vm = vm(repo)
        vm.openBidSheet()
        assertEquals("small", vm.state.value.form?.vehicleId)
    }

    @Test fun `el ultimo vehiculo usado se preselecciona junto con su nota y el atajo`() = runTest {
        val prefs = MemoryPrefs(BidRecall("big", "Salida a primera hora", BidShortcut.MINUS_5))
        val repo = FakeLoads(Result.success(listOf(load)), fleet = listOf(truckOf("big", 20000.0), truckOf("small", 6000.0)))
        val vm = vm(repo, prefs)
        vm.openBidSheet()
        val form = vm.state.value.form!!
        assertEquals("big", form.vehicleId)
        assertEquals("Salida a primera hora", form.notes)
        assertEquals(1900.0, form.amount, "-5 % del sugerido")
    }

    @Test fun `un vehiculo en ruta puede ofertar y uno en mantenimiento no`() = runTest {
        val repo = FakeLoads(
            Result.success(listOf(load)),
            fleet = listOf(truckOf("route", 8000.0, VehicleStatus.IN_ROUTE), truckOf("maint", 8000.0, VehicleStatus.MAINTENANCE)),
        )
        val vm = vm(repo)
        assertEquals(listOf("route"), vm.state.value.compatibleVehicles.map { it.id })
        assertEquals(VehicleFit.UNAVAILABLE, vm.state.value.vehicleChoices.first { it.vehicle.id == "maint" }.fit)
    }

    @Test fun `la agenda bloquea un vehiculo ocupado esas fechas y no se puede elegir`() = runTest {
        val repo = FakeLoads(Result.success(listOf(load)), fleet = listOf(truckOf("busy", 6000.0), truckOf("free", 9000.0)))
        repo.eligibilityResult = eligibility(
            VehicleEligibility("busy", eligible = false, blockerCode = "LOGYX-CAP-005", freeFrom = "2026-12-05"),
            VehicleEligibility("free"),
        )
        val vm = vm(repo)
        vm.openBidSheet()
        assertEquals(listOf("free"), vm.state.value.bookableVehicles.map { it.id })
        assertEquals("free", vm.state.value.form?.vehicleId, "el chico está ocupado: se toma el libre")
        vm.selectVehicle("busy")
        assertEquals("free", vm.state.value.form?.vehicleId, "un vehículo ocupado no se elige")
    }

    @Test fun `cobras neto con la tasa vigente y desglose del ejemplo del dueno`() = runTest {
        val repo = FakeLoads(Result.success(listOf(load.copy(priceFloor = 1000.0))), fleet = listOf(truckOf("1", 6000.0)))
        repo.rateResult = Result.success(0.08)
        val vm = vm(repo)
        vm.openBidSheet()
        vm.onAmountChange("1180")
        val b = vm.state.value.netBreakdown!!
        assertEquals(1000.0, b.netOfVat)
        assertEquals(180.0, b.vat)
        assertEquals(80.0, b.commission)
        assertEquals(14.4, b.commissionVat)
        assertEquals(1085.6, b.carrierNet)
        vm.onAmountChange("900")
        assertNull(vm.state.value.netBreakdown, "bajo el piso (1,000) no hay neto")
    }

    @Test fun `al ofertar se recuerda el vehiculo la nota y el atajo y los avisos de agenda llegan al mensaje`() = runTest {
        val prefs = MemoryPrefs()
        val repo = FakeLoads(Result.success(listOf(load)), fleet = listOf(truckOf("1", 6000.0)))
        repo.placeResult = Result.success(
            BidView("b1", "1", amount = 1900.0, status = BidStatus.PENDING, warnings = listOf(BidWarning("LOGYX-CAP-006", "ABC-1 ya tiene 1 oferta para esas fechas"))),
        )
        val vm = vm(repo, prefs)
        vm.openBidSheet()
        vm.onAmountChange("1900")
        vm.onNotesChange("Con GPS")
        vm.submitBid()
        assertEquals(BidRecall("1", "Con GPS", BidShortcut.MINUS_5), prefs.saved)
        assertEquals(R.string.loads_bid_sent_warning, vm.state.value.message?.textRes)
        assertTrue(vm.state.value.message?.args?.single().toString().contains("1 oferta"))
    }
}
