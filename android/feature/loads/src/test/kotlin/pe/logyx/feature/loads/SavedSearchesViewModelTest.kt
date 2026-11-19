package pe.logyx.feature.loads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.LoadsRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.designsystem.components.MessageKind
import pe.logyx.core.designsystem.components.UiText
import pe.logyx.core.model.BidWarning
import pe.logyx.core.model.CargoType
import pe.logyx.core.model.CityItem
import pe.logyx.core.model.OrgType
import pe.logyx.core.model.RequestView
import pe.logyx.core.model.Role
import pe.logyx.core.model.SavedSearch
import pe.logyx.core.model.SavedSearchRequest
import pe.logyx.core.model.UserInfo
import pe.logyx.core.model.VehicleView
import pe.logyx.core.network.ApiException
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun carrier(role: Role) = UserInfo("u1", "c@logyx.pe", "Carla Quispe", role, "org1", "Andes SAC", OrgType.CARRIER, true, organizationCity = "Lima")

private class RoleAuth(role: Role) : AuthRepository {
    override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.LoggedIn(carrier(role)))
    override suspend fun login(email: String, password: String) = Result.success(carrier(Role.ADMIN))
    override suspend fun logout() {}
}

private class SavedLoads(var list: Result<List<SavedSearch>> = Result.success(emptyList())) : LoadsRepository {
    var createResult: Result<SavedSearch>? = null
    var updateResult: Result<SavedSearch>? = null
    var deleteResult: Result<Unit> = Result.success(Unit)
    val created = mutableListOf<SavedSearchRequest>()
    val updated = mutableListOf<Pair<String, SavedSearchRequest>>()
    val deleted = mutableListOf<String>()
    override suspend fun savedSearches() = list
    override suspend fun createSavedSearch(body: SavedSearchRequest): Result<SavedSearch> {
        created += body
        return createResult ?: Result.success(SavedSearch("n${created.size}", body.name, body.originCity, body.destinationCity, body.cargoType, active = body.active ?: true))
    }
    override suspend fun updateSavedSearch(id: String, body: SavedSearchRequest): Result<SavedSearch> {
        updated += id to body
        return updateResult ?: Result.success(SavedSearch(id, body.name, body.originCity, body.destinationCity, body.cargoType, active = body.active ?: true))
    }
    override suspend fun deleteSavedSearch(id: String): Result<Unit> { deleted += id; return deleteResult }
    override suspend fun marketplace(): Result<List<RequestView>> = Result.success(emptyList())
    override suspend fun load(id: String): Result<RequestView> = Result.failure(UnsupportedOperationException())
    override suspend fun myBids(): Result<List<RequestView>> = Result.success(emptyList())
    override suspend fun vehicles(): Result<List<VehicleView>> = Result.success(emptyList())
    override suspend fun placeBid(requestId: String, vehicleId: String, amount: Double, notes: String?) = Result.failure<pe.logyx.core.model.BidView>(UnsupportedOperationException())
}

private val lima = SavedSearch("s1", "Lima a Juliaca", originCity = "LIMA", destinationCity = "JULIACA", cargoType = CargoType.GENERAL, maxWeightKg = 5000.0)
private val filters = SavedSearchRequest(name = "", originCity = "LIMA", destinationCity = "JULIACA")

@OptIn(ExperimentalCoroutinesApi::class)
class SavedSearchesViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `carga mis busquedas`() {
        val vm = SavedSearchesViewModel(RoleAuth(Role.OPS), SavedLoads(Result.success(listOf(lima))))
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(listOf("s1"), s.items.map { it.id })
        assertTrue(s.canWrite)
    }

    @Test fun `error de carga se muestra tal cual`() {
        val vm = SavedSearchesViewModel(RoleAuth(Role.OPS), SavedLoads(Result.failure(ApiException(0, "NETWORK", "Sin conexión"))))
        assertEquals("Sin conexión", vm.state.value.error)
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test fun `crea una busqueda y la pone primero`() {
        val repo = SavedLoads(Result.success(listOf(lima)))
        val vm = SavedSearchesViewModel(RoleAuth(Role.ADMIN), repo)
        vm.save("  Ruta sur  ", filters)
        val s = vm.state.value
        assertEquals("Ruta sur", repo.created.single().name)
        assertEquals(true, repo.created.single().active)
        assertEquals(listOf("n1", "s1"), s.items.map { it.id })
        assertTrue(s.saved)
        assertFalse(s.saving)
        assertEquals(MessageKind.Success, s.message?.kind)
        assertEquals(R.string.loads_saved_created, s.message?.textRes)
    }

    @Test fun `nombre en blanco no llama a la API`() {
        val repo = SavedLoads()
        val vm = SavedSearchesViewModel(RoleAuth(Role.ADMIN), repo)
        vm.save("   ", filters)
        assertTrue(repo.created.isEmpty())
        assertEquals(UiText.of(R.string.loads_saved_name_required), vm.state.value.saveError)
    }

    @Test fun `tope de busquedas ALT-002 se muestra tal cual y no cierra`() {
        val repo = SavedLoads()
        val msg = "Llegaste al máximo de 20 búsquedas guardadas. Borra una para guardar otra."
        repo.createResult = Result.failure(ApiException(409, "LOGYX-ALT-002", msg))
        val vm = SavedSearchesViewModel(RoleAuth(Role.ADMIN), repo)
        vm.save("Otra", filters)
        val s = vm.state.value
        assertEquals(UiText.Raw(msg), s.saveError)
        assertFalse(s.saved)
        assertFalse(s.saving)
        assertTrue(s.items.isEmpty())
        vm.resetSave()
        assertNull(vm.state.value.saveError)
    }

    @Test fun `pausar y reanudar reenvian la busqueda con active`() {
        val repo = SavedLoads(Result.success(listOf(lima)))
        val vm = SavedSearchesViewModel(RoleAuth(Role.OPS), repo)
        vm.setActive(lima, false)
        val (id, body) = repo.updated.single()
        assertEquals("s1", id)
        assertEquals(false, body.active)
        // el PUT reemplaza: se reenvían nombre y filtros tal cual
        assertEquals("Lima a Juliaca", body.name)
        assertEquals("LIMA", body.originCity)
        assertEquals(5000.0, body.maxWeightKg)
        val paused = vm.state.value.items.single()
        assertFalse(paused.active)
        assertEquals(R.string.loads_saved_paused, vm.state.value.message?.textRes)
        assertTrue(vm.state.value.busyIds.isEmpty())
        vm.setActive(paused, true)
        assertTrue(vm.state.value.items.single().active)
        assertEquals(R.string.loads_saved_resumed, vm.state.value.message?.textRes)
    }

    @Test fun `error al pausar deja la busqueda como estaba`() {
        val repo = SavedLoads(Result.success(listOf(lima)))
        repo.updateResult = Result.failure(ApiException(500, "LOGYX-SYS-500", "No pudimos actualizar la búsqueda."))
        val vm = SavedSearchesViewModel(RoleAuth(Role.OPS), repo)
        vm.setActive(lima, false)
        assertTrue(vm.state.value.items.single().active)
        assertEquals("No pudimos actualizar la búsqueda.", vm.state.value.message?.text)
        assertEquals(MessageKind.Error, vm.state.value.message?.kind)
    }

    @Test fun `borrar pide confirmacion`() {
        val repo = SavedLoads(Result.success(listOf(lima)))
        val vm = SavedSearchesViewModel(RoleAuth(Role.OPS), repo)
        vm.askDelete(lima)
        assertEquals("s1", vm.state.value.deleting?.id)
        assertTrue(repo.deleted.isEmpty())
        vm.dismissDelete()
        assertNull(vm.state.value.deleting)
        vm.askDelete(lima)
        vm.confirmDelete()
        assertEquals(listOf("s1"), repo.deleted)
        assertTrue(vm.state.value.items.isEmpty())
        assertNull(vm.state.value.deleting)
        assertEquals(R.string.loads_saved_deleted, vm.state.value.message?.textRes)
    }

    @Test fun `el Lector ve la lista pero no escribe`() {
        val repo = SavedLoads(Result.success(listOf(lima)))
        val vm = SavedSearchesViewModel(RoleAuth(Role.VIEWER), repo)
        assertFalse(vm.state.value.canWrite)
        assertEquals(1, vm.state.value.items.size)
        vm.save("Nueva", filters)
        vm.setActive(lima, false)
        vm.askDelete(lima)
        vm.confirmDelete()
        assertTrue(repo.created.isEmpty())
        assertTrue(repo.updated.isEmpty())
        assertTrue(repo.deleted.isEmpty())
        assertNull(vm.state.value.deleting)
    }

    // ─── Filtros de la pantalla de cargas → búsqueda guardada ───

    private val cities = listOf(CityItem("LIMA", "Lima", -12.04, -77.04), CityItem("JULIACA", "Juliaca", -15.5, -70.1))

    @Test fun `sin filtros no hay nada que guardar`() {
        assertNull(LoadsFeedUiState().toSavedSearchRequest())
        // «Mejor pago» es un orden, no un filtro
        assertNull(LoadsFeedUiState(filters = setOf(LoadFilter.BestPay)).toSavedSearchRequest())
    }

    @Test fun `los filtros de la pantalla se guardan con codigos y fechas`() {
        val s = LoadsFeedUiState(
            originCity = cities[0], destinationCity = cities[1], cargoType = CargoType.PERISHABLE,
            dateFrom = LocalDate.of(2026, 11, 28), dateTo = LocalDate.of(2026, 12, 5),
            filters = setOf(LoadFilter.MyVehicle, LoadFilter.BestPay), vehicles = listOf(truck("1", 8000.0), truck("2", 12000.0)),
        )
        val r = s.toSavedSearchRequest("Ruta")!!
        assertEquals("Ruta", r.name)
        assertEquals("LIMA", r.originCity)
        assertEquals("JULIACA", r.destinationCity)
        assertEquals(CargoType.PERISHABLE, r.cargoType)
        assertEquals("2026-11-28", r.pickupFrom)
        assertEquals("2026-12-05", r.pickupTo)
        assertEquals(12000.0, r.maxWeightKg)
    }

    @Test fun `cerca de mi guarda la ciudad de la organizacion como origen`() {
        val r = LoadsFeedUiState(filters = setOf(LoadFilter.Nearby), userCity = "Lima").toSavedSearchRequest()
        assertNotNull(r)
        assertEquals("LIMA", r.originCity)
    }

    @Test fun `nombre sugerido segun la ruta`() {
        assertEquals("Lima → Juliaca", LoadsFeedUiState(originCity = cities[0], destinationCity = cities[1]).suggestedSearchName())
        assertEquals("Desde Lima", LoadsFeedUiState(originCity = cities[0]).suggestedSearchName())
        assertEquals("Hacia Juliaca", LoadsFeedUiState(destinationCity = cities[1]).suggestedSearchName())
        assertEquals("", LoadsFeedUiState(cargoType = CargoType.BULK).suggestedSearchName())
    }

    // ─── Resumen del filtro ───

    private fun SavedSearch.summary() = summaryParts(
        city = { c -> cities.first { it.code == c }.name }, cargo = { it.name.lowercase() }, weight = { "Hasta ${it.toInt()} kg" },
        dates = { f, t -> "${f ?: "…"}/${t ?: "…"}" }, vehicle = { id -> if (id == "v1") "Vehículo ABC-1" else null }, anyCity = "cualquiera",
    ).joinToString(" · ")

    @Test fun `resumen completo`() {
        val s = lima.copy(pickupFrom = "2026-11-28", pickupTo = "2026-12-05", vehicleId = "v1")
        assertEquals("Lima → Juliaca · general · 2026-11-28/2026-12-05 · Hasta 5000 kg · Vehículo ABC-1", s.summary())
    }

    @Test fun `resumen con un solo extremo de la ruta y sin filtros`() {
        assertEquals("Lima → cualquiera", SavedSearch("a", "x", originCity = "LIMA").summary())
        assertEquals("cualquiera → Juliaca", SavedSearch("a", "x", destinationCity = "JULIACA").summary())
        assertEquals("", SavedSearch("a", "x").summary())
        assertFalse(SavedSearch("a", "x").hasFilters)
        assertTrue(lima.hasFilters)
    }

    @Test fun `un vehiculo desconocido no aparece en el resumen`() {
        assertEquals("fragile", SavedSearch("a", "x", cargoType = CargoType.FRAGILE, vehicleId = "otro").summary())
    }

    // ─── Avisos al ofertar (CAP-006, FLT-007/008) ───

    @Test fun `avisos de la oferta se unen sin repetir`() {
        assertNull(bidWarningsText(emptyList()))
        assertNull(bidWarningsText(listOf(BidWarning("LOGYX-FLT-007", null), BidWarning(null, "  "))))
        assertEquals(
            "El SOAT de ABC-1 está vencido. La inspección vence antes del recojo.",
            bidWarningsText(
                listOf(
                    BidWarning("LOGYX-FLT-007", "El SOAT de ABC-1 está vencido."),
                    BidWarning("LOGYX-FLT-008", "La inspección vence antes del recojo."),
                    BidWarning("LOGYX-FLT-007", "El SOAT de ABC-1 está vencido."),
                ),
            ),
        )
    }
}
