package pe.logyx.app

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import pe.logyx.app.navigation.LogyxApp
import pe.logyx.core.data.SessionState
import pe.logyx.core.designsystem.LogyxTheme
import kotlin.test.assertEquals

/** Login -> pantalla de inicio según el rol (navegación raíz de `LogyxApp`). */
@RunWith(AndroidJUnit4::class)
class RoleHomeTest {
    private val auth = FakeAuth()
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FakeKoinRule(auth)).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun login_leads_to_carrier_home() {
        compose.setContent { LogyxTheme { LogyxApp() } }
        compose.onNodeWithText("Inicia sesión").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("carrier@logyx.pe")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Logyx2026!")
        compose.onNodeWithText("Iniciar sesión").performClick()
        waitFor("Tu actividad")
        compose.onNodeWithText("Tu actividad").assertIsDisplayed()
        assertEquals(1, auth.loginCalls)
    }

    @Test
    fun pyme_sees_requests() {
        auth.setLoggedIn(Data.pyme)
        compose.setContent { LogyxTheme { LogyxApp() } }
        waitFor("Mis solicitudes")
        compose.onNodeWithText("Mis solicitudes").assertIsDisplayed()
    }

    @Test
    fun driver_sees_active_trip() {
        auth.setLoggedIn(Data.driver)
        compose.setContent { LogyxTheme { LogyxApp() } }
        waitFor("Llegué al recojo")
        compose.onNodeWithText("Llegué al recojo").assertIsDisplayed()
    }
}
