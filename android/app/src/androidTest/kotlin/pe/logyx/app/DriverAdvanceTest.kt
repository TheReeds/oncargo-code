package pe.logyx.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import pe.logyx.feature.driver.LocalPhotoCapture
import pe.logyx.feature.driver.PhotoCapture
import pe.logyx.core.model.StopType
import kotlin.test.assertEquals

/** Conductor: avanza con el botón XL por las 4 transiciones hasta "Viaje entregado". */
@RunWith(AndroidJUnit4::class)
class DriverAdvanceTest {
    private val evidence = FakeEvidence()
    private val shipments = FakeShipments(Data.trip(Data.stop("p", 1, StopType.PICKUP), Data.stop("d", 2, StopType.DELIVERY)), evidence)
    private val auth = FakeAuth(SessionState.LoggedIn(Data.driver))
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FakeKoinRule(auth, shipments = shipments, evidence = evidence)).around(compose)

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
        // La captura de foto se simula: devuelve un JPEG mínimo en vez de abrir la cámara.
        val photo = PhotoCapture { byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()) }
        compose.setContent { CompositionLocalProvider(LocalPhotoCapture provides photo) { LogyxTheme { LogyxApp() } } }
        step("Llegué al recojo", "Confirmar llegada", "Carga recogida")
        step("Carga recogida", "Confirmar recojo", "Llegué a destino")
        step("Llegué a destino", "Confirmar llegada", "Entregado")
        waitFor("Evidencia de entrega")
        // Sin foto subida el botón "Entregado" está deshabilitado (RN06).
        compose.onNodeWithText("Entregado").assertIsNotEnabled()
        compose.onNodeWithText("Tomar foto de entrega").performClick()
        compose.waitUntil(10_000) { evidence.hasUploadedPhoto("s1") }
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithText("Entregado").assertIsEnabled() }.isSuccess
        }
        compose.onNodeWithText("Entregado").performClick()
        waitFor("Confirmar entrega")
        compose.onNodeWithText("Confirmar entrega").performClick()
        waitFor("Viaje entregado")
        assertEquals(ShipmentStatus.DELIVERED, shipments.current.status)
    }
}
