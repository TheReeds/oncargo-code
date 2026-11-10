package pe.logyx.feature.auth

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
import pe.logyx.core.data.SessionState
import pe.logyx.core.model.OrgType
import pe.logyx.core.model.Role
import pe.logyx.core.model.UserInfo
import pe.logyx.core.network.ApiException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private class FakeAuth(var result: Result<UserInfo>) : AuthRepository {
    override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.LoggedOut)
    var calls = 0
    override suspend fun login(email: String, password: String): Result<UserInfo> { calls++; return result }
    override suspend fun logout() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val user = UserInfo("1", "a@b.pe", "Ana", Role.ADMIN, "2", "Org", OrgType.CARRIER, true)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `valida campos vacios sin llamar a la API`() = runTest {
        val auth = FakeAuth(Result.success(user))
        val vm = LoginViewModel(auth)
        vm.submit()
        assertNotNull(vm.state.value.emailError)
        assertNotNull(vm.state.value.passwordError)
        assertEquals(0, auth.calls)
    }

    @Test fun `login correcto deja de cargar sin error`() = runTest {
        val auth = FakeAuth(Result.success(user))
        val vm = LoginViewModel(auth)
        vm.onEmailChange("a@b.pe"); vm.onPasswordChange("x")
        vm.submit()
        assertEquals(1, auth.calls)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.error)
    }

    @Test fun `credenciales invalidas muestran el mensaje de la API`() = runTest {
        val auth = FakeAuth(Result.failure(ApiException(401, "LOGYX-IDN-001", "Correo o contraseña incorrectos")))
        val vm = LoginViewModel(auth)
        vm.onEmailChange("a@b.pe"); vm.onPasswordChange("mala")
        vm.submit()
        assertEquals("Correo o contraseña incorrectos", vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }
}
