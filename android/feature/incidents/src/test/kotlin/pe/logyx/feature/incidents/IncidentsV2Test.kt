package pe.logyx.feature.incidents

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
import pe.logyx.core.data.IncidentsRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun v2(id: String = "i1", category: IncidentCategory = IncidentCategory.ROAD_BLOCKED, blocking: Boolean = false, canWithdraw: Boolean = true) =
    IncidentView(id, "s1", category = category, description = "Huaico", status = IncidentStatus.OPEN, active = true, blocking = blocking,
        severity = IncidentSeverity.MEDIUM, canWithdraw = canWithdraw, reportedByOrgId = "org-c")

private class V2Incidents(var detail: IncidentView, var withdrawResult: Result<IncidentView>? = null) : IncidentsRepository {
    var withdrawn = 0
    override suspend fun report(shipmentId: String, category: IncidentCategory, description: String) = Result.success(detail)
    override suspend fun forShipment(shipmentId: String) = Result.success(listOf(detail))
    override suspend fun detail(id: String) = Result.success(detail)
    override suspend fun comment(id: String, body: String) = Result.success(detail)
    override suspend fun mine(active: Boolean?, page: Int) = Result.failure<Page<IncidentView>>(IllegalStateException())
    override suspend fun withdraw(id: String): Result<IncidentView> {
        withdrawn++
        return withdrawResult ?: Result.success(detail.copy(status = IncidentStatus.CLOSED, active = false, canWithdraw = false, withdrawnAt = "2026-11-19T10:00:00-05:00"))
    }
}

private class Auth : AuthRepository {
    override val state: StateFlow<SessionState> = MutableStateFlow(
        SessionState.LoggedIn(UserInfo("u", "a@b.pe", "Ana", Role.ADMIN, "org-c", "Org", OrgType.CARRIER)),
    )
    override suspend fun login(email: String, password: String): Result<UserInfo> = throw NotImplementedError()
    override suspend fun logout() = Unit
}

/** Incidencias v2 (V23, roadmap 2.7): informativas, retirar y categorías nuevas o desconocidas. */
@OptIn(ExperimentalCoroutinesApi::class)
class IncidentsV2Test {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `retirar una informativa pide confirmacion y actualiza el detalle`() = runTest {
        val repo = V2Incidents(v2())
        val vm = IncidentDetailViewModel("i1", repo, Auth(), FakeRealtime())
        assertTrue(vm.state.value.canWithdraw)
        vm.withdraw()
        assertEquals(1, repo.withdrawn, "withdraw sin pasar por el diálogo también funciona (la pantalla lo pide antes)")
        assertFalse(vm.state.value.canWithdraw)
        assertNotNull(vm.state.value.incident?.withdrawnAt)
        assertEquals(pe.logyx.core.designsystem.components.MessageKind.Success, vm.state.value.message?.kind)
    }

    @Test fun `sin canWithdraw no se ofrece y un error del servidor se muestra`() = runTest {
        val blocked = V2Incidents(v2(category = IncidentCategory.ACCIDENT, blocking = true, canWithdraw = false))
        val vm = IncidentDetailViewModel("i1", blocked, Auth(), FakeRealtime())
        vm.askWithdraw()
        assertFalse(vm.state.value.confirmingWithdraw)
        vm.withdraw()
        assertEquals(0, blocked.withdrawn)

        val failing = V2Incidents(v2(), Result.failure(ApiException(409, "LOGYX-INC-007", "Solo quien reportó la incidencia la retira")))
        val vm2 = IncidentDetailViewModel("i1", failing, Auth(), FakeRealtime())
        vm2.askWithdraw()
        assertTrue(vm2.state.value.confirmingWithdraw)
        vm2.withdraw()
        assertFalse(vm2.state.value.confirmingWithdraw)
        assertEquals(pe.logyx.core.designsystem.components.MessageKind.Error, vm2.state.value.message?.kind)
    }

    @Test fun `una informativa activa no detiene el envio en la lista`() = runTest {
        val vm = IncidentsListViewModel("s1", V2Incidents(v2()), FakeRealtime())
        assertTrue(vm.state.value.hasActive)
        assertFalse(vm.state.value.holdsShipment, "el viaje sigue: se puede reportar otra")
        val vm2 = IncidentsListViewModel("s1", V2Incidents(v2(category = IncidentCategory.THEFT, blocking = true)), FakeRealtime())
        assertTrue(vm2.state.value.holdsShipment)
    }

    @Test fun `al reportar se ofrecen las categorias nuevas y no la desconocida`() {
        assertTrue(IncidentCategory.PICKUP_NO_SHOW in reportableIncidentCategories)
        assertTrue(IncidentCategory.PAYMENT_DISPUTE in reportableIncidentCategories)
        assertFalse(IncidentCategory.UNKNOWN in reportableIncidentCategories)
        assertTrue(IncidentCategory.DELAY.informative)
        assertTrue(IncidentCategory.ROAD_BLOCKED.informative)
        assertFalse(IncidentCategory.BREAKDOWN.informative)
    }
}
