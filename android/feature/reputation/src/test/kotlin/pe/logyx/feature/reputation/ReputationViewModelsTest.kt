package pe.logyx.feature.reputation

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
import pe.logyx.core.data.ReputationRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.model.*
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun review(id: String) = ReviewView(id, rating = 4.0, reviewerName = "X")

private class FakeRep(
    var rep: Result<ReputationView>,
    var shipment: Result<ShipmentReviewsView> = Result.success(ShipmentReviewsView(canReview = true)),
) : ReputationRepository {
    var submitted: List<Any?>? = null
    var submitResult: Result<ReviewView> = Result.success(review("n"))
    override suspend fun reputation(organizationId: String) = rep
    override suspend fun reviews(organizationId: String, page: Int) =
        Result.success(Page(listOf(review("p$page-a"), review("p$page-b")), page, 2, 5))
    override suspend fun shipmentReviews(shipmentId: String) = shipment
    override suspend fun submit(shipmentId: String, punctuality: Int, cargoCondition: Int, communication: Int, comment: String?): Result<ReviewView> {
        submitted = listOf(punctuality, cargoCondition, communication, comment)
        return submitResult
    }
}

private class FakeAuth(type: OrgType) : AuthRepository {
    override val state: StateFlow<SessionState> = MutableStateFlow(
        SessionState.LoggedIn(UserInfo("u", "a@b.pe", "A", Role.ADMIN, "o", organizationType = type)),
    )
    override suspend fun login(email: String, password: String) = Result.failure<UserInfo>(UnsupportedOperationException())
    override suspend fun logout() {}
}

private fun rep(total: Int = 5, recent: List<ReviewView> = listOf(review("r1"))) =
    ReputationView("o1", "Transportes Andes", OrgType.CARRIER, totalReviews = total, recentReviews = recent)

@OptIn(ExperimentalCoroutinesApi::class)
class ReputationViewModelsTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `perfil carga reputacion y habilita ver mas`() {
        val vm = OrganizationProfileViewModel("o1", FakeRep(Result.success(rep())))
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(1, s.reviews.size)
        assertTrue(s.hasMore)
    }

    @Test fun `perfil sin reseñas no ofrece ver mas`() {
        val vm = OrganizationProfileViewModel("o1", FakeRep(Result.success(rep(total = 0, recent = emptyList()))))
        assertFalse(vm.state.value.hasMore)
    }

    @Test fun `ver mas pagina desde la pagina 0 sin duplicar`() {
        val vm = OrganizationProfileViewModel("o1", FakeRep(Result.success(rep())))
        vm.loadMore()
        assertEquals(listOf("p0-a", "p0-b"), vm.state.value.reviews.map { it.id })
        vm.loadMore()
        assertEquals(4, vm.state.value.reviews.size)
        assertEquals(listOf("p0-a", "p0-b", "p1-a", "p1-b"), vm.state.value.reviews.map { it.id })
    }

    @Test fun `perfil con error inicial expone mensaje`() {
        val vm = OrganizationProfileViewModel("o1", FakeRep(Result.failure(ApiException(500, "X", "Falló"))))
        assertEquals("Falló", vm.state.value.error)
    }

    @Test fun `PYME califica estado de la carga y transportista exactitud declarada`() {
        assertTrue(ReviewViewModel("s", FakeRep(Result.failure(Exception())), FakeAuth(OrgType.PYME)).state.value.ratesCarrier)
        assertFalse(ReviewViewModel("s", FakeRep(Result.failure(Exception())), FakeAuth(OrgType.CARRIER)).state.value.ratesCarrier)
    }

    @Test fun `no se puede calificar muestra motivo y no envia`() {
        val repo = FakeRep(Result.failure(Exception()), Result.success(ShipmentReviewsView(false, "NOT_DELIVERED", "El envío aún no se entrega.", listOf(review("m")))))
        val vm = ReviewViewModel("s", repo, FakeAuth(OrgType.PYME))
        vm.onPunctuality(5); vm.onCargoCondition(5); vm.onCommunication(5)
        vm.submit()
        assertFalse(vm.state.value.canReview)
        assertEquals("El envío aún no se entrega.", vm.state.value.blockedReason)
        assertEquals(1, vm.state.value.existing.size)
        assertNull(repo.submitted)
    }

    @Test fun `enviar requiere las tres dimensiones`() {
        val repo = FakeRep(Result.failure(Exception()))
        val vm = ReviewViewModel("s", repo, FakeAuth(OrgType.PYME))
        vm.onPunctuality(4); vm.onCommunication(5)
        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        assertNull(repo.submitted)
        vm.onCargoCondition(3)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test fun `envio exitoso marca done y manda comentario recortado`() {
        val repo = FakeRep(Result.failure(Exception()))
        val vm = ReviewViewModel("s", repo, FakeAuth(OrgType.CARRIER))
        vm.onPunctuality(4); vm.onCargoCondition(3); vm.onCommunication(5); vm.onComment("  Buen trato ")
        vm.submit()
        assertTrue(vm.state.value.done)
        assertEquals(listOf(4, 3, 5, "Buen trato"), repo.submitted)
        assertNotNull(vm.state.value.message)
    }

    @Test fun `comentario vacio se envia como null`() {
        val repo = FakeRep(Result.failure(Exception()))
        val vm = ReviewViewModel("s", repo, FakeAuth(OrgType.PYME))
        vm.onPunctuality(1); vm.onCargoCondition(1); vm.onCommunication(1)
        vm.submit()
        assertNull(repo.submitted?.get(3))
    }

    @Test fun `comentario mayor a 1000 bloquea el envio`() {
        val vm = ReviewViewModel("s", FakeRep(Result.failure(Exception())), FakeAuth(OrgType.PYME))
        vm.onPunctuality(5); vm.onCargoCondition(5); vm.onCommunication(5)
        vm.onComment("a".repeat(1001))
        assertFalse(vm.state.value.canSubmit)
        vm.onComment("a".repeat(1000))
        assertTrue(vm.state.value.canSubmit)
    }

    @Test fun `RN07 - NEG-001 en el comentario de la resena queda bajo el campo`() {
        val repo = FakeRep(Result.failure(Exception()))
        repo.submitResult = Result.failure(ApiException(400, "LOGYX-NEG-001", "No incluyas teléfonos.", field = "comment"))
        val vm = ReviewViewModel("s", repo, FakeAuth(OrgType.PYME))
        vm.onPunctuality(5); vm.onCargoCondition(5); vm.onCommunication(5); vm.onComment("987654321")
        vm.submit()
        assertFalse(vm.state.value.done)
        assertEquals("No incluyas teléfonos.", vm.state.value.commentError)
        assertNull(vm.state.value.message)
        assertFalse(vm.state.value.canSubmit)
        vm.onComment("Buen servicio")
        assertNull(vm.state.value.commentError)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test fun `error al enviar no marca done`() {
        val repo = FakeRep(Result.failure(Exception()))
        repo.submitResult = Result.failure(ApiException(409, "ALREADY_REVIEWED", "Ya calificaste este envío."))
        val vm = ReviewViewModel("s", repo, FakeAuth(OrgType.PYME))
        vm.onPunctuality(5); vm.onCargoCondition(5); vm.onCommunication(5)
        vm.submit()
        assertFalse(vm.state.value.done)
        assertEquals("Ya calificaste este envío.", vm.state.value.message?.text)
    }
}
