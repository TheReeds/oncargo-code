package pe.logyx.feature.shipments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.AdjustmentsRepository
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.designsystem.components.MessageKind
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class Session(type: OrgType, role: Role = Role.ADMIN) : AuthRepository {
    override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.LoggedIn(UserInfo("u", "a@b.pe", "Ana", role, "org", "Org", type)))
    override suspend fun login(email: String, password: String) = Result.failure<UserInfo>(IllegalStateException())
    override suspend fun logout() {}
}

private val pending = AdjustmentView("a1", "s1", type = AdjustmentType.TOLL, amount = 45.5, status = AdjustmentStatus.PENDING, respondBy = "2026-11-21T10:00:00-05:00")

private class Adjustments(var view: ShipmentAdjustmentsView) : AdjustmentsRepository {
    var loads = 0
    val decisions = mutableListOf<String>()
    val proposals = mutableListOf<Pair<ProposeAdjustmentRequest, String?>>()
    var rejectError: ApiException? = null
    override suspend fun forShipment(shipmentId: String): Result<ShipmentAdjustmentsView> { loads++; return Result.success(view) }
    override suspend fun propose(shipmentId: String, request: ProposeAdjustmentRequest, idempotencyKey: String?): Result<AdjustmentView> {
        proposals += request to idempotencyKey
        return Result.success(AdjustmentView("a9", type = request.type, amount = request.amount, status = AdjustmentStatus.PENDING))
    }
    override suspend fun accept(id: String, note: String?): Result<AdjustmentView> {
        decisions += "accept:$id"
        view = view.copy(finalPrice = 1895.5, acceptedTotal = 45.5, adjustments = view.adjustments.map { it.copy(status = AdjustmentStatus.ACCEPTED) })
        return Result.success(pending.copy(status = AdjustmentStatus.ACCEPTED))
    }
    override suspend fun reject(id: String, note: String): Result<AdjustmentView> {
        rejectError?.let { return Result.failure(it) }
        decisions += "reject:$id:$note"
        return Result.success(pending.copy(status = AdjustmentStatus.REJECTED, decisionNote = note))
    }
    override suspend fun withdraw(id: String) = Result.failure<AdjustmentView>(IllegalStateException())
}

/** Precio final y ajustes del envío (2.6): la PYME acepta/rechaza (motivo obligatorio) y el transportista propone sugerencias. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShipmentAdjustmentsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val base = ShipmentAdjustmentsView(
        agreedPrice = 1850.0, acceptedTotal = 0.0, finalPrice = 1850.0, adjustments = listOf(pending),
        suggestions = listOf(AdjustmentSuggestionView(AdjustmentType.DETENTION, "d", 90.0, "Estadía de 4 h")),
    )

    @Test fun `la PYME acepta un ajuste pendiente y se recarga el precio final`() = runTest {
        val repo = Adjustments(base)
        val vm = ShipmentAdjustmentsViewModel("s1", Session(OrgType.PYME), repo, FakeRealtime())
        assertTrue(vm.state.value.hasContent)
        assertTrue(vm.state.value.canDecide(pending))
        vm.askAccept(pending)
        assertNotNull(vm.state.value.accepting)
        vm.confirmAccept()
        assertEquals(listOf("accept:a1"), repo.decisions)
        assertNull(vm.state.value.accepting)
        assertEquals(1895.5, vm.state.value.data?.finalPrice)
        assertEquals(MessageKind.Success, vm.state.value.message?.kind)
    }

    @Test fun `rechazar pide motivo y muestra ADJ-004 bajo el campo`() = runTest {
        val repo = Adjustments(base)
        val vm = ShipmentAdjustmentsViewModel("s1", Session(OrgType.PYME), repo, FakeRealtime())
        vm.askReject(pending)
        vm.confirmReject()
        assertNotNull(vm.state.value.rejecting?.error, "sin motivo no se envía")
        assertTrue(repo.decisions.isEmpty())

        repo.rejectError = ApiException(400, "LOGYX-ADJ-004", "Indica por qué rechazas el ajuste", "note")
        vm.onRejectNote("  ")
        vm.onRejectNote("x")
        vm.confirmReject()
        assertEquals("Indica por qué rechazas el ajuste", (vm.state.value.rejecting?.error as? UiText.Raw)?.value)

        repo.rejectError = null
        vm.onRejectNote("No pasamos por ese peaje")
        vm.confirmReject()
        assertEquals(listOf("reject:a1:No pasamos por ese peaje"), repo.decisions)
        assertNull(vm.state.value.rejecting)
    }

    @Test fun `el transportista no decide pero propone una sugerencia con su clave`() = runTest {
        val repo = Adjustments(base)
        val vm = ShipmentAdjustmentsViewModel("s1", Session(OrgType.CARRIER), repo, FakeRealtime(), newKey = { "key-12345678" })
        assertFalse(vm.state.value.canDecide(pending))
        vm.askAccept(pending)
        assertNull(vm.state.value.accepting)
        vm.askPropose(base.suggestions.single())
        vm.confirmPropose()
        val (body, key) = repo.proposals.single()
        assertEquals(AdjustmentType.DETENTION, body.type)
        assertEquals(90.0, body.amount)
        assertEquals("d", body.stopId)
        assertEquals("key-12345678", key)
    }

    @Test fun `un ajuste escalado lo decide OnCargo y el conductor no ve sugerencias`() = runTest {
        val escalated = pending.copy(escalated = true)
        val vm = ShipmentAdjustmentsViewModel("s1", Session(OrgType.PYME), Adjustments(base.copy(adjustments = listOf(escalated))), FakeRealtime())
        assertFalse(vm.state.value.canDecide(escalated))
        val driver = ShipmentAdjustmentsViewModel("s1", Session(OrgType.CARRIER, Role.DRIVER), Adjustments(base.copy(adjustments = emptyList())), FakeRealtime())
        assertFalse(driver.state.value.isCarrier)
        assertFalse(driver.state.value.hasContent)
    }

    @Test fun `shipment adjustment_updated del envio recarga`() = runTest(UnconfinedTestDispatcher()) {
        val repo = Adjustments(base)
        val rt = FakeRealtime()
        val vm = ShipmentAdjustmentsViewModel("s1", Session(OrgType.PYME), repo, rt)
        val job = launch { vm.liveLoop() }
        val before = repo.loads
        rt.emit(RealtimeTypes.SHIPMENT_ADJUSTMENT_UPDATED, "shipmentId" to "s1", "adjustmentId" to "a1", "status" to "ACCEPTED")
        assertEquals(before + 1, repo.loads)
        rt.emit(RealtimeTypes.SHIPMENT_ADJUSTMENT_UPDATED, "shipmentId" to "otro")
        assertEquals(before + 1, repo.loads)
        job.cancel()
    }
}
