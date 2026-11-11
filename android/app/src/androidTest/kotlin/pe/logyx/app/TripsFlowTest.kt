package pe.logyx.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import pe.logyx.app.navigation.LogyxApp
import pe.logyx.core.data.SessionState
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.model.BookingStatus
import pe.logyx.core.model.CargoType
import kotlin.test.assertEquals

/** PYME: pestaña Viajes -> reservar espacio (validación inline, confirmación) -> aparece en "Mis reservas". */
@RunWith(AndroidJUnit4::class)
class PymeBookingFlowTest {
    private val trips = FakeTrips()
    private val auth = FakeAuth(SessionState.LoggedIn(Data.pyme))
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FakeKoinRule(auth)).around(FakeTripsKoinRule(trips)).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun book_space_on_a_published_trip() {
        compose.setContent { LogyxTheme { LogyxApp() } }
        waitFor("Mis solicitudes")
        compose.onNodeWithText("Viajes").performClick()
        waitFor("Transportes Sur Andino SRL")
        compose.onNodeWithText("Transportes Sur Andino SRL").assertIsDisplayed()

        compose.onAllNodesWithText("Reservar espacio").onFirst().performClick()
        waitFor("Peso de tu carga")
        // Sin datos: validación inline y no se envía nada
        compose.onAllNodesWithText("Reservar espacio").onLast().performClick()
        waitFor("Ingresa el peso de tu carga, en kg.")
        waitFor("Describe qué vas a enviar.")
        assertEquals(0, trips.booked.size)

        compose.onAllNodes(hasSetTextAction())[0].performTextInput("2500")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Mercadería de bazar en cajas")
        compose.onNode(hasContentDescription("Tipo de carga", substring = true)).performClick()
        compose.onNodeWithText("Carga general").performClick()
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("Av. Argentina 1234, Callao")
        compose.onAllNodes(hasSetTextAction())[3].performTextInput("Jr. San Román 456, Juliaca")
        waitFor("S/ 1,200.00") // 0.48 por kg x 2,500 kg
        // Con el teclado abierto el botón puede quedar tapado: se dispara la acción semántica, no un toque.
        compose.onAllNodesWithText("Reservar espacio").onLast().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)

        waitFor("¿Reservar 2,500 kg por S/ 1,200.00?")
        compose.onNodeWithText("Enviar reserva").performClick()
        compose.waitUntil(10_000) { trips.booked.size == 1 }
        assertEquals(2500.0, trips.booked.single().second.weightKg)
        assertEquals(CargoType.GENERAL, trips.booked.single().second.cargoType)
        // Salta a "Mis reservas" y muestra la reserva por confirmar
        waitFor("Por confirmar")
        waitFor("Reserva enviada")
    }
}

/** Transportista: Home -> Mis viajes -> detalle -> confirmar una reserva (con diálogo de confirmación). */
@RunWith(AndroidJUnit4::class)
class CarrierTripBookingsFlowTest {
    private val trips = FakeTrips(bookings = listOf(TripData.booking()))
    private val auth = FakeAuth(SessionState.LoggedIn(Data.carrier))
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(FakeKoinRule(auth)).around(FakeTripsKoinRule(trips)).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun confirm_a_pending_booking() {
        compose.setContent { LogyxTheme { LogyxApp() } }
        waitFor("Tu actividad")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Ver mis viajes"))
        compose.onNodeWithText("Ver mis viajes").performClick()
        waitFor("Mis viajes")
        waitFor("ASA-202")
        compose.onNodeWithText("ASA-202").performClick()

        waitFor("Comercial Altiplano SAC")
        compose.onAllNodesWithText("Confirmar reserva").onFirst().performClick()
        waitFor("¿Confirmar la reserva de 2,500 kg?")
        assertEquals(0, trips.confirmed.size)
        compose.onAllNodesWithText("Confirmar reserva").onLast().performClick()
        compose.waitUntil(10_000) { trips.confirmed.size == 1 }
        waitFor("Reserva confirmada")
        assertEquals(BookingStatus.CONFIRMED, trips.bookings.single().status)
    }
}
