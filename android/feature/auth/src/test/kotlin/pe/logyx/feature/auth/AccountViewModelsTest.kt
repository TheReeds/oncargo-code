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
import kotlin.test.assertTrue

private val demoUser = UserInfo("1", "a@b.pe", "Ana", Role.ADMIN, "2", "Org", OrgType.CARRIER, true)

/** Repositorio de sesión de pruebas: registra las llamadas y devuelve lo que se le configure. */
private class ScriptedAuth(
    var changeResult: Result<Unit> = Result.success(Unit),
    var forgotResult: Result<String> = Result.success("Si el correo está registrado, te enviamos un enlace."),
    var logoutAllResult: Result<Unit> = Result.success(Unit),
) : AuthRepository {
    private val _state = MutableStateFlow<SessionState>(SessionState.LoggedIn(demoUser))
    override val state: StateFlow<SessionState> = _state
    var changeCalls = mutableListOf<Pair<String, String>>()
    var forgotCalls = mutableListOf<String>()
    var logoutCalls = 0
    var logoutAllCalls = 0
    var refreshUserCalls = 0
    override suspend fun login(email: String, password: String) = Result.success(demoUser)
    override suspend fun logout() { logoutCalls++; _state.value = SessionState.LoggedOut }
    override suspend fun refreshUser(): Result<UserInfo> { refreshUserCalls++; return Result.success(demoUser) }
    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        changeCalls += currentPassword to newPassword
        return changeResult
    }
    override suspend fun logoutAll(): Result<Unit> { logoutAllCalls++; return logoutAllResult.also { if (it.isSuccess) _state.value = SessionState.LoggedOut } }
    override suspend fun forgotPassword(email: String): Result<String> { forgotCalls += email; return forgotResult }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun filled(auth: ScriptedAuth, current: String = "vieja-123", new: String = "nueva-clave-456", confirm: String = new) =
        ChangePasswordViewModel(auth).also { it.onCurrentChange(current); it.onNewChange(new); it.onConfirmChange(confirm) }

    @Test fun `valida sin llamar a la API la actual vacia, corta, igual y confirmacion distinta`() = runTest {
        val auth = ScriptedAuth()
        val vm = ChangePasswordViewModel(auth)
        vm.submit()
        assertEquals(PasswordFormError.CurrentEmpty, vm.state.value.currentError)
        assertEquals(PasswordFormError.NewTooShort, vm.state.value.newError)
        vm.onCurrentChange("abcdefgh"); vm.onNewChange("abcdefgh"); vm.submit()
        assertEquals(PasswordFormError.NewSameAsCurrent, vm.state.value.newError)
        vm.onNewChange("x".repeat(73)); vm.submit()
        assertEquals(PasswordFormError.NewTooLong, vm.state.value.newError)
        vm.onNewChange("nueva-clave-456"); vm.onConfirmChange("otra-cosa-1234"); vm.submit()
        assertEquals(PasswordFormError.ConfirmMismatch, vm.state.value.confirmError)
        assertTrue(auth.changeCalls.isEmpty())
    }

    @Test fun `cambio correcto marca done y manda las dos contraseñas`() = runTest {
        val auth = ScriptedAuth()
        val vm = filled(auth)
        vm.submit()
        assertEquals(listOf("vieja-123" to "nueva-clave-456"), auth.changeCalls)
        assertTrue(vm.state.value.done)
        assertFalse(vm.state.value.saving)
        assertEquals("", vm.state.value.current, "limpia los secretos del estado")
        vm.onDoneShown()
        assertFalse(vm.state.value.done)
    }

    @Test fun `AUTH-006 se muestra bajo la contraseña actual`() = runTest {
        val auth = ScriptedAuth(changeResult = Result.failure(ApiException(400, "LOGYX-AUTH-006", "La contraseña actual no es correcta", "currentPassword")))
        val vm = filled(auth)
        vm.submit()
        assertEquals("La contraseña actual no es correcta", vm.state.value.serverErrors["currentPassword"])
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.saving)
        assertFalse(vm.state.value.done)
        // al editar el campo se limpia su error
        vm.onCurrentChange("otra")
        assertNull(vm.state.value.serverErrors["currentPassword"])
    }

    @Test fun `AUTH-010 se muestra bajo la contraseña nueva`() = runTest {
        val auth = ScriptedAuth(changeResult = Result.failure(ApiException(400, "LOGYX-AUTH-010", "La nueva contraseña debe ser distinta de la actual", "newPassword")))
        val vm = filled(auth)
        vm.submit()
        assertEquals("La nueva contraseña debe ser distinta de la actual", vm.state.value.serverErrors["newPassword"])
    }

    @Test fun `un error sin campo (429 o sin red) se muestra como aviso del formulario`() = runTest {
        val auth = ScriptedAuth(changeResult = Result.failure(ApiException(429, "LOGYX-AUTH-429", "Demasiados intentos. Espera unos minutos.")))
        val vm = filled(auth)
        vm.submit()
        assertEquals("Demasiados intentos. Espera unos minutos.", vm.state.value.error)
        assertTrue(vm.state.value.serverErrors.isEmpty)
    }

    @Test fun `no envia dos veces mientras guarda`() = runTest {
        val auth = ScriptedAuth()
        val vm = filled(auth)
        vm.submit(); vm.submit()
        assertEquals(1, auth.changeCalls.size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `prefill usa el correo del login solo si el campo esta vacio`() {
        val vm = ForgotPasswordViewModel(ScriptedAuth())
        vm.prefill("ana@empresa.pe")
        assertEquals("ana@empresa.pe", vm.state.value.email)
        vm.prefill("otra@empresa.pe")
        assertEquals("ana@empresa.pe", vm.state.value.email)
    }

    @Test fun `correo invalido no llama a la API`() {
        val auth = ScriptedAuth()
        val vm = ForgotPasswordViewModel(auth)
        vm.onEmailChange("sin-arroba")
        vm.submit()
        assertTrue(vm.state.value.emailInvalid)
        assertTrue(auth.forgotCalls.isEmpty())
    }

    @Test fun `envia y muestra el mismo mensaje exista o no el correo`() {
        val auth = ScriptedAuth()
        val vm = ForgotPasswordViewModel(auth)
        vm.onEmailChange("  ana@empresa.pe ")
        vm.submit()
        assertEquals(listOf("ana@empresa.pe"), auth.forgotCalls)
        assertTrue(vm.state.value.sent)
        assertEquals("Si el correo está registrado, te enviamos un enlace.", vm.state.value.serverMessage)
        assertFalse(vm.state.value.sending)
        // otro correo: misma respuesta del servidor, mismo estado
        val vm2 = ForgotPasswordViewModel(ScriptedAuth())
        vm2.onEmailChange("nadie@desconocido.pe"); vm2.submit()
        assertEquals(vm.state.value.copy(email = ""), vm2.state.value.copy(email = ""))
    }

    @Test fun `limite de intentos 429 muestra el error y no marca enviado`() {
        val auth = ScriptedAuth(forgotResult = Result.failure(ApiException(429, "LOGYX-AUTH-429", "Hiciste demasiados intentos.")))
        val vm = ForgotPasswordViewModel(auth)
        vm.onEmailChange("ana@empresa.pe"); vm.submit()
        assertEquals("Hiciste demasiados intentos.", vm.state.value.error)
        assertFalse(vm.state.value.sent)
        vm.onEmailChange("ana@empresa.pe")
        assertNull(vm.state.value.error)
    }

    @Test fun `mensaje vacio del servidor cae al texto de la pantalla`() {
        val vm = ForgotPasswordViewModel(ScriptedAuth(forgotResult = Result.success("")))
        vm.onEmailChange("ana@empresa.pe"); vm.submit()
        assertTrue(vm.state.value.sent)
        assertNull(vm.state.value.serverMessage)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `al abrir Cuenta vuelve a leer el usuario y expone la sesion`() {
        val auth = ScriptedAuth()
        val vm = AccountViewModel(auth)
        assertEquals(1, auth.refreshUserCalls)
        assertEquals("Ana", vm.user.value?.fullName)
    }

    @Test fun `cerrar sesion llama al repositorio`() = runTest {
        val auth = ScriptedAuth()
        AccountViewModel(auth).logout()
        assertEquals(1, auth.logoutCalls)
    }

    @Test fun `cerrar sesion en todos los dispositivos`() = runTest {
        val auth = ScriptedAuth()
        val vm = AccountViewModel(auth)
        vm.logoutAll()
        assertEquals(1, auth.logoutAllCalls)
        assertNull(vm.ui.value.message)
    }

    @Test fun `si logout-all falla avisa y permite reintentar`() = runTest {
        val auth = ScriptedAuth(logoutAllResult = Result.failure(ApiException(0, "NETWORK", "No pudimos conectar con OnCargo.")))
        val vm = AccountViewModel(auth)
        vm.logoutAll()
        assertFalse(vm.ui.value.loggingOutAll)
        assertNotNull(vm.ui.value.message)
        assertEquals("No pudimos conectar con OnCargo.", vm.ui.value.message?.text)
        vm.onMessageShown()
        assertNull(vm.ui.value.message)
        vm.logoutAll()
        assertEquals(2, auth.logoutAllCalls)
    }
}
