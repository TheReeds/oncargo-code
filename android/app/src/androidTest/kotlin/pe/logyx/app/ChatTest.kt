package pe.logyx.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import pe.logyx.core.data.SessionState
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.feature.negotiation.ChatRoute
import kotlin.test.assertEquals

/** Chat (RF26, RN07): un teléfono no genera burbuja y el aviso queda bajo el campo; un mensaje válido sí aparece. */
@RunWith(AndroidJUnit4::class)
class ChatTest {
    private val auth = FakeAuth(SessionState.LoggedIn(Data.pyme), loginAs = Data.pyme)
    private val negotiation = FakeNegotiation()
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FakeKoinRule(auth)).around(FakeNegotiationRule(negotiation)).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun sendButton() = compose.onNode(hasContentDescription("Enviar mensaje").or(hasText("Enviar mensaje")))

    @Test
    fun phone_number_is_blocked_without_bubble_then_valid_message_is_sent() {
        compose.setContent { LogyxTheme { ChatRoute("r1", "org-carrier", "Cargo Sur", onBack = {}) } }
        waitFor("Aún no hay mensajes")

        val field = compose.onAllNodes(hasSetTextAction())[0]
        field.performTextInput("Llámame al 999 888 777")
        sendButton().performClick()

        waitFor("No puedes compartir teléfonos, correos ni enlaces")
        compose.onNodeWithText("No puedes compartir teléfonos, correos ni enlaces", substring = true).assertIsDisplayed()
        // Sin burbuja: el texto solo existe en el campo (1 nodo), no como mensaje enviado.
        assertEquals(1, compose.onAllNodesWithText("Llámame al 999 888 777", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, negotiation.stored.size)

        // Al editar, el aviso desaparece; luego un mensaje válido aparece como burbuja.
        field.performTextReplacement("Recojo mañana a las 8:00")
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("No puedes compartir teléfonos", substring = true)).fetchSemanticsNodes().isEmpty()
        }
        sendButton().performClick()

        waitFor("Recojo mañana a las 8:00")
        compose.waitUntil(5_000) { negotiation.stored.size == 1 }
        assertEquals(2, negotiation.attempts.size)
    }
}
