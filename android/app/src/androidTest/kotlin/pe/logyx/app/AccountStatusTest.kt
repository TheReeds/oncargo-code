package pe.logyx.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import pe.logyx.app.navigation.LogyxApp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.ProvideReadOnly
import pe.logyx.core.model.OrgType
import pe.logyx.core.model.Role
import pe.logyx.core.model.VerificationStatus

/** B2 (rol Lector) y A1 (banner de verificación, RN01) en las pantallas de inicio. */
@RunWith(AndroidJUnit4::class)
class AccountStatusTest {
    private val auth = FakeAuth()
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FakeKoinRule(auth)).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun write_button_is_disabled_for_viewer_and_enabled_otherwise() {
        var readOnly = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            LogyxTheme {
                ProvideReadOnly(readOnly.value) { LogyxButton("Enviar oferta", {}, variant = ButtonVariant.Money, requiresOperate = true) }
            }
        }
        compose.onNodeWithText("Enviar oferta").assertIsNotEnabled()
        readOnly.value = false
        compose.onNodeWithText("Enviar oferta").assertIsEnabled()
    }

    @Test
    fun viewer_sees_read_only_notice() {
        auth.setLoggedIn(Data.user(Role.VIEWER, OrgType.PYME, "Lucía Vega"))
        compose.setContent { LogyxTheme { LogyxApp() } }
        waitFor("Tu rol Lector solo permite consultar")
        compose.onNodeWithText("Tu rol Lector solo permite consultar", substring = true).assertIsDisplayed()
    }

    @Test
    fun pending_carrier_sees_verification_banner_on_home() {
        auth.setLoggedIn(Data.carrier.copy(rucVerified = false, verificationStatus = VerificationStatus.PENDING))
        compose.setContent { LogyxTheme { LogyxApp() } }
        waitFor("Tu empresa está en revisión")
        compose.onNodeWithText("Tu empresa está en revisión").assertIsDisplayed()
    }

    @Test
    fun rejected_pyme_sees_the_operator_note() {
        auth.setLoggedIn(
            Data.pyme.copy(rucVerified = false, verificationStatus = VerificationStatus.REJECTED, verificationNote = "El RUC no coincide con la razón social."),
        )
        compose.setContent { LogyxTheme { LogyxApp() } }
        waitFor("Verificación rechazada")
        compose.onNodeWithText("El RUC no coincide con la razón social.", substring = true).assertIsDisplayed()
    }

    @Test
    fun verified_account_shows_no_banner() {
        auth.setLoggedIn(Data.carrier.copy(verificationStatus = VerificationStatus.VERIFIED))
        compose.setContent { LogyxTheme { LogyxApp() } }
        waitFor("Tu actividad")
        compose.onAllNodes(hasText("Tu empresa está en revisión")).assertCountEquals(0)
    }
}
