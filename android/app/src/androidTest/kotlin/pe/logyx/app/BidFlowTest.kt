package pe.logyx.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import pe.logyx.app.navigation.LogyxApp
import pe.logyx.core.data.SessionState
import pe.logyx.core.designsystem.LogyxTheme
import kotlin.test.assertEquals

/** Transportista: feed -> detalle -> ofertar (error bajo el piso, luego oferta válida con confirmación). */
@RunWith(AndroidJUnit4::class)
class BidFlowTest {
    private val loads = FakeLoads()
    private val auth = FakeAuth(SessionState.LoggedIn(Data.carrier))
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FakeKoinRule(auth, loads)).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun place_bid_with_floor_validation() {
        compose.setContent { LogyxTheme { LogyxApp() } }
        waitFor("Tu actividad")
        compose.onNodeWithText("Cargas").performClick()
        waitFor("Ver carga y ofertar")
        compose.onNodeWithText("Ver carga y ofertar").performClick()
        waitFor("Hacer una oferta")
        compose.onNodeWithText("Hacer una oferta").performClick()

        // Vehículo
        waitFor("Enviar oferta")
        // Con un solo vehículo en la flota la hoja lo preselecciona; con varios hay que elegirlo.
        if (compose.onAllNodes(hasText("Elige tu vehículo")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(hasContentDescription("Vehículo", substring = true)).performClick()
            compose.onNodeWithText("ASA-202").performClick()
        }

        // Monto bajo el piso -> error y no se envía
        val amount = compose.onAllNodes(hasSetTextAction())[0]
        amount.performTextInput("500")
        compose.onNodeWithText("Enviar oferta").performClick()
        waitFor("Tu oferta es menor al piso de S/ 650.00")
        assertEquals(0, loads.placed.size)

        // Monto válido -> confirmación -> oferta enviada
        amount.performTextClearance()
        amount.performTextInput("700")
        compose.onNodeWithText("Enviar oferta").performClick()
        waitFor("¿Enviar oferta de S/ 700.00?")
        compose.onAllNodesWithText("Enviar oferta")[1].performClick()
        compose.waitUntil(10_000) { loads.placed.size == 1 }
        assertEquals(700.0, loads.placed.single().second)
        waitFor("Oferta enviada")
    }
}
