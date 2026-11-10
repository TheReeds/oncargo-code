package pe.logyx.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import pe.logyx.app.navigation.LogyxApp
import pe.logyx.core.data.SessionState
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.model.ShipmentStatus
import pe.logyx.core.model.StopType
import kotlin.test.assertEquals

/** Conductor: avanza con el botón XL por las 4 transiciones hasta "Viaje entregado". */
@RunWith(AndroidJUnit4::class)
class DriverAdvanceTest {
    private val shipments = FakeShipments(Data.trip(Data.stop("p", 1, StopType.PICKUP), Data.stop("d", 2, StopType.DELIVERY)))
    private val auth = FakeAuth(SessionState.LoggedIn(Data.driver))
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FakeKoinRule(auth, shipments = shipments)).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun step(action: String, confirm: String, next: String) {
        waitFor(action)
        compose.onNodeWithText(action).performClick()
        waitFor(confirm)
        compose.onNodeWithText(confirm).performClick()
        waitFor(next)
    }

    @Test
    fun advance_until_delivered() {
        compose.setContent { LogyxTheme { LogyxApp() } }
        step("Llegué al recojo", "Confirmar llegada", "Carga recogida")
        step("Carga recogida", "Confirmar recojo", "Llegué a destino")
        step("Llegué a destino", "Confirmar llegada", "Entregado")
        waitFor("Entregado")
        compose.onNodeWithText("Entregado").performClick()
        waitFor("Confirmar entrega")
        compose.onNodeWithText("Confirmar entrega").performClick()
        waitFor("Viaje entregado")
        assertEquals(ShipmentStatus.DELIVERED, shipments.current.status)
    }
}
